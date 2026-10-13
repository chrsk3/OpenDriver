package org.opendriver.core.model

enum class ViabilityStatus(val label: String, val badgeColor: String) {
    EXCELLENT("Excelente", "GREEN"),
    GOOD("Boa", "LIGHT_GREEN"),
    REGULAR("Regular", "YELLOW"),
    UNPROFITABLE("Prejuízo / Recusar", "RED")
}
