package org.opendriver.core.model

import java.util.Locale

/**
 * Posição de ancoragem horizontal do card flutuante de oferta.
 *
 * Suporta as 3 posições do GigU:
 * - [CENTER]: Centro da tela (Gravity.CENTER_HORIZONTAL, x = 0).
 * - [LEFT]: Alinhado à esquerda (Gravity.START, x = margem).
 * - [RIGHT]: Alinhado à direita (Gravity.END, x = margem).
 */
enum class CardPosition(val displayName: String) {
    CENTER("Centro"),
    LEFT("Esquerda"),
    RIGHT("Direita");

    companion object {
        fun from(value: String?): CardPosition {
            if (value.isNullOrBlank()) return CENTER
            return when (value.trim().uppercase(Locale.ROOT)) {
                "CENTER", "CENTRO" -> CENTER
                "RIGHT", "DIREITA" -> RIGHT
                "LEFT", "ESQUERDA" -> LEFT
                else -> CENTER
            }
        }
    }
}
