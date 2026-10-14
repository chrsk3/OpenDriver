package org.opendriver.data

/** Uma corrida capturada, guardada no histórico local. */
data class RideHistoryEntry(
    val timeMs: Long,
    val platform: String,
    val fare: Double,
    val distanceKm: Double,
    val profit: Double,
    val status: String
) {
    fun serialize(): String = listOf(timeMs, platform, fare, distanceKm, profit, status).joinToString(";")

    companion object {
        fun parse(line: String): RideHistoryEntry? {
            val p = line.split(";")
            if (p.size < 6) return null
            return try {
                RideHistoryEntry(p[0].toLong(), p[1], p[2].toDouble(), p[3].toDouble(), p[4].toDouble(), p[5])
            } catch (_: Exception) {
                null
            }
        }
    }
}
