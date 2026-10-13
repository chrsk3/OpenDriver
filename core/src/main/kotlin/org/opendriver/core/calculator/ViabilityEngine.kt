package org.opendriver.core.calculator

import org.opendriver.core.model.*

/**
 * Motor central de cálculo e classificação de viabilidade de corridas.
 */
object ViabilityEngine {

    /**
     * Avalia uma oferta de corrida com base no perfil do veículo e preferências do motorista.
     */
    fun evaluate(
        offer: RideOffer,
        profile: VehicleProfile = VehicleProfile(),
        preferences: DriverPreferences = DriverPreferences()
    ): ViabilityResult {
        val totalDistance = offer.totalDistanceKm
        val totalHours = offer.totalDurationHours

        // Custos proporcionais à distância total rodada
        val fuelCost = totalDistance * profile.fuelCostPerKm
        val maintenanceCost = totalDistance * profile.maintenanceCostPerKm
        val depreciationCost = totalDistance * profile.depreciationCostPerKm
        val fixedCost = totalDistance * profile.fixedCostPerKm
        val totalCost = fuelCost + maintenanceCost + depreciationCost + fixedCost

        // Ganhos e Lucro
        val effectiveGross = offer.grossFare - offer.tollFare
        val netProfit = effectiveGross - totalCost
        val cashProfit = effectiveGross - fuelCost

        // Taxas unitárias
        val grossPerKm = if (totalDistance > 0) offer.grossFare / totalDistance else 0.0
        val netPerKm = if (totalDistance > 0) netProfit / totalDistance else 0.0

        val grossHourly = if (totalHours > 0) offer.grossFare / totalHours else 0.0
        val netHourly = if (totalHours > 0) netProfit / totalHours else 0.0

        // Detecção de alertas e armadilhas comuns para motoristas
        val alerts = mutableListOf<String>()

        if (offer.pickupDistanceKm > preferences.maxPickupDistanceKm) {
            alerts.add("Deslocamento longo: ${formatTwoDecimals(offer.pickupDistanceKm)} km até o embarque")
        }

        if (offer.pickupRatio >= 0.40) {
            val pct = (offer.pickupRatio * 100).toInt()
            alerts.add("Atenção: $pct% da distância total é apenas para buscar o passageiro")
        }

        offer.passengerRating?.let { rating ->
            if (rating < preferences.minPassengerRating) {
                alerts.add("Nota de passageiro baixa: ${formatTwoDecimals(rating)}")
            }
        }

        if (netProfit < 0) {
            alerts.add("Prejuízo real de R$ ${formatTwoDecimals(-netProfit)}")
        } else if (netHourly < preferences.minNetHourlyRate) {
            alerts.add("Rendimento/hora (R$ ${formatTwoDecimals(netHourly)}/h) abaixo da sua meta")
        }

        if (grossPerKm < preferences.minGrossPerKm) {
            alerts.add("Ganho bruto (R$ ${formatTwoDecimals(grossPerKm)}/km) abaixo da sua meta de R$ ${formatTwoDecimals(preferences.minGrossPerKm)}/km")
        }

        // Determinação do status
        val status = when {
            netProfit <= 0 || netPerKm <= 0.20 -> ViabilityStatus.UNPROFITABLE
            netPerKm >= preferences.minNetPerKm && netHourly >= preferences.minNetHourlyRate && offer.pickupDistanceKm <= preferences.maxPickupDistanceKm -> {
                if (grossPerKm >= preferences.minGrossPerKm * 1.30 || netHourly >= preferences.minNetHourlyRate * 1.30) {
                    ViabilityStatus.EXCELLENT
                } else {
                    ViabilityStatus.GOOD
                }
            }
            else -> ViabilityStatus.REGULAR
        }

        return ViabilityResult(
            offer = offer,
            profile = profile,
            preferences = preferences,
            totalEstimatedCost = totalCost,
            fuelCost = fuelCost,
            maintenanceCost = maintenanceCost,
            depreciationCost = depreciationCost,
            fixedCost = fixedCost,
            netProfit = netProfit,
            immediateCashProfit = cashProfit,
            grossPerKm = grossPerKm,
            netPerKm = netPerKm,
            grossHourlyRate = grossHourly,
            netHourlyRate = netHourly,
            status = status,
            alerts = alerts
        )
    }

    private fun formatTwoDecimals(value: Double): String {
        return String.format(java.util.Locale.US, "%.2f", value)
    }
}
