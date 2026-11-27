package ar.correlativas.mecanica.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * Dibujos técnicos vectoriales del estilo "Plano técnico", hechos con geometría (no imágenes):
 * engranajes que engranan de verdad, la voluta de una bomba como espiral, etc.
 *
 * Se arman UNA vez por tamaño (en drawWithCache) como una lista de trazos ya calculados; dibujar
 * después es solo recorrer la lista. Siguen las convenciones de línea del dibujo técnico:
 * gruesa (contornos), fina (cotas y rayado), de eje (trazo y punto) y oculta (de trazos).
 */

/** Qué máquina se dibuja en el fondo de cada pantalla. */
enum class Dibujo { ENGRANAJES, PISTON, BOMBA, TURBINA, RODAMIENTO, RESORTE }

/** Un trazo listo para dibujar. */
sealed interface Trazo {
    class Linea(val path: Path, val tipo: TipoLinea) : Trazo
    class Rayado(val contorno: Path, val area: Rect) : Trazo
    class Cota(val a: Offset, val b: Offset, val desplazo: Float, val texto: TextLayoutResult) : Trazo
    class Rotulo(val en: Offset, val texto: TextLayoutResult) : Trazo
}

enum class TipoLinea { GRUESA, FINA, EJE, OCULTA }

/** Estilos de línea según el grosor base (1 dp). */
class Lineas(dp: Float) {
    val gruesa = Stroke(width = 1.3f * dp, cap = StrokeCap.Round)
    val fina = Stroke(width = 0.6f * dp, cap = StrokeCap.Round)
    val eje = Stroke(width = 0.6f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f * dp, 3f * dp, 2f * dp, 3f * dp)))
    val oculta = Stroke(width = 0.8f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * dp, 3f * dp)))
    val paso = 6f * dp
    val flecha = 5f * dp

    fun de(t: TipoLinea) = when (t) {
        TipoLinea.GRUESA -> gruesa
        TipoLinea.FINA -> fina
        TipoLinea.EJE -> eje
        TipoLinea.OCULTA -> oculta
    }
}

/** Dibuja una lista de trazos con una tinta. */
fun DrawScope.dibujar(trazos: List<Trazo>, tinta: Color, l: Lineas) {
    trazos.forEach { t ->
        when (t) {
            is Trazo.Linea -> drawPath(t.path, tinta, style = l.de(t.tipo))
            is Trazo.Rayado -> rayar(t.contorno, t.area, tinta, l)
            is Trazo.Cota -> cota(t, tinta, l)
            is Trazo.Rotulo -> drawText(t.texto, color = tinta, topLeft = t.en)
        }
    }
}

/** Rayado de corte a 45°, recortado al contorno. */
fun DrawScope.rayar(contorno: Path, area: Rect, tinta: Color, l: Lineas) {
    clipPath(contorno) {
        var x = area.left - area.height
        while (x < area.right) {
            drawLine(tinta, Offset(x, area.bottom), Offset(x + area.height, area.top), l.fina.width)
            x += l.paso
        }
    }
}

/** Cota: líneas de referencia, línea de cota con flechas y la medida en el medio. */
private fun DrawScope.cota(c: Trazo.Cota, tinta: Color, l: Lineas) {
    val largo = hypot(c.b.x - c.a.x, c.b.y - c.a.y).coerceAtLeast(1f)
    val d = Offset((c.b.x - c.a.x) / largo, (c.b.y - c.a.y) / largo)
    val n = Offset(-d.y, d.x)
    val pa = c.a + n * c.desplazo
    val pb = c.b + n * c.desplazo
    val sobra = n * (l.flecha * 0.8f * if (c.desplazo >= 0) 1f else -1f)
    drawLine(tinta, c.a + n * (c.desplazo * 0.15f), pa + sobra, l.fina.width)
    drawLine(tinta, c.b + n * (c.desplazo * 0.15f), pb + sobra, l.fina.width)
    drawLine(tinta, pa, pb, l.fina.width)
    flecha(pa, d, tinta, l)
    flecha(pb, -d, tinta, l)
    val medio = (pa + pb) / 2f
    val tam = Size(c.texto.size.width.toFloat(), c.texto.size.height.toFloat())
    val lado = if (c.desplazo >= 0) 1f else -1f
    val centro = medio + n * (lado * (tam.height * 0.55f + 2f))
    drawText(c.texto, color = tinta, topLeft = centro - Offset(tam.width / 2, tam.height / 2))
}

