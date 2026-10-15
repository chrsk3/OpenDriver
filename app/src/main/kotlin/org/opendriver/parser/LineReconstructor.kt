package org.opendriver.parser

import android.graphics.Rect
import com.google.mlkit.vision.text.Text

/**
 * Reconstrói linhas de texto a partir dos blocos e elementos geométricos do ML Kit.
 *
 * Inspirado na implementação do GigU (co.gigu.app - qf0.smali):
 * O ML Kit frequentemente divide partes da mesma linha horizontal em blocos
 * ou linhas separadas (ex: o símbolo "R$" em tamanho menor separado do valor "25,50",
 * ou a distância "5,2" separada de "km").
 *
 * Lógica de sobreposição vertical ≥ 50%:
 * - Dois elementos de texto A e B pertencem à mesma linha horizontal se a
 *   sobreposição vertical entre suas caixas delimitadoras (bounding boxes)
 *   for de pelo menos 50% da altura do menor elemento:
 *     overlap = min(A.bottom, B.bottom) - max(A.top, B.top)
 *     minHeight = min(A.height, B.height)
 *     pertencem à mesma linha se: overlap > 0 && 2 * overlap >= minHeight
 * - Usar minHeight garante que elementos de tamanhos visuais diferentes
 *   (como "R$" pequeno e valor grande, ou expoentes/subscritos) sejam
 *   agrupados corretamente na mesma linha horizontal (mesmo baseline).
 * - Dentro de cada linha agrupada, os elementos são ordenados da esquerda
 *   para a direita (coordenada X 'left').
 * - As linhas completas são ordenadas de cima para baixo (coordenada Y 'top').
 */
object LineReconstructor {

    data class TextItem(
        val text: String,
        val box: Rect
    ) {
        val height: Int get() = box.height().coerceAtLeast(1)
    }

    private class LineGroup(
        val items: MutableList<TextItem> = mutableListOf()
    ) {
        val top: Int get() = items.minOfOrNull { it.box.top } ?: 0

        /**
         * Calcula a razão máxima de sobreposição vertical deste grupo com um novo item,
         * comparando elemento a elemento (mesma abordagem do GigU - qf0.smali).
         * Retorna a maior razão [0.0..1.0] onde 2 * overlap >= min(h1, h2).
         */
        fun maxVerticalOverlapRatio(item: TextItem): Double {
            var maxRatio = 0.0
            for (existing in items) {
                val overlap = minOf(existing.box.bottom, item.box.bottom) - maxOf(existing.box.top, item.box.top)
                if (overlap > 0) {
                    val minHeight = minOf(existing.height, item.height)
                    if (minHeight > 0 && 2 * overlap >= minHeight) {
                        val ratio = overlap.toDouble() / minHeight
                        if (ratio > maxRatio) {
                            maxRatio = ratio
                        }
                    }
                }
            }
            return maxRatio
        }

        fun add(item: TextItem) {
            items.add(item)
        }
    }

    /**
     * Extrai os elementos do resultado do ML Kit e reconstrói as linhas
     * agrupadas e ordenadas geometricamente.
     */
    fun reconstruct(visionText: Text): List<String> {
        val allItems = mutableListOf<TextItem>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                if (line.elements.isNotEmpty()) {
                    for (element in line.elements) {
                        val box = element.boundingBox
                        val text = normalize(element.text)
                        if (box != null && text.isNotEmpty()) {
                            allItems.add(TextItem(text, box))
                        }
                    }
                } else {
                    val box = line.boundingBox
                    val text = normalize(line.text)
                    if (box != null && text.isNotEmpty()) {
                        allItems.add(TextItem(text, box))
                    }
                }
            }
        }

        // Fallback: se nenhum elemento com bounding box foi encontrado, usa o texto linear
        if (allItems.isEmpty()) {
            return visionText.textBlocks.flatMap { b ->
                b.lines.mapNotNull { normalize(it.text).takeIf { t -> t.isNotEmpty() } }
            }
        }

        return reconstructFromItems(allItems)
    }

    /**
     * Agrupa geometricamente uma lista de [TextItem] por baseline vertical (>= 50% de sobreposição)
     * e ordena palavras da esquerda para a direita, e linhas de cima para baixo.
     */
    fun reconstructFromItems(items: List<TextItem>): List<String> {
        if (items.isEmpty()) return emptyList()

        // Ordena elementos iniciais por coordenada vertical (top)
        val sortedItems = items.sortedBy { it.box.top }
        val groups = mutableListOf<LineGroup>()

        for (item in sortedItems) {
            var bestGroup: LineGroup? = null
            var bestRatio = 0.0

            for (group in groups) {
                val ratio = group.maxVerticalOverlapRatio(item)
                // Critério: sobreposição vertical >= 50% (2 * overlap >= minHeight)
                if (ratio >= 0.50 && ratio > bestRatio) {
                    bestRatio = ratio
                    bestGroup = group
                }
            }

            if (bestGroup != null) {
                bestGroup.add(item)
            } else {
                val newGroup = LineGroup()
                newGroup.add(item)
                groups.add(newGroup)
            }
        }

        // 1. Ordena os elementos de cada linha da esquerda para a direita (left)
        for (group in groups) {
            group.items.sortBy { it.box.left }
        }

        // 2. Ordena as linhas completas de cima para baixo (top)
        groups.sortBy { it.top }

        // 3. Monta as strings de cada linha
        return groups.mapNotNull { group ->
            val line = group.items.joinToString(" ") { it.text }.trim()
            if (line.isNotEmpty()) line else null
        }
    }

    private fun normalize(raw: String): String =
        raw.replace(' ', ' ').replace(' ', ' ').trim()
}
