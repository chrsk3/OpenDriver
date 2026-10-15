package org.opendriver.data

import org.opendriver.data.local.RideEntity

/** Uma corrida capturada, guardada no histórico local. */
data class RideHistoryEntry(
    val timeMs: Long,
    val platform: String,
    val fare: Double,
    val distanceKm: Double,
    val profit: Double,
    val status: String,
    val id: Long = 0,
    val durationMinutes: Double = 0.0,
    val netPerKm: Double = 0.0,
    val netHourlyRate: Double = 0.0,
    val pickupAddress: String? = null,
    val destinationAddress: String? = null
) {
    fun serialize(): String = listOf(timeMs, platform, fare, distanceKm, profit, status).joinToString(";")

    fun toEntity(): RideEntity = RideEntity(
        id = id,
        timestamp = timeMs,
        platform = platform,
        grossFare = fare,
        totalDistanceKm = distanceKm,
        totalDurationMinutes = durationMinutes,
        netProfit = profit,
        netPerKm = netPerKm,
        netHourlyRate = netHourlyRate,
        status = status,
        pickupAddress = pickupAddress,
        destinationAddress = destinationAddress
    )

    companion object {
        fun parse(line: String): RideHistoryEntry? {
            val p = line.split(";")
            if (p.size < 6) return null
            return try {
                RideHistoryEntry(
                    timeMs = p[0].toLong(),
                    platform = p[1],
                    fare = p[2].toDouble(),
                    distanceKm = p[3].toDouble(),
                    profit = p[4].toDouble(),
                    status = p[5]
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
