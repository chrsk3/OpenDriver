package org.opendriver.core.model

/**
 * Resultado detalhado da avaliação de uma corrida.
 */
data class ViabilityResult(
    val offer: RideOffer,
    val profile: VehicleProfile,
    val preferences: DriverPreferences,

    // Custos calculados em R$
    val totalEstimatedCost: Double,
    val fuelCost: Double,
    val maintenanceCost: Double,
    val depreciationCost: Double,
    val fixedCost: Double,

    // Lucros calculados em R$
    val netProfit: Double,               // Lucro líquido real final
    val immediateCashProfit: Double,     // Dinheiro imediato de bolso (Bruto - Combustível)

    // Métricas unitárias
    val grossPerKm: Double,              // Ganho bruto por km (R$/km)
    val netPerKm: Double,                // Lucro líquido por km (R$/km)
    val grossHourlyRate: Double,         // Ganho bruto por hora (R$/h)
    val netHourlyRate: Double,           // Lucro líquido por hora (R$/h)

    // Avaliação final
    val status: ViabilityStatus,
    val alerts: List<String>             // Alertas (ex: "Deslocamento longo", "Nota baixa", etc.)
)
