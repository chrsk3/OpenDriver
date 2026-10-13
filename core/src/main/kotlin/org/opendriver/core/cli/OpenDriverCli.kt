package org.opendriver.core.cli

import org.opendriver.core.calculator.ViabilityEngine
import org.opendriver.core.model.*
import java.util.Locale
import java.util.Scanner

fun main(args: Array<String>) {
    if (args.contains("--interativo") || args.contains("-i")) {
        executarModoInterativo()
    } else {
        executarDemonstracao()
    }
}

fun executarDemonstracao() {
    println("==================================================")
    println("       🚀 OpenDriver - Core de Cálculo v1.0       ")
    println("     Calculadora Open-Source de Corridas de App    ")
    println("==================================================")
    println()

    val meuCarro = VehicleProfile(
        name = "Carro 1.0 Flex",
        fuelType = FuelType.GASOLINE,
        fuelPrice = 5.85,
        fuelConsumption = 11.5,      // 11.5 km/l
        maintenanceCostPerKm = 0.20, // R$ 0.20/km
        depreciationCostPerKm = 0.15,// R$ 0.15/km
        dailyFixedCost = 25.00,      // IPVA, Seguro, Limpeza rateado
        estimatedDailyKm = 120.0
    )

    val minhasMetas = DriverPreferences(
        minGrossPerKm = 2.00,
        minNetPerKm = 1.20,
        minNetHourlyRate = 35.00,
        maxPickupDistanceKm = 3.5,
        minPassengerRating = 4.75
    )

    println("🚗 [Configuração Atual do Veículo]")
    println(" • Combustível: ${meuCarro.fuelType.displayName} (R$ %.2f/${meuCarro.fuelType.unitLabel})".format(Locale.US, meuCarro.fuelPrice))
    println(" • Consumo Médio: %.1f km/${meuCarro.fuelType.unitLabel}".format(Locale.US, meuCarro.fuelConsumption))
    println(" • Custo Combustível/km: R$ %.2f".format(Locale.US, meuCarro.fuelCostPerKm))
    println(" • Custo Manutenção/km: R$ %.2f".format(Locale.US, meuCarro.maintenanceCostPerKm))
    println(" • Depreciação/km: R$ %.2f".format(Locale.US, meuCarro.depreciationCostPerKm))
    println(" • Custo Total Real/km: R$ %.2f".format(Locale.US, meuCarro.totalCostPerKm))
    println("--------------------------------------------------\n")

    val cenarios = listOf(
        RideOffer(
            platform = Platform.UBER,
            category = "UberX",
            grossFare = 24.50,
            pickupDistanceKm = 1.2,
            tripDistanceKm = 6.8,
            pickupDurationMinutes = 4.0,
            tripDurationMinutes = 16.0,
            passengerRating = 4.92
        ),
        RideOffer(
            platform = Platform.UBER,
            category = "UberX - Corrida Ilusória",
            grossFare = 18.00,
            pickupDistanceKm = 6.0,
            tripDistanceKm = 5.0,
            pickupDurationMinutes = 15.0,
            tripDurationMinutes = 15.0,
            passengerRating = 4.80
        ),
        RideOffer(
            platform = Platform.NINETY_NINE,
            category = "99Pop",
            grossFare = 62.00,
            pickupDistanceKm = 2.0,
            tripDistanceKm = 28.0,
            pickupDurationMinutes = 5.0,
            tripDurationMinutes = 38.0,
            passengerRating = 4.95
        ),
        RideOffer(
            platform = Platform.UBER,
            category = "UberX - Tarifa Baixa / Trânsito",
            grossFare = 11.20,
            pickupDistanceKm = 2.5,
            tripDistanceKm = 4.5,
            pickupDurationMinutes = 8.0,
            tripDurationMinutes = 25.0,
            passengerRating = 4.60
        )
    )

    cenarios.forEachIndexed { i, corrida ->
        val resultado = ViabilityEngine.evaluate(corrida, meuCarro, minhasMetas)
        exibirResultado(i + 1, resultado)
    }

    println("💡 Dica: Para calcular uma corrida personalizada, execute: ./run.sh -i")
}

