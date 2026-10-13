package org.opendriver.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.opendriver.core.model.Platform

class RideTextParserTest {

    @Test
    fun testParseUberOffer() {
        val screenTexts = listOf(
            "UberX",
            "R$ 24,50",
            "1,2 km",
            "4 min até o local",
            "6,8 km",
            "16 min de viagem",
            "★ 4.92",
            "Aceitar corrida"
        )

        val offer = RideTextParser.parse("com.ubercab.driver", screenTexts)

        assertNotNull(offer)
        assertEquals(Platform.UBER, offer!!.platform)
        assertEquals(24.50, offer.grossFare, 0.01)
        assertEquals(1.2, offer.pickupDistanceKm, 0.01)
        assertEquals(6.8, offer.tripDistanceKm, 0.01)
        assertEquals(4.0, offer.pickupDurationMinutes, 0.01)
        assertEquals(16.0, offer.tripDurationMinutes, 0.01)
        assertEquals(4.92, offer.passengerRating ?: 0.0, 0.01)
    }

    @Test
    fun testParse99Offer() {
        val screenTexts = listOf(
            "99Pop",
            "R$ 18,90",
            "2,1 km",
            "6 min",
            "7,5 km",
            "20 min",
            "4.8 ★"
        )

        val offer = RideTextParser.parse("com.taxis99.driver", screenTexts)

        assertNotNull(offer)
        assertEquals(Platform.NINETY_NINE, offer!!.platform)
        assertEquals(18.90, offer.grossFare, 0.01)
        assertEquals(2.1, offer.pickupDistanceKm, 0.01)
        assertEquals(7.5, offer.tripDistanceKm, 0.01)
    }
}
