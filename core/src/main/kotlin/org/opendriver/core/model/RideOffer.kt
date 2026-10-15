package org.opendriver.core.model

/**
 * Representa os dados capturados da oferta de corrida.
 *
 * @property platform Plataforma de origem (Uber, 99, etc.)
 * @property category Categoria (ex: "UberX", "Comfort", "99Pop")
 * @property grossFare Valor bruto oferecido ao motorista em R$ (ex: 28.50)
 * @property pickupDistanceKm Distância até o ponto de embarque do passageiro (ex: 1.5)
 * @property tripDistanceKm Distância do trajeto do passageiro até o destino (ex: 9.0)
 * @property pickupDurationMinutes Tempo estimado até o embarque em minutos (ex: 5.0)
 * @property tripDurationMinutes Tempo estimado de viagem em minutos (ex: 18.0)
 * @property passengerRating Nota do passageiro (se disponível, ex: 4.92)
 * @property tollFare Pedágio reembolsado ou valor extra (R$)
 */
data class RideOffer(
    val platform: Platform = Platform.UBER,
    val category: String = "Padrão",
    val grossFare: Double,
    val pickupDistanceKm: Double = 0.0,
    val tripDistanceKm: Double,
    val pickupDurationMinutes: Double = 0.0,
    val tripDurationMinutes: Double,
    val passengerRating: Double? = null,
    val tollFare: Double = 0.0,
    val pickupAddress: String? = null,
    val destinationAddress: String? = null
) {
    /** Distância total a ser percorrida (Busca + Viagem) */
    val totalDistanceKm: Double
        get() = pickupDistanceKm + tripDistanceKm

    /** Duração total estimada em minutos (Busca + Viagem) */
    val totalDurationMinutes: Double
        get() = pickupDurationMinutes + tripDurationMinutes

    /** Duração total convertida para horas */
    val totalDurationHours: Double
        get() = totalDurationMinutes / 60.0

    /** Proporção do trajeto gasta apenas buscando o passageiro (0.0 a 1.0) */
    val pickupRatio: Double
        get() = if (totalDistanceKm > 0) pickupDistanceKm / totalDistanceKm else 0.0
}