fun executarModoInterativo() {
    val scanner = Scanner(System.`in`)
    println("\n=== 📱 OpenDriver: Calculadora Rápida de Corrida ===")

    print("Valor bruto da corrida (R$): ")
    val valor = scanner.next().replace(",", ".").toDoubleOrNull() ?: 0.0

    print("Distância até o passageiro (km) [0 para ignorar]: ")
    val distBusca = scanner.next().replace(",", ".").toDoubleOrNull() ?: 0.0

    print("Distância da viagem com o passageiro (km): ")
    val distViagem = scanner.next().replace(",", ".").toDoubleOrNull() ?: 0.0

    print("Tempo estimado até o passageiro (minutos) [0 para ignorar]: ")
    val tempoBusca = scanner.next().replace(",", ".").toDoubleOrNull() ?: 0.0

    print("Tempo estimado de viagem (minutos): ")
    val tempoViagem = scanner.next().replace(",", ".").toDoubleOrNull() ?: 0.0

    print("Nota do passageiro (ex: 4.85) [ou aperte ENTER]: ")
    val notaStr = scanner.next()
    val nota = notaStr.replace(",", ".").toDoubleOrNull()

    val oferta = RideOffer(
        grossFare = valor,
        pickupDistanceKm = distBusca,
        tripDistanceKm = distViagem,
        pickupDurationMinutes = tempoBusca,
        tripDurationMinutes = tempoViagem,
        passengerRating = nota
    )

    val resultado = ViabilityEngine.evaluate(oferta)
    exibirResultado(1, resultado)
}

fun exibirResultado(numero: Int, res: ViabilityResult) {
    val o = res.offer
    val icone = when (res.status) {
        ViabilityStatus.EXCELLENT -> "🟢 [EXCELENTE]"
        ViabilityStatus.GOOD -> "🟢 [BOA]"
        ViabilityStatus.REGULAR -> "🟡 [REGULAR]"
        ViabilityStatus.UNPROFITABLE -> "🔴 [NÃO COMPENSA / RECUSAR]"
    }

    println("==================================================")
    println("Corrida #$numero - ${o.platform.displayName} (${o.category}) => $icone")
    println("--------------------------------------------------")
    println(" • Valor Bruto: R$ %.2f".format(Locale.US, o.grossFare))
    println(" • Distância Total: %.1f km (Busca: %.1f km | Viagem: %.1f km)".format(Locale.US, o.totalDistanceKm, o.pickupDistanceKm, o.tripDistanceKm))
    println(" • Tempo Total: %.0f min (Busca: %.0f min | Viagem: %.0f min)".format(Locale.US, o.totalDurationMinutes, o.pickupDurationMinutes, o.tripDurationMinutes))
    o.passengerRating?.let {
        println(" • Nota Passageiro: %.2f ⭐".format(Locale.US, it))
    }
    println("------------------ [CUSTOS] ----------------------")
    println(" • Custo Total Estimado: R$ %.2f".format(Locale.US, res.totalEstimatedCost))
    println("   - Combustível: R$ %.2f | Manutenção: R$ %.2f".format(Locale.US, res.fuelCost, res.maintenanceCost))
    println("------------------ [LUCRO] -----------------------")
    println(" • Lucro Líquido Real: R$ %.2f".format(Locale.US, res.netProfit))
    println(" • Lucro de Caixa (Bolso): R$ %.2f".format(Locale.US, res.immediateCashProfit))
    println(" • Ganho Bruto/KM: R$ %.2f/km".format(Locale.US, res.grossPerKm))
    println(" • Lucro Líquido/KM: R$ %.2f/km".format(Locale.US, res.netPerKm))
    println(" • Ganho Bruto/Hora: R$ %.2f/h".format(Locale.US, res.grossHourlyRate))
    println(" • Lucro Líquido/Hora: R$ %.2f/h".format(Locale.US, res.netHourlyRate))

    if (res.alerts.isNotEmpty()) {
        println("------------------ [ALERTAS] ---------------------")
        res.alerts.forEach { alerta ->
            println(" ⚠️  $alerta")
        }
    }
    println("==================================================\n")
}
