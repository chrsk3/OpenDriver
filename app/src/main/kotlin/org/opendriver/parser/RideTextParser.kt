package org.opendriver.parser

import com.google.mlkit.vision.text.Text
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
    private val DURATION_REGEX = Regex(
        "([0-9]+)" + SP + "*h(?:" + SP + "*([0-9]+)" + SP + "*min)?|([0-9]+)" + SP + "*min",
        RegexOption.IGNORE_CASE
    )
    // OCR lê a estrela como "*"; aceita formatos como "4,95 (66)", "★ 4.92", "* 4.92", "4.8 ★", "4.8 *", "nota 4.8".
    private val RATING_REGEX = Regex(
        "([3-5][.,][0-9]{1,2})" + SP + "*\\([0-9]+\\)|" +
        "[★*]" + SP + "*([3-5][.,][0-9]{1,2})|" +
        "([3-5][.,][0-9]{1,2})" + SP + "*[★*]|" +
        "nota" + SP + "*:?" + SP + "*([3-5][.,][0-9]{1,2})",
        RegexOption.IGNORE_CASE
    )

    private val PICKUP_PREFIX_REGEX = Regex(
        "^(?:embarque|buscar\\s+em|origem|partida|retirada)[:\\s]*(.*)$",
        RegexOption.IGNORE_CASE
    )

    private val DESTINATION_PREFIX_REGEX = Regex(
        "^(?:destino|desembarque|levar\\s+para|entrega)[:\\s]*(.*)$",
        RegexOption.IGNORE_CASE
    )

    private val STREET_PREFIX_REGEX = Regex(
        "^(?:rua|r\\.|av\\.|avenida|alameda|al\\.|praça|praca|pça|rodovia|rod\\.|estrada|estr\\.|travessa|tv\\.)\\b",
        RegexOption.IGNORE_CASE
    )

    /**
     * Analisa o objeto completo [Text] do ML Kit utilizando reconstrução geométrica
     * de linhas (sobreposição vertical >= 50% e ordenação horizontal) antes de passar pelo parser regex.
     */
    fun parse(packageName: String, visionText: Text): RideOffer? =
        parse(packageName, LineReconstructor.reconstruct(visionText))

    /**
     * Analisa uma lista de textos coletados da tela do app e tenta montar uma RideOffer válida.
     *
     * @param packageName Nome do pacote do app em primeiro plano (ex: com.ubercab.driver, com.app99.driver)
     * @param screenTexts Lista de todas as strings visíveis capturadas na árvore de acessibilidade
     */
    fun parse(packageName: String, screenTexts: List<String>): RideOffer? {
        val fullContent = screenTexts.joinToString(" \n ")

        // Detecta a plataforma
        val platform = when {
            packageName.contains("uber", ignoreCase = true) || fullContent.contains("Uber", ignoreCase = true) -> Platform.UBER
            packageName.contains("app99", ignoreCase = true) ||
                packageName.contains("taxis99", ignoreCase = true) ||
                packageName.contains("didiglobal", ignoreCase = true) ||
                packageName.contains("99", ignoreCase = true) ||
                fullContent.contains("99Pop", ignoreCase = true) ||
                fullContent.contains("99Comfort", ignoreCase = true) ||
                fullContent.contains("99Moto", ignoreCase = true) ||
                fullContent.contains("99Entrega", ignoreCase = true) ||
                fullContent.contains("99Negocia", ignoreCase = true) ||
                fullContent.contains("99Plus", ignoreCase = true) ||
                fullContent.contains("99Taxi", ignoreCase = true) ||
                fullContent.contains("99Táxi", ignoreCase = true) -> Platform.NINETY_NINE
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
        val durations = DURATION_REGEX.findAll(fullContent).mapNotNull { m ->
            val h = m.groupValues[1].toIntOrNull()
            val mAfterH = m.groupValues[2].toIntOrNull()
            val onlyMin = m.groupValues[3].toIntOrNull()
            when {
                h != null -> (h * 60 + (mAfterH ?: 0)).toDouble()
                onlyMin != null -> onlyMin.toDouble()
                else -> null
            }
        }.filter { it > 0 }.toList()

        val (pickupDuration, tripDuration) = when {
            durations.size >= 2 -> Pair(durations[0], durations[1])
            durations.size == 1 -> Pair(0.0, durations[0])
            else -> Pair(0.0, (tripDistance * 3.0).coerceAtLeast(5.0)) // Estimativa padrão se tempo não vier
        }

        // 4. Extração da Nota do Passageiro
        val ratingMatch = RATING_REGEX.find(fullContent)
        val passengerRating = ratingMatch?.let { match ->
            val ratingStr = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
            ratingStr?.replace(",", ".")?.toDoubleOrNull()
        }

        // 5. Categoria (UberX, Comfort, 99Pop, etc.)
        val category = when {
            fullContent.contains("99Pop", ignoreCase = true) -> "99Pop"
            fullContent.contains("99Comfort", ignoreCase = true) -> "99Comfort"
            fullContent.contains("99Plus", ignoreCase = true) -> "99Plus"
            fullContent.contains("99Negocia", ignoreCase = true) -> "99Negocia"
            fullContent.contains("99Moto", ignoreCase = true) -> "99Moto"
            fullContent.contains("99Taxi", ignoreCase = true) || fullContent.contains("99Táxi", ignoreCase = true) -> "99Taxi"
            fullContent.contains("99Entrega", ignoreCase = true) -> "99Entrega"
            fullContent.contains("Comfort", ignoreCase = true) -> "Comfort"
            fullContent.contains("Black", ignoreCase = true) -> "Black"
            fullContent.contains("Moto", ignoreCase = true) -> "Moto"
            fullContent.contains("Flash", ignoreCase = true) || fullContent.contains("Entrega", ignoreCase = true) -> "Entrega"
            fullContent.contains("UberX", ignoreCase = true) -> "UberX"
            else -> "Padrão"
        }

        // 6. Extração de Endereços (Embarque e Destino)
        val (pickupAddress, destinationAddress) = extractAddresses(screenTexts)

        return RideOffer(
            platform = platform,
            category = category,
            grossFare = grossFare,
            pickupDistanceKm = pickupDistance,
            tripDistanceKm = tripDistance,
            pickupDurationMinutes = pickupDuration,
            tripDurationMinutes = tripDuration,
            passengerRating = passengerRating,
            pickupAddress = pickupAddress,
            destinationAddress = destinationAddress
        )
    }

    private fun extractAddresses(screenTexts: List<String>): Pair<String?, String?> {
        var pickup: String? = null
        var destination: String? = null

        // 1. Procura por prefixos explícitos (na mesma linha ou linha seguinte)
        for (i in screenTexts.indices) {
            val line = screenTexts[i].trim()
            if (line.isEmpty()) continue

            // Prefixo de embarque
            val pickupMatch = PICKUP_PREFIX_REGEX.matchEntire(line)
            if (pickupMatch != null && pickup == null) {
                val value = pickupMatch.groupValues[1].trim()
                if (value.isNotEmpty()) {
                    pickup = value
                } else if (i + 1 < screenTexts.size) {
                    val nextLine = screenTexts[i + 1].trim()
                    if (isAddressCandidate(nextLine)) {
                        pickup = nextLine
                    }
                }
            }

            // Prefixo de destino
            val destMatch = DESTINATION_PREFIX_REGEX.matchEntire(line)
            if (destMatch != null && destination == null) {
                val value = destMatch.groupValues[1].trim()
                if (value.isNotEmpty() && !value.equals("da corrida", ignoreCase = true)) {
                    destination = value
                } else if (i + 1 < screenTexts.size) {
                    val nextLine = screenTexts[i + 1].trim()
                    if (isAddressCandidate(nextLine)) {
                        destination = nextLine
                    }
                }
            }
        }

        // 2. Se ainda faltar embarque ou destino, busca por nomes de logradouros
        if (pickup == null || destination == null) {
            val streetCandidates = screenTexts.map { it.trim() }.filter { line ->
                line != pickup && line != destination &&
                    STREET_PREFIX_REGEX.containsMatchIn(line) &&
                    isAddressCandidate(line)
            }
            if (pickup == null && streetCandidates.isNotEmpty()) {
                pickup = streetCandidates[0]
            }
            if (destination == null && streetCandidates.size > 1) {
                destination = streetCandidates[1]
            }
        }

        return Pair(pickup, destination)
    }

    private fun isAddressCandidate(text: String): Boolean {
        if (text.length < 3 || text.length > 120) return false
        if (FARE_REGEX.containsMatchIn(text)) return false
        if (DISTANCE_REGEX.containsMatchIn(text)) return false
        if (DURATION_REGEX.containsMatchIn(text)) return false
        if (RATING_REGEX.containsMatchIn(text)) return false
        val lower = text.lowercase()
        if (lower in setOf(
                "aceitar", "recusar", "selecionar", "escolher", "voltar", "cancelar",
                "99pop", "99comfort", "99moto", "99negocia", "99entrega", "99plus", "99taxi",
                "uberx", "comfort", "black"
            )
        ) return false
        return true
    }
}
