package org.opendriver.core.model

/**
 * Representa os custos operacionais do veículo do motorista.
 *
 * @property name Nome/Identificação do veículo (ex: "Chevrolet Onix 1.0" ou "BYD Dolphin Mini")
 * @property fuelType Tipo de combustível utilizado (Gasolina, Etanol, Elétrico, GNV, Diesel)
 * @property fuelPrice Preço por litro/m³/kWh em R$ (ex: 5.80 na gasolina ou 0.90 no kWh)
 * @property fuelConsumption Consumo médio para combustão/GNV em km/l ou km/m³ (ex: 11.5)
 * @property kwhPer100Km Consumo médio de veículo elétrico em kWh a cada 100 km (ex: 13.0)
 * @property maintenanceCostPerKm Custo estimado de manutenção preventiva por km (ex: 0.20 para combustão ou 0.08 para elétrico)
 * @property depreciationCostPerKm Desvalorização veicular por km rodado (ex: 0.15)
 * @property dailyFixedCost Custos fixos diários (seguro, IPVA, taxas, rateio de aluguel/parcela)
 * @property estimatedDailyKm Média estimada de km rodados por dia trabalhado (para rateio fixo)
 */
data class VehicleProfile(
    val name: String = "Meu Veículo",
    val fuelType: FuelType = FuelType.GASOLINE,
    val fuelPrice: Double = 5.80,
    val fuelConsumption: Double = 11.5,
    val kwhPer100Km: Double = 13.0,
    val maintenanceCostPerKm: Double = 0.20,
    val depreciationCostPerKm: Double = 0.15,
    val dailyFixedCost: Double = 20.00,
    val estimatedDailyKm: Double = 120.0
) {
    /** Custo de energia/combustível por km rodado (R$/km) */
    val fuelCostPerKm: Double
        get() = when (fuelType) {
            FuelType.ELECTRIC -> {
                // No painel do elétrico: ex. 13 kWh a cada 100 km => (13 / 100) * R$/kWh
                if (kwhPer100Km > 0) (kwhPer100Km / 100.0) * fuelPrice else 0.0
            }
            else -> {
                if (fuelConsumption > 0) fuelPrice / fuelConsumption else 0.0
            }
        }

    /** Custo fixo rateado por km estimado (R$/km) */
    val fixedCostPerKm: Double
        get() = if (estimatedDailyKm > 0) dailyFixedCost / estimatedDailyKm else 0.0

    /** Custo operacional variável direto por km (Combustível/Energia + Manutenção + Depreciação) */
    val variableCostPerKm: Double
        get() = fuelCostPerKm + maintenanceCostPerKm + depreciationCostPerKm

    /** Custo operacional total por km (incluindo fixos rateados) */
    val totalCostPerKm: Double
        get() = variableCostPerKm + fixedCostPerKm
}
