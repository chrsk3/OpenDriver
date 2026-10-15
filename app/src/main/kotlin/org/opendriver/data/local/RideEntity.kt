package org.opendriver.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.opendriver.data.RideHistoryEntry

/**
 * Entidade SQLite (Room) representando o histórico de uma corrida capturada.
 *
 * Mapeia os dados completos de oferta, viabilidade e notas:
 * - id: Identificador único autoincremental
 * - timestamp: Data/hora do evento em milissegundos
 * - platform: Plataforma de origem (Uber, 99, etc.)
 * - grossFare: Valor total bruto oferecido
 * - totalDistanceKm: Distância total (busca + viagem) em km
 * - totalDurationMinutes: Duração total estimada em minutos
 * - netProfit: Lucro líquido calculado
 * - grossPerKm: Ganho bruto por km (R$/km)
 * - netPerKm: Lucro líquido por km (R$/km)
 * - grossHourlyRate: Ganho bruto por hora (R$/h)
 * - netHourlyRate: Lucro líquido por hora (R$/h)
 * - status: Avaliação do semáforo ("Excelente", "Boa", "Regular", "Prejuízo / Recusar")
 * - pickupAddress: Endereço de embarque (opcional)
 * - destinationAddress: Endereço de destino (opcional)
 * - passengerRating: Nota do passageiro (opcional)
 * - rawNotes: Observações ou diagnósticos extras (opcional)
 */
@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "platform")
    val platform: String,

    @ColumnInfo(name = "gross_fare")
    val grossFare: Double,

    @ColumnInfo(name = "total_distance_km")
    val totalDistanceKm: Double,

    @ColumnInfo(name = "total_duration_minutes")
    val totalDurationMinutes: Double = 0.0,

    @ColumnInfo(name = "net_profit")
    val netProfit: Double = 0.0,

    @ColumnInfo(name = "gross_per_km")
    val grossPerKm: Double = 0.0,

    @ColumnInfo(name = "net_per_km")
    val netPerKm: Double = 0.0,

    @ColumnInfo(name = "gross_hourly_rate")
    val grossHourlyRate: Double = 0.0,

    @ColumnInfo(name = "net_hourly_rate")
    val netHourlyRate: Double = 0.0,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "pickup_address")
    val pickupAddress: String? = null,

    @ColumnInfo(name = "destination_address")
    val destinationAddress: String? = null,

    @ColumnInfo(name = "passenger_rating")
    val passengerRating: Double? = null,

    @ColumnInfo(name = "raw_notes")
    val rawNotes: String? = null
) {
    fun toHistoryEntry(): RideHistoryEntry = RideHistoryEntry(
        id = id,
        timeMs = timestamp,
        platform = platform,
        fare = grossFare,
        distanceKm = totalDistanceKm,
        profit = netProfit,
        status = status,
        durationMinutes = totalDurationMinutes,
        netPerKm = netPerKm,
        netHourlyRate = netHourlyRate,
        pickupAddress = pickupAddress,
        destinationAddress = destinationAddress
    )
}
