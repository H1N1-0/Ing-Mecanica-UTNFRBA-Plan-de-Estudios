package ar.correlativas.mecanica.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR de la copia de seguridad, con ZXing (Java puro: sin red ni servicios de Google). */
object Qr {
    /** Módulos del QR (true = negro), sin margen. */
    class Matriz(val lado: Int, private val celdas: BooleanArray) {
        operator fun get(x: Int, y: Int) = celdas[y * lado + x]
    }

    fun generar(texto: String): Matriz {
        val hints = mapOf(
            // Corrección media: tolera reflejos de pantalla sin agrandar demasiado el código.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0, hints)
        val lado = m.width
        return Matriz(lado, BooleanArray(lado * lado) { m.get(it % lado, it / lado) })
    }

    private val hintsLectura = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    private fun leer(fuente: LuminanceSource): String? = try {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(fuente)), hintsLectura).text
    } catch (_: NotFoundException) {
        null
    } catch (_: Exception) {
        null
    }

    /** Lee un QR de una imagen (pixeles ARGB), por ejemplo una captura guardada. */
    fun leerPixeles(ancho: Int, alto: Int, pixeles: IntArray): String? = leer(RGBLuminanceSource(ancho, alto, pixeles))

    /** Lee un QR de un cuadro de la cámara (plano Y de YUV). */
    fun leerLuminancia(datos: ByteArray, ancho: Int, alto: Int, filaBytes: Int): String? =
        leer(PlanarYUVLuminanceSource(datos, filaBytes, alto, 0, 0, ancho, alto, false))
}
