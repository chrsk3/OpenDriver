package org.opendriver.service

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs

/**
 * Deduplicador perceptual de frames baseado na Soma das Diferenças Absolutas (SAD).
 *
 * Inspirado na implementação do GigU (co.gigu.app):
 * Antes de acionar o OCR pesado (ML Kit), o frame é reduzido para 64x64 pixels
 * (4.096 amostras de luminância) e comparado com o último frame processado.
 *
 * Como funciona o limiar (Threshold):
 * - Resolução de amostragem: 64 x 64 = 4.096 pixels.
 * - Cada pixel é convertido em luminância inteira no intervalo [0, 255].
 * - A diferença máxima absoluta entre dois pixels é 255 (preto vs branco).
 * - O SAD máximo teórico é 4.096 * 255 = 1.044.480.
 * - O limiar padrão de 8% ([DEFAULT_THRESHOLD_FRACTION]) equivale a um SAD de ~83.558.
 * - Pequenas variações de rota no mapa, animações sutis de UI ou frames idênticos
 *   geram SAD bem abaixo desse valor (< 8%), sendo sumariamente descartados para
 *   poupar bateria e ciclos de CPU do ML Kit.
 * - Quando uma oferta de corrida surge na tela, um card grande de alto contraste
 *   cobre a região de interesse, gerando uma variação de luminância drástica
 *   (SAD comumente entre 25% e 70%), ultrapassando o limiar com folga e acionando o OCR.
 */
class FrameDeduplicator(
    var thresholdFraction: Float = DEFAULT_THRESHOLD_FRACTION
) {
    private var lastLuminance: IntArray? = null
    private var lastPackageName: String? = null

    /**
     * Avalia se o [bitmap] atual é perceptualmente duplicado em relação ao último processado.
     *
     * @param bitmap O bitmap a ser avaliado (preferencialmente o recorte da região da oferta).
     * @param packageName Nome do pacote do app ativo (ex: com.ubercab.driver).
     * @return true se o frame deve ser DESCARTADO (SAD < limiar); false se deve ser PROCESSADO.
     */
    fun shouldSkip(bitmap: Bitmap, packageName: String? = null): Boolean {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return false
        }

        // Se o usuário trocou de aplicativo alvo, não compara com o frame do app anterior
        if (packageName != null && packageName != lastPackageName) {
            lastPackageName = packageName
            reset()
        }

        val currentLuminance = extractLuminance64x64(bitmap) ?: return false

        val previous = lastLuminance
        if (previous == null) {
            // Primeiro frame capturado: registra como referência e deixa passar para OCR
            lastLuminance = currentLuminance
            return false
        }

        val sad = computeSad(currentLuminance, previous)
        val thresholdSad = (MAX_SAD * thresholdFraction).toLong()

        return if (sad < thresholdSad) {
            Log.d(
                TAG,
                "Frame duplicado ignorado: SAD=$sad < limiar=$thresholdSad (${(thresholdFraction * 100).toInt()}%)"
            )
            true
        } else {
            Log.d(
                TAG,
                "Frame novo aceito para OCR: SAD=$sad >= limiar=$thresholdSad"
            )
            lastLuminance = currentLuminance
            false
        }
    }

    /**
     * Reseta o frame de referência (garante que a próxima captura seja processada).
     */
    fun reset() {
        lastLuminance = null
    }

    /**
     * Reduz o bitmap para 64x64 e calcula a luminância de cada pixel (0..255).
     */
    private fun extractLuminance64x64(src: Bitmap): IntArray? {
        return try {
            val scaled = Bitmap.createScaledBitmap(src, SAMPLE_SIZE, SAMPLE_SIZE, true)
            val pixels = IntArray(TOTAL_PIXELS)
            scaled.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
            if (scaled !== src) {
                scaled.recycle()
            }

            // Conversão rápida para luminância inteira padrão BT.601: (77*R + 150*G + 29*B) >> 8
            val luminance = IntArray(TOTAL_PIXELS)
            for (i in 0 until TOTAL_PIXELS) {
                val p = pixels[i]
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                luminance[i] = (r * 77 + g * 150 + b * 29) shr 8
            }
            luminance
        } catch (t: Throwable) {
            Log.w(TAG, "Falha ao extrair amostra 64x64 para deduplicação: ${t.message}")
            null
        }
    }

    companion object {
        private const val TAG = "FrameDeduplicator"

        const val SAMPLE_SIZE = 64
        const val TOTAL_PIXELS = SAMPLE_SIZE * SAMPLE_SIZE // 4.096
        const val MAX_PIXEL_DIFF = 255
        const val MAX_SAD = TOTAL_PIXELS * MAX_PIXEL_DIFF // 1.044.480

        /** Limiar padrão de 8% do SAD máximo (~83.558) */
        const val DEFAULT_THRESHOLD_FRACTION = 0.08f

        /**
         * Calcula a Soma das Diferenças Absolutas (SAD) entre dois vetores de luminância.
         */
        fun computeSad(a: IntArray, b: IntArray): Long {
            var diffSum = 0L
            val len = minOf(a.size, b.size)
            for (i in 0 until len) {
                diffSum += abs(a[i] - b[i])
            }
            return diffSum
        }
    }
}
