package org.opendriver.parser

import org.opendriver.core.model.Platform
import org.opendriver.core.model.RideOffer

/**
 * Parser inteligente responsável por extrair dados de corridas a partir
 * dos textos capturados na tela do app de motorista (Uber / 99).
 */
object RideTextParser {

    // Apps usam espaço inquebrável (U+00A0) entre "R$" e o valor; por isso \s não basta.
    private const val SP = "[\\s\\u00A0\\u202F]"
    private val FARE_REGEX = Regex("R\\$" + SP + "*([0-9]+[.,][0-9]{2})", RegexOption.IGNORE_CASE)
    private val DISTANCE_REGEX = Regex("([0-9]+(?:[.,][0-9]+)?)" + SP + "*km", RegexOption.IGNORE_CASE)
    private val DURATION_REGEX = Regex("([0-9]+)" + SP + "*min", RegexOption.IGNORE_CASE)
    // OCR lê a estrela como "*"; aceita também o formato "4,95 (66)" (nota + nº de corridas).
    private val RATING_REGEX = Regex(
        "([3-5][.,][0-9]{1,2})" + SP + "*\\([0-9]+\\)|[★*]" + SP + "*([3-5][.,][0-9]{1,2})"
    )

    /**
     * Analisa uma lista de textos coletados da tela do app e tenta montar uma RideOffer válida.
     *
     * @param packageName Nome do pacote do app em primeiro plano (ex: com.ubercab.driver)
     * @param screenTexts Lista de todas as strings visíveis capturadas na árvore de acessibilidade
     */
    fun parse(packageName: String, screenTexts: List<String>): RideOffer? {
        val fullContent = screenTexts.joinToString(" \n ")

        // Detecta a plataforma
        val platform = when {
            packageName.contains("uber", ignoreCase = true) || fullContent.contains("Uber", ignoreCase = true) -> Platform.UBER
            packageName.contains("99", ignoreCase = true) || fullContent.contains("99Pop", ignoreCase = true) -> Platform.NINETY_NINE
            packageName.contains("indrive", ignoreCase = true) -> Platform.INDRIVE
            else -> Platform.OTHER
        }

        // 1. Extração do Valor da Corrida (R$)
        val fareMatches = FARE_REGEX.findAll(fullContent).mapNotNull {
            it.groupValues[1].replace(",", ".").toDoubleOrNull()
        }.toList()

        if (fareMatches.isEmpty()) {
            return null // Sem valor de corrida identificado
        }

        // O valor da corrida normalmente é o maior valor monetário na tela da oferta
        val grossFare = fareMatches.maxOrNull() ?: return null
        if (grossFare <= 1.0) return null // Ignora valores irrisórios/inválidos

        // 2. Extração de Distâncias (km)
        val distances = DISTANCE_REGEX.findAll(fullContent).mapNotNull {
            it.groupValues[1].replace(",", ".").toDoubleOrNull()
        }.filter { it > 0.05 }.toList()

        val (pickupDistance, tripDistance) = when {
            distances.size >= 2 -> Pair(distances[0], distances[1])
            distances.size == 1 -> Pair(0.0, distances[0])
            else -> return null // Sem distância identificada
        }

        // 3. Extração de Tempos (minutos)
        val durations = DURATION_REGEX.findAll(fullContent).mapNotNull {
            it.groupValues[1].toDoubleOrNull()
        }.filter { it > 0 }.toList()

        val (pickupDuration, tripDuration) = when {
            durations.size >= 2 -> Pair(durations[0], durations[1])
            durations.size == 1 -> Pair(0.0, durations[0])
            else -> Pair(0.0, (tripDistance * 3.0).coerceAtLeast(5.0)) // Estimativa padrão se tempo não vier
        }

        // 4. Extração da Nota do Passageiro
        val ratingMatch = RATING_REGEX.find(fullContent)
        val passengerRating = ratingMatch?.let {
            val ratingStr = it.groupValues[1].ifEmpty { it.groupValues[2] }
            ratingStr.replace(",", ".").toDoubleOrNull()
        }

        // 5. Categoria (UberX, Comfort, 99Pop, etc.)
        val category = when {
            fullContent.contains("Comfort", ignoreCase = true) -> "Comfort"
            fullContent.contains("Black", ignoreCase = true) -> "Black"
            fullContent.contains("99Pop", ignoreCase = true) -> "99Pop"
            fullContent.contains("99Plus", ignoreCase = true) -> "99Plus"
            fullContent.contains("99Moto", ignoreCase = true) || fullContent.contains("Moto", ignoreCase = true) -> "Moto"
            fullContent.contains("Flash", ignoreCase = true) || fullContent.contains("Entrega", ignoreCase = true) -> "Entrega"
            else -> "Padrão"
        }

        return RideOffer(
            platform = platform,
            category = category,
            grossFare = grossFare,
            pickupDistanceKm = pickupDistance,
            tripDistanceKm = tripDistance,
            pickupDurationMinutes = pickupDuration,
            tripDurationMinutes = tripDuration,
            passengerRating = passengerRating
        )
    }
}
