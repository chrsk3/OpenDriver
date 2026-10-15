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
            "Rua das Flores, 123",
            "7,5 km",
            "20 min",
            "Av. Paulista, 1000",
            "4.8 ★",
            "Aceitar"
        )

        val offer = RideTextParser.parse("com.app99.driver", screenTexts)

        assertNotNull(offer)
        assertEquals(Platform.NINETY_NINE, offer!!.platform)
        assertEquals("99Pop", offer.category)
        assertEquals(18.90, offer.grossFare, 0.01)
        assertEquals(2.1, offer.pickupDistanceKm, 0.01)
        assertEquals(7.5, offer.tripDistanceKm, 0.01)
        assertEquals(6.0, offer.pickupDurationMinutes, 0.01)
        assertEquals(20.0, offer.tripDurationMinutes, 0.01)
        assertEquals(4.8, offer.passengerRating ?: 0.0, 0.01)
        assertEquals("Rua das Flores, 123", offer.pickupAddress)
        assertEquals("Av. Paulista, 1000", offer.destinationAddress)
    }

    @Test
    fun testParse99WithPrefixAddresses() {
        val screenTexts = listOf(
            "99Comfort",
            "R$ 32,50",
            "1,5 km • 5 min",
            "Embarque: Rua Augusta, 500",
            "12,0 km • 35 min",
            "Destino: Av. Brigadeiro Faria Lima, 2000",
            "nota: 4,9",
            "Aceitar corrida"
        )

        val offer = RideTextParser.parse("com.app99.radar.driver", screenTexts)

        assertNotNull(offer)
        assertEquals(Platform.NINETY_NINE, offer!!.platform)
        assertEquals("99Comfort", offer.category)
        assertEquals(32.50, offer.grossFare, 0.01)
        assertEquals(1.5, offer.pickupDistanceKm, 0.01)
        assertEquals(12.0, offer.tripDistanceKm, 0.01)
        assertEquals(4.9, offer.passengerRating ?: 0.0, 0.01)
        assertEquals("Rua Augusta, 500", offer.pickupAddress)
        assertEquals("Av. Brigadeiro Faria Lima, 2000", offer.destinationAddress)
    }

    @Test
    fun testParseDurationWithHours() {
        val screenTexts = listOf(
            "99Pop",
            "R$ 115,00",
            "3,0 km",
            "10 min",
            "65,0 km",
            "1 h 15 min",
            "Aceitar"
        )

        val offer = RideTextParser.parse("com.didiglobal.driver", screenTexts)

        assertNotNull(offer)
        assertEquals(Platform.NINETY_NINE, offer!!.platform)
        assertEquals(115.00, offer.grossFare, 0.01)
        assertEquals(3.0, offer.pickupDistanceKm, 0.01)
        assertEquals(65.0, offer.tripDistanceKm, 0.01)
        assertEquals(10.0, offer.pickupDurationMinutes, 0.01)
        assertEquals(75.0, offer.tripDurationMinutes, 0.01)
    }
}
