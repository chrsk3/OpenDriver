package org.opendriver.core.model

/**
 * Metas e preferências do motorista que classificam a corrida no semáforo.
 *
 * Cada métrica tem dois limites: um "bom" (atinge a meta → verde) e um "ruim"
 * (abaixo → vermelho). Entre eles, fica amarelo.
 */
data class DriverPreferences(
    val minNetPerKm: Double = 1.20,
    val ruimNetPerKm: Double = 0.50,
    val minNetHourlyRate: Double = 35.00,
    val ruimNetHourlyRate: Double = 20.00,
    val minGrossPerKm: Double = 2.00,
    val minPassengerRating: Double = 4.90,
    val ruimPassengerRating: Double = 4.70,
    val maxPickupDistanceKm: Double = 4.0
)
