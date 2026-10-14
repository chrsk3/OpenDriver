package org.opendriver.core.model

/**
 * Tamanho da bolha de resultado.
 *
 * [scale] é o multiplicador único usado para derivar TODAS as dimensões do card
 * (corner radius, largura da borda, padding interno, tipografia e ícones), de modo
 * que o frame acompanhe proporcionalmente o conteúdo em qualquer tamanho.
 */
enum class BubbleSize(val displayName: String, val scale: Float) {
    PEQUENO("Pequeno", 0.8f),
    MEDIO("Médio", 1.0f),
    GRANDE("Grande", 1.25f)
}