private fun DrawScope.flecha(punta: Offset, hacia: Offset, tinta: Color, l: Lineas) {
    val n = Offset(-hacia.y, hacia.x)
    val base = punta + hacia * l.flecha
    val p = Path().apply {
        moveTo(punta.x, punta.y)
        val a = base + n * (l.flecha * 0.32f)
        val b = base - n * (l.flecha * 0.32f)
        lineTo(a.x, a.y)
        lineTo(b.x, b.y)
        close()
    }
    drawPath(p, tinta)
}

/** Cuadrícula de papel milimetrado: líneas menores y mayores en dos paths. */
class Cuadricula(val menor: Path, val mayor: Path)

fun cuadricula(size: Size, paso: Float, cadaMayor: Int = 5): Cuadricula {
    val menor = Path()
    val mayor = Path()
    var i = 0
    var x = 0f
    while (x <= size.width) {
        val p = if (i % cadaMayor == 0) mayor else menor
        p.moveTo(x, 0f); p.lineTo(x, size.height)
        x += paso; i++
    }
    i = 0
    var y = 0f
    while (y <= size.height) {
        val p = if (i % cadaMayor == 0) mayor else menor
        p.moveTo(0f, y); p.lineTo(size.width, y)
        y += paso; i++
    }
    return Cuadricula(menor, mayor)
}

// ---------------------------------------------------------------- geometría

private fun polar(c: Offset, r: Float, a: Double) = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())

private fun circulo(c: Offset, r: Float) = Path().apply { addOval(Rect(c, r)) }

private fun linea(a: Offset, b: Offset) = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
}

/** Ejes de simetría en cruz, un poco más largos que la pieza. */
private fun ejes(c: Offset, r: Float) = Path().apply {
    moveTo(c.x - r, c.y); lineTo(c.x + r, c.y)
    moveTo(c.x, c.y - r); lineTo(c.x, c.y + r)
}

/**
 * Contorno de un engranaje recto: dientes trapezoidales sobre las circunferencias de raíz y de
 * cabeza (módulo m = 2·rPaso/z). [giro] centra un diente en ese ángulo.
 */
