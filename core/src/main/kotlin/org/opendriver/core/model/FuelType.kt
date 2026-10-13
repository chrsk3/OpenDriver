package org.opendriver.core.model

enum class FuelType(val displayName: String, val unitLabel: String) {
    GASOLINE("Gasolina", "litro"),
    ETHANOL("Etanol", "litro"),
    CNG("GNV", "m³"),
    DIESEL("Diesel", "litro"),
    ELECTRIC("Elétrico", "kWh")
}
