package org.opendriver.core

import org.opendriver.core.calculator.ViabilityEngine
import org.opendriver.core.model.*

fun main() {
    println("🧪 Executando testes unitários do OpenDriver...")

    testLucroLiquidoBasico()
    testCorridaPrejuizo()
    testAlertaBuscaLonga()
    testSegurancaDivisaoPorZero()

    println("✅ Todos os testes passaram com sucesso!")
}

fun assertTrue(condition: Boolean, message: String) {
    if (!condition) {
        throw AssertionError("FALHA: $message")
    }
}

fun assertEquals(expected: Double, actual: Double, delta: Double = 0.01, message: String) {
    if (Math.abs(expected - actual) > delta) {
        throw AssertionError("FALHA: $message (Esperado: $expected, Atual: $actual)")
    }
}

fun testLucroLiquidoBasico() {
    val perfil = VehicleProfile(
        fuelPrice = 5.00,
        fuelConsumption = 10.0, // R$ 0.50/km
        maintenanceCostPerKm = 0.20,
        depreciationCostPerKm = 0.10,
        dailyFixedCost = 0.0 // Zerado para simplificar teste
    )
    // Custo/km = 0.50 + 0.20 + 0.10 = 0.80/km

    val corrida = RideOffer(
        grossFare = 20.00,
        pickupDistanceKm = 2.0,
        tripDistanceKm = 8.0,
        pickupDurationMinutes = 5.0,
        tripDurationMinutes = 15.0 // Total 10 km, 20 min (1/3 hora)
    )

    val resultado = ViabilityEngine.evaluate(corrida, perfil)

    // Custo total: 10 km * 0.80 = 8.00
    assertEquals(8.00, resultado.totalEstimatedCost, message = "Custo total estimado")
    // Lucro líquido: 20.00 - 8.00 = 12.00
    assertEquals(12.00, resultado.netProfit, message = "Lucro líquido")
    // Lucro caixa: 20.00 - (10 * 0.50) = 15.00
    assertEquals(15.00, resultado.immediateCashProfit, message = "Lucro de caixa")
    // Lucro/km: 12.00 / 10 = 1.20
    assertEquals(1.20, resultado.netPerKm, message = "Lucro por km")
    // Lucro/hora: 12.00 / (20/60) = 36.00/h
    assertEquals(36.00, resultado.netHourlyRate, message = "Lucro por hora")

    assertTrue(resultado.status == ViabilityStatus.GOOD || resultado.status == ViabilityStatus.EXCELLENT, "Status deve ser positivo")
}

fun testCorridaPrejuizo() {
    val perfil = VehicleProfile(
        fuelPrice = 6.00,
        fuelConsumption = 10.0, // 0.60
        maintenanceCostPerKm = 0.30,
        depreciationCostPerKm = 0.20,
        dailyFixedCost = 0.0
    ) // Custo = 1.10/km

    val corrida = RideOffer(
        grossFare = 8.00,
        pickupDistanceKm = 3.0,
        tripDistanceKm = 7.0, // Total 10 km
        pickupDurationMinutes = 5.0,
        tripDurationMinutes = 15.0
    )

    // Custo: 10 * 1.10 = 11.00. Ganho: 8.00 => Prejuízo de -3.00
    val resultado = ViabilityEngine.evaluate(corrida, perfil)
    assertTrue(resultado.netProfit < 0, "Corrida deve dar prejuízo")
    assertTrue(resultado.status == ViabilityStatus.UNPROFITABLE, "Status deve ser UNPROFITABLE")
    assertTrue(resultado.alerts.any { it.contains("Prejuízo") }, "Deve conter alerta de prejuízo")
}

fun testAlertaBuscaLonga() {
    val metas = DriverPreferences(maxPickupDistanceKm = 3.0)
    val corrida = RideOffer(
        grossFare = 25.00,
        pickupDistanceKm = 5.5, // Maior que 3.0
        tripDistanceKm = 5.0,
        pickupDurationMinutes = 12.0,
        tripDurationMinutes = 12.0
    )

    val resultado = ViabilityEngine.evaluate(corrida, preferences = metas)
    assertTrue(resultado.alerts.any { it.contains("Deslocamento longo") }, "Deve alertar busca longa")
    assertTrue(resultado.alerts.any { it.contains("buscar o passageiro") }, "Deve alertar proporção de busca alta")
}

fun testSegurancaDivisaoPorZero() {
    val perfil = VehicleProfile(fuelConsumption = 0.0, estimatedDailyKm = 0.0)
    val corrida = RideOffer(
        grossFare = 0.0,
        pickupDistanceKm = 0.0,
        tripDistanceKm = 0.0,
        pickupDurationMinutes = 0.0,
        tripDurationMinutes = 0.0
    )

    val resultado = ViabilityEngine.evaluate(corrida, perfil)
    assertEquals(0.0, resultado.grossPerKm, message = "Divisão segura de km")
    assertEquals(0.0, resultado.grossHourlyRate, message = "Divisão segura de horas")
}