fun engranaje(c: Offset, rPaso: Float, dientes: Int, giro: Double = 0.0): Path {
    val m = 2 * rPaso / dientes
    val rCabeza = rPaso + m
    val rRaiz = rPaso - 1.25f * m
    val paso = 2 * PI / dientes
    return Path().apply {
        for (i in 0 until dientes) {
            val a = giro + i * paso
            val pts = listOf(
                polar(c, rRaiz, a - 0.30 * paso),
                polar(c, rCabeza, a - 0.13 * paso),
                polar(c, rCabeza, a + 0.13 * paso),
                polar(c, rRaiz, a + 0.30 * paso),
                polar(c, rRaiz, a + 0.50 * paso),
            )
            if (i == 0) moveTo(pts[0].x, pts[0].y) else lineTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        close()
    }
}

/** Datos para medir los textos de las cotas una sola vez. */
class Rotulador(private val medidor: TextMeasurer, private val estilo: TextStyle) {
    fun medir(texto: String, tam: Float = 11f) = medidor.measure(texto, estilo.copy(fontSize = tam.sp))
}

/**
 * Arma los trazos de [dibujo] dentro de [caja]. Todo se escala con el lado menor de la caja:
 * el mismo dibujo sirve en celular, tablet y horizontal.
 */
fun trazosDe(dibujo: Dibujo, caja: Rect, r: Rotulador): List<Trazo> = when (dibujo) {
    Dibujo.ENGRANAJES -> engranajes(caja, r)
    Dibujo.PISTON -> pistonYBiela(caja, r)
    Dibujo.BOMBA -> bombaCentrifuga(caja, r)
    Dibujo.TURBINA -> turbina(caja, r)
    Dibujo.RODAMIENTO -> rodamiento(caja, r)
    Dibujo.RESORTE -> resorte(caja, r)
}

private fun engranajes(caja: Rect, r: Rotulador): List<Trazo> {
    val l = minOf(caja.width, caja.height)
    val z1 = 22
    val z2 = 13
    val r1 = l * 0.30f
    val r2 = r1 * z2 / z1
    val phi = -0.55 // ángulo de la línea de centros
    val c1 = Offset(caja.center.x - l * 0.12f, caja.center.y + l * 0.08f)
    val c2 = polar(c1, r1 + r2, phi)
    val paso2 = 2 * PI / z2
    val t = mutableListOf<Trazo>()
    // Ruedas: el diente de la grande apunta a la chica y la chica tiene un hueco enfrente.
    t += Trazo.Linea(engranaje(c1, r1, z1, phi), TipoLinea.GRUESA)
    t += Trazo.Linea(engranaje(c2, r2, z2, phi + PI + paso2 / 2), TipoLinea.GRUESA)
    // Circunferencias primitivas (de eje) y ejes de cada rueda.
    t += Trazo.Linea(circulo(c1, r1), TipoLinea.EJE)
    t += Trazo.Linea(circulo(c2, r2), TipoLinea.EJE)
    t += Trazo.Linea(ejes(c1, r1 * 1.25f), TipoLinea.EJE)
    t += Trazo.Linea(ejes(c2, r2 * 1.35f), TipoLinea.EJE)
    // Cubo, agujero con chavetero y alivianamientos de la rueda grande.
    t += Trazo.Linea(circulo(c1, r1 * 0.36f), TipoLinea.GRUESA)
    val agujero = r1 * 0.15f
    t += Trazo.Linea(
        Path().apply {
            addOval(Rect(c1, agujero))
            addRect(Rect(c1.x - agujero * 0.28f, c1.y - agujero * 1.25f, c1.x + agujero * 0.28f, c1.y - agujero * 0.8f))
        },
        TipoLinea.GRUESA,
    )
    repeat(6) { i ->
        t += Trazo.Linea(circulo(polar(c1, r1 * 0.6f, i * PI / 3 + 0.3), r1 * 0.11f), TipoLinea.GRUESA)
    }
    t += Trazo.Linea(circulo(c1, r1 * 0.6f), TipoLinea.EJE)
    t += Trazo.Linea(circulo(c2, r2 * 0.28f), TipoLinea.GRUESA)
    t += Trazo.Linea(circulo(c2, r2 * 0.12f), TipoLinea.GRUESA)
    // Cotas: diámetro primitivo y distancia entre centros.
    val m = 2 * r1 / z1
    t += Trazo.Cota(
        Offset(c1.x - r1, c1.y + r1 + m), Offset(c1.x + r1, c1.y + r1 + m), l * 0.09f,
        r.medir("Ø${z1 * 4}  m4  z$z1"),
    )
    t += Trazo.Cota(c1, c2, -l * 0.05f, r.medir("a = ${(z1 + z2) * 2}"))
    return t
}

private fun pistonYBiela(caja: Rect, r: Rotulador): List<Trazo> {
    val l = minOf(caja.width, caja.height)
    val cx = caja.center.x
    val arriba = caja.top + l * 0.05f
    val diam = l * 0.36f
    val pared = l * 0.05f
    val alturaPiston = l * 0.30f
    val radioManivela = l * 0.17f
    val largoBiela = l * 0.46f
    val yPerno = arriba + alturaPiston * 0.55f
    val cMuneca = Offset(cx + radioManivela * 0.75f, yPerno + largoBiela)
    val cCiguenal = Offset(cx, cMuneca.y - radioManivela * 0.66f)
    val t = mutableListOf<Trazo>()
    // Camisa del cilindro en corte (rayada) a los dos lados.
    val altoCamisa = alturaPiston * 1.7f
    listOf(cx - diam / 2 - pared, cx + diam / 2).forEach { x ->
        val rect = Rect(x, arriba - l * 0.06f, x + pared, arriba - l * 0.06f + altoCamisa)
        val p = Path().apply { addRect(rect) }
        t += Trazo.Linea(p, TipoLinea.GRUESA)
        t += Trazo.Rayado(p, rect)
    }
    // Pistón con ranuras de aros, perno y su eje.
    val piston = Rect(cx - diam / 2 + l * 0.005f, arriba, cx + diam / 2 - l * 0.005f, arriba + alturaPiston)
    t += Trazo.Linea(Path().apply { addRect(piston) }, TipoLinea.GRUESA)
    repeat(3) { i ->
        val y = arriba + alturaPiston * (0.10f + i * 0.08f)
        t += Trazo.Linea(linea(Offset(piston.left, y), Offset(piston.left + l * 0.03f, y)), TipoLinea.FINA)
        t += Trazo.Linea(linea(Offset(piston.right - l * 0.03f, y), Offset(piston.right, y)), TipoLinea.FINA)
    }
    val perno = Offset(cx, yPerno)
    t += Trazo.Linea(circulo(perno, l * 0.035f), TipoLinea.GRUESA)
    // Biela: cuerpo afinado del pie (perno) a la cabeza (muñequilla).
    val dx = cMuneca.x - perno.x
    val dy = cMuneca.y - perno.y
    val largo = hypot(dx, dy)
    val n = Offset(-dy / largo, dx / largo)
    val pie = l * 0.045f
    val cabeza = l * 0.07f
    t += Trazo.Linea(linea(perno + n * pie * 0.7f, cMuneca + n * cabeza * 0.6f), TipoLinea.GRUESA)
    t += Trazo.Linea(linea(perno - n * pie * 0.7f, cMuneca - n * cabeza * 0.6f), TipoLinea.GRUESA)
    t += Trazo.Linea(circulo(perno, pie), TipoLinea.GRUESA)
    t += Trazo.Linea(circulo(cMuneca, cabeza), TipoLinea.GRUESA)
    t += Trazo.Linea(circulo(cMuneca, cabeza * 0.5f), TipoLinea.GRUESA)
    t += Trazo.Linea(linea(perno, cMuneca), TipoLinea.EJE)
    // Cigüeñal: círculo de la manivela (de eje), muñón y contrapeso.
    t += Trazo.Linea(circulo(cCiguenal, radioManivela), TipoLinea.EJE)
    t += Trazo.Linea(circulo(cCiguenal, l * 0.06f), TipoLinea.GRUESA)
    val contrapeso = Path().apply {
        arcTo(Rect(cCiguenal, radioManivela * 1.15f), 200f - 90f, 140f, true)
        lineTo(cCiguenal.x, cCiguenal.y)
        close()
    }
    t += Trazo.Linea(contrapeso, TipoLinea.OCULTA)
    // Eje del cilindro y cotas de diámetro y carrera.
    t += Trazo.Linea(linea(Offset(cx, arriba - l * 0.1f), Offset(cx, cCiguenal.y + radioManivela * 1.3f)), TipoLinea.EJE)
    t += Trazo.Linea(ejes(cCiguenal, radioManivela * 1.3f), TipoLinea.EJE)
    t += Trazo.Cota(Offset(piston.left, arriba), Offset(piston.right, arriba), -l * 0.08f, r.medir("Ø76 H7"))
    t += Trazo.Cota(
        Offset(cx + diam / 2 + pared, cCiguenal.y - radioManivela), Offset(cx + diam / 2 + pared, cCiguenal.y + radioManivela),
        -l * 0.10f, r.medir("C = 86"),
    )
    return t
}

private fun bombaCentrifuga(caja: Rect, r: Rotulador): List<Trazo> {
    val l = minOf(caja.width, caja.height)
    val c = Offset(caja.center.x - l * 0.05f, caja.center.y + l * 0.05f)
    val r0 = l * 0.22f
    val r1 = l * 0.36f
    val t = mutableListOf<Trazo>()
    // Voluta: espiral que se abre una vuelta y sale tangente por la descarga.
    val voluta = Path()
    val pasos = 72
    for (i in 0..pasos) {
        val a = -PI / 2 + 2 * PI * i / pasos
        val rr = r0 + (r1 - r0) * i / pasos
        val p = polar(c, rr, a)
        if (i == 0) voluta.moveTo(p.x, p.y) else voluta.lineTo(p.x, p.y)
    }
    val fin = polar(c, r1, 3 * PI / 2)
    voluta.lineTo(fin.x + l * 0.32f, fin.y)
    val boca = polar(c, r0, -PI / 2)
    voluta.moveTo(boca.x, boca.y)
    voluta.lineTo(boca.x + l * 0.32f, boca.y)
    t += Trazo.Linea(voluta, TipoLinea.GRUESA)
    // Brida de descarga.
    t += Trazo.Linea(linea(Offset(fin.x + l * 0.32f, fin.y - l * 0.03f), Offset(fin.x + l * 0.32f, boca.y + l * 0.03f)), TipoLinea.GRUESA)
    // Rodete: disco, ojo de aspiración y álabes curvados hacia atrás.
    val rRodete = r0 * 0.86f
    t += Trazo.Linea(circulo(c, rRodete), TipoLinea.GRUESA)
    t += Trazo.Linea(circulo(c, rRodete * 0.38f), TipoLinea.GRUESA)
    repeat(7) { i ->
        val a = i * 2 * PI / 7
        val p0 = polar(c, rRodete * 0.40f, a)
        val pc = polar(c, rRodete * 0.75f, a + 0.45)
        val p1 = polar(c, rRodete * 0.98f, a + 0.95)
        t += Trazo.Linea(Path().apply { moveTo(p0.x, p0.y); quadraticTo(pc.x, pc.y, p1.x, p1.y) }, TipoLinea.GRUESA)
    }
    // Eje en corte (rayado) con chavetero.
    val eje = Path().apply { addOval(Rect(c, rRodete * 0.2f)) }
    t += Trazo.Linea(eje, TipoLinea.GRUESA)
    t += Trazo.Rayado(eje, Rect(c, rRodete * 0.2f))
    t += Trazo.Linea(ejes(c, r1 * 1.15f), TipoLinea.EJE)
    t += Trazo.Cota(Offset(c.x - rRodete, c.y + r1 + l * 0.02f), Offset(c.x + rRodete, c.y + r1 + l * 0.02f), l * 0.06f, r.medir("Ø D2 = 250"))
    t += Trazo.Rotulo(Offset(c.x - r1, c.y - r1 - l * 0.09f), r.medir("CORTE A-A", 12f))
    return t
}

private fun turbina(caja: Rect, r: Rotulador): List<Trazo> {
    val l = minOf(caja.width, caja.height)
    val c = caja.center
    val rDisco = l * 0.26f
    val rAlabe = l * 0.40f
    val t = mutableListOf<Trazo>()
    t += Trazo.Linea(circulo(c, rDisco), TipoLinea.GRUESA)
    t += Trazo.Linea(circulo(c, rAlabe), TipoLinea.FINA)
    t += Trazo.Linea(circulo(c, rDisco * 0.3f), TipoLinea.GRUESA)
    t += Trazo.Linea(circulo(c, rDisco * 0.12f), TipoLinea.GRUESA)
    // Álabes: perfiles curvos alrededor del disco.
    val n = 30
    repeat(n) { i ->
        val a = i * 2 * PI / n
        val raiz = polar(c, rDisco, a)
        val medio = polar(c, (rDisco + rAlabe) / 2, a + 0.10)
        val punta = polar(c, rAlabe, a + 0.04)
        val dorso = polar(c, (rDisco + rAlabe) / 2, a + 0.16)
        val raiz2 = polar(c, rDisco, a + 0.09)
        t += Trazo.Linea(
            Path().apply {
                moveTo(raiz.x, raiz.y)
                quadraticTo(medio.x, medio.y, punta.x, punta.y)
                quadraticTo(dorso.x, dorso.y, raiz2.x, raiz2.y)
            },
            TipoLinea.GRUESA,
        )
    }
    // Agujeros de fijación en el disco.
    repeat(8) { i -> t += Trazo.Linea(circulo(polar(c, rDisco * 0.62f, i * PI / 4), rDisco * 0.07f), TipoLinea.GRUESA) }
    t += Trazo.Linea(circulo(c, rDisco * 0.62f), TipoLinea.EJE)
    t += Trazo.Linea(ejes(c, rAlabe * 1.12f), TipoLinea.EJE)
    t += Trazo.Cota(Offset(c.x - rAlabe, c.y + rAlabe + l * 0.02f), Offset(c.x + rAlabe, c.y + rAlabe + l * 0.02f), l * 0.06f, r.medir("Ø620  30 álabes"))
    return t
}

private fun rodamiento(caja: Rect, r: Rotulador): List<Trazo> {
    val l = minOf(caja.width, caja.height)
    val c = Offset(caja.center.x - l * 0.12f, caja.center.y)
    val rExt = l * 0.30f
    val t = mutableListOf<Trazo>()
    // Vista frontal: aros exterior e interior, bolillas en el círculo primitivo y jaula.
    t += Trazo.Linea(circulo(c, rExt), TipoLinea.GRUESA)
    t += Trazo.Linea(circulo(c, rExt * 0.82f), TipoLinea.GRUESA)
    t += Trazo.Linea(circulo(c, rExt * 0.52f), TipoLinea.GRUESA)
    t += Trazo.Linea(circulo(c, rExt * 0.38f), TipoLinea.GRUESA)
    val rPrimitivo = rExt * 0.67f
    val bolilla = rExt * 0.13f
    repeat(9) { i -> t += Trazo.Linea(circulo(polar(c, rPrimitivo, i * 2 * PI / 9), bolilla), TipoLinea.GRUESA) }
    t += Trazo.Linea(circulo(c, rPrimitivo), TipoLinea.EJE)
    t += Trazo.Linea(ejes(c, rExt * 1.2f), TipoLinea.EJE)
    // Vista lateral en corte: los dos aros rayados con la bolilla.
    val x0 = c.x + rExt * 1.55f
    val ancho = rExt * 0.5f
    listOf(-1f, 1f).forEach { s ->
        val yAfuera = c.y + s * rExt
        val yAdentro = c.y + s * rExt * 0.38f
        val ext = Rect(x0, minOf(yAfuera, c.y + s * rExt * 0.82f), x0 + ancho, maxOf(yAfuera, c.y + s * rExt * 0.82f))
        val int = Rect(x0, minOf(yAdentro, c.y + s * rExt * 0.52f), x0 + ancho, maxOf(yAdentro, c.y + s * rExt * 0.52f))
        listOf(ext, int).forEach { rect ->
            val p = Path().apply { addRect(rect) }
            t += Trazo.Linea(p, TipoLinea.GRUESA)
            t += Trazo.Rayado(p, rect)
        }
        t += Trazo.Linea(circulo(Offset(x0 + ancho / 2, c.y + s * rPrimitivo), bolilla), TipoLinea.GRUESA)
    }
    t += Trazo.Linea(linea(Offset(x0 - l * 0.04f, c.y), Offset(x0 + ancho + l * 0.04f, c.y)), TipoLinea.EJE)
    t += Trazo.Cota(Offset(c.x - rExt, c.y + rExt + l * 0.02f), Offset(c.x + rExt, c.y + rExt + l * 0.02f), l * 0.06f, r.medir("Ø D = 62  6205"))
    t += Trazo.Cota(Offset(x0, c.y - rExt), Offset(x0 + ancho, c.y - rExt), -l * 0.05f, r.medir("B = 15"))
    return t
}

private fun resorte(caja: Rect, r: Rotulador): List<Trazo> {
    val l = minOf(caja.width, caja.height)
    val cx = caja.center.x
    val espiras = 7
    val ancho = l * 0.17f
    val alambre = l * 0.022f
    val paso = l * 0.085f
    val y0 = caja.center.y - paso * espiras / 2
    val t = mutableListOf<Trazo>()
    val izquierda = List(espiras + 1) { i -> Offset(cx - ancho, y0 + i * paso) }
    val derecha = List(espiras) { i -> Offset(cx + ancho, y0 + (i + 0.5f) * paso) }
    // Secciones del alambre (rayadas) a cada lado.
    (izquierda + derecha).forEach { p ->
        val sec = Path().apply { addOval(Rect(p, alambre)) }
        t += Trazo.Linea(sec, TipoLinea.GRUESA)
        t += Trazo.Rayado(sec, Rect(p, alambre))
    }
    // Espiras: adelante con línea llena, atrás con línea oculta.
    derecha.forEachIndexed { i, d ->
        val a = izquierda[i]
        val b = izquierda[i + 1]
        t += Trazo.Linea(linea(Offset(a.x, a.y - alambre), Offset(d.x, d.y - alambre)), TipoLinea.GRUESA)
        t += Trazo.Linea(linea(Offset(a.x, a.y + alambre), Offset(d.x, d.y + alambre)), TipoLinea.GRUESA)
        t += Trazo.Linea(linea(Offset(d.x, d.y + alambre), Offset(b.x, b.y - alambre)), TipoLinea.OCULTA)
    }
    // Platillos de apoyo arriba y abajo.
    val arriba = izquierda.first().y - alambre * 2
    val abajo = izquierda.last().y + alambre * 2
    t += Trazo.Linea(linea(Offset(cx - ancho * 1.5f, arriba), Offset(cx + ancho * 1.5f, arriba)), TipoLinea.GRUESA)
    t += Trazo.Linea(linea(Offset(cx - ancho * 1.5f, abajo), Offset(cx + ancho * 1.5f, abajo)), TipoLinea.GRUESA)
    t += Trazo.Linea(linea(Offset(cx, arriba - l * 0.06f), Offset(cx, abajo + l * 0.06f)), TipoLinea.EJE)
    // Cotas: largo libre, paso y diámetro exterior.
    t += Trazo.Cota(Offset(cx + ancho * 1.5f, arriba), Offset(cx + ancho * 1.5f, abajo), -l * 0.08f, r.medir("L0 = 120"))
    t += Trazo.Cota(izquierda[0], izquierda[1], l * 0.09f, r.medir("p = 14"))
    t += Trazo.Cota(
        Offset(cx - ancho - alambre, abajo + l * 0.02f), Offset(cx + ancho + alambre, abajo + l * 0.02f), l * 0.06f,
        r.medir("Ø De = 40"),
    )
    return t
}
