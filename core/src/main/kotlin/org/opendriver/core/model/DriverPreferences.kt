package org.opendriver.core.model

/**
 * Metas e preferências configuradas pelo motorista para classificar a corrida.
 *
 * @property minGrossPerKm Mínimo aceitável de ganho bruto por km total (ex: R$ 2.00)
 * @property minNetPerKm Mínimo aceitável de lucro líquido por km total (ex: R$ 1.20)
 * @property minNetHourlyRate Mínimo aceitável de lucro líquido por hora trabalhada (ex: R$ 35.00)
 * @property maxPickupDistanceKm Distância máxima aceitável de deslocamento até o passageiro (ex: 3.5 km)
 * @property minPassengerRating Nota mínima aceitável do passageiro (ex: 4.75)
 */
data class DriverPreferences(
    val minGrossPerKm: Double = 2.00,
    val minNetPerKm: Double = 1.20,
    val minNetHourlyRate: Double = 35.00,
    val maxPickupDistanceKm: Double = 4.0,
    val minPassengerRating: Double = 4.75
)
