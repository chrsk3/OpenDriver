package org.opendriver.data

import android.content.Context
import android.content.SharedPreferences
import org.opendriver.core.model.BubbleSize
import org.opendriver.core.model.DriverPreferences
import org.opendriver.core.model.FuelType
import org.opendriver.core.model.VehicleProfile

/**
 * Repositório para carregar e salvar todas as preferências do motorista localmente.
 */
class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("opendriver_prefs", Context.MODE_PRIVATE)

    // === Copiloto & Bolha Flutuante ===

    fun getCopilotEnabled(): Boolean = prefs.getBoolean("copilot_enabled", true)
    fun setCopilotEnabled(enabled: Boolean) = prefs.edit().putBoolean("copilot_enabled", enabled).apply()

    fun getEarningsCalcEnabled(): Boolean = prefs.getBoolean("earnings_calc_enabled", true)
    fun setEarningsCalcEnabled(enabled: Boolean) = prefs.edit().putBoolean("earnings_calc_enabled", enabled).apply()

    fun getBubbleSize(): BubbleSize {
        val name = prefs.getString("bubble_size", BubbleSize.MEDIO.name) ?: BubbleSize.MEDIO.name
        return try { BubbleSize.valueOf(name) } catch (_: Exception) { BubbleSize.MEDIO }
    }
    fun setBubbleSize(size: BubbleSize) = prefs.edit().putString("bubble_size", size.name).apply()

    /** Opacidade (10–100) do botão flutuante que abre/fecha o card de resultado. */
    fun getFloatingButtonOpacity(): Int =
        prefs.getInt("floating_button_opacity", prefs.getInt("bubble_opacity", 90))

    fun setFloatingButtonOpacity(opacity: Int) = prefs.edit()
        .putInt("floating_button_opacity", opacity.coerceIn(10, 100))
        .apply()

    // === Aplicativos selecionados ===

    fun getSelectedApps(): Set<String> =
        prefs.getStringSet("selected_apps", setOf("Uber", "99")) ?: setOf("Uber", "99")
    fun setSelectedApps(apps: Set<String>) = prefs.edit().putStringSet("selected_apps", apps).apply()

    // === Informações de Trabalho ===

    fun getDiasPorSemana(): Int = prefs.getInt("dias_semana", 6)
    fun setDiasPorSemana(dias: Int) = prefs.edit().putInt("dias_semana", dias).apply()

    fun getHorasPorDia(): Double = prefs.getFloat("horas_dia", 10f).toDouble()
    fun setHorasPorDia(h: Double) = prefs.edit().putFloat("horas_dia", h.toFloat()).apply()

    fun getKmPorDia(): Double = prefs.getFloat("km_dia", 200f).toDouble()
    fun setKmPorDia(km: Double) = prefs.edit().putFloat("km_dia", km.toFloat()).apply()

    fun getMetaSemanal(): Double = prefs.getFloat("meta_semanal", 3000f).toDouble()
    fun setMetaSemanal(meta: Double) = prefs.edit().putFloat("meta_semanal", meta.toFloat()).apply()

    // === Situação do veículo ===

    fun getVehicleType(): String = prefs.getString("vehicle_type", "Carro") ?: "Carro"
    fun setVehicleType(t: String) = prefs.edit().putString("vehicle_type", t).apply()

    fun getVehicleSituation(): String = prefs.getString("vehicle_situation", "Financiado") ?: "Financiado"
    fun setVehicleSituation(s: String) = prefs.edit().putString("vehicle_situation", s).apply()

    fun getVehiclePlate(): String = prefs.getString("vehicle_plate", "") ?: ""
    fun setVehiclePlate(p: String) = prefs.edit().putString("vehicle_plate", p).apply()

    // === Categorias ===

    fun getSelectedCategories(): Set<String> =
        prefs.getStringSet("selected_categories", setOf("Uber X", "Comfort", "99 Pop")) ?: setOf("Uber X", "Comfort", "99 Pop")
    fun setSelectedCategories(cats: Set<String>) = prefs.edit().putStringSet("selected_categories", cats).apply()

    // === Custos Mensais/Anuais ===

    fun getFinanciamentoMensal(): Double = prefs.getFloat("financiamento_mensal", 0f).toDouble()
    fun setFinanciamentoMensal(v: Double) = prefs.edit().putFloat("financiamento_mensal", v.toFloat()).apply()

    fun getManutencaoMensal(): Double = prefs.getFloat("manutencao_mensal", 0f).toDouble()
    fun setManutencaoMensal(v: Double) = prefs.edit().putFloat("manutencao_mensal", v.toFloat()).apply()

    fun getSeguroMensal(): Double = prefs.getFloat("seguro_mensal", 0f).toDouble()
    fun setSeguroMensal(v: Double) = prefs.edit().putFloat("seguro_mensal", v.toFloat()).apply()

    fun getOutrosCustosMensais(): Double = prefs.getFloat("outros_custos_mensais", 0f).toDouble()
    fun setOutrosCustosMensais(v: Double) = prefs.edit().putFloat("outros_custos_mensais", v.toFloat()).apply()

    fun getImpostosAnuais(): Double = prefs.getFloat("impostos_anuais", 0f).toDouble()
    fun setImpostosAnuais(v: Double) = prefs.edit().putFloat("impostos_anuais", v.toFloat()).apply()

    // === Filtros do semáforo ===

    fun getMinGanhoKm(): Double = prefs.getFloat("min_ganho_km", 2.35f).toDouble()
    fun setMinGanhoKm(v: Double) = prefs.edit().putFloat("min_ganho_km", v.toFloat()).apply()

    fun getRuimGanhoKm(): Double = prefs.getFloat("ruim_ganho_km", 1.90f).toDouble()
    fun setRuimGanhoKm(v: Double) = prefs.edit().putFloat("ruim_ganho_km", v.toFloat()).apply()

    fun getMinGanhoHora(): Double = prefs.getFloat("min_ganho_hora", 40f).toDouble()
    fun setMinGanhoHora(v: Double) = prefs.edit().putFloat("min_ganho_hora", v.toFloat()).apply()

    fun getRuimGanhoHora(): Double = prefs.getFloat("ruim_ganho_hora", 32f).toDouble()
    fun setRuimGanhoHora(v: Double) = prefs.edit().putFloat("ruim_ganho_hora", v.toFloat()).apply()

    fun getMinNota(): Double = prefs.getFloat("min_nota_pass", 4.90f).toDouble()
    fun setMinNota(v: Double) = prefs.edit().putFloat("min_nota_pass", v.toFloat()).apply()

    fun getRuimNota(): Double = prefs.getFloat("ruim_nota_pass", 4.70f).toDouble()
    fun setRuimNota(v: Double) = prefs.edit().putFloat("ruim_nota_pass", v.toFloat()).apply()

    // === Combustível / Elétrico ===

    fun getVehicleProfile(): VehicleProfile {
        val fuelTypeName = prefs.getString("fuel_type", FuelType.GASOLINE.name) ?: FuelType.GASOLINE.name
        val fuelType = try { FuelType.valueOf(fuelTypeName) } catch (_: Exception) { FuelType.GASOLINE }
        return VehicleProfile(
            name = prefs.getString("vehicle_name", "Meu Veículo") ?: "Meu Veículo",
            fuelType = fuelType,
            fuelPrice = prefs.getFloat("fuel_price", if (fuelType == FuelType.ELECTRIC) 1.20f else 5.85f).toDouble(),
            fuelConsumption = prefs.getFloat("fuel_consumption", 11.5f).toDouble(),
            kwhPer100Km = prefs.getFloat("kwh_per_100km", 13.0f).toDouble(),
            maintenanceCostPerKm = prefs.getFloat("maint_per_km", if (fuelType == FuelType.ELECTRIC) 0.08f else 0.20f).toDouble(),
            depreciationCostPerKm = prefs.getFloat("deprec_per_km", 0.15f).toDouble(),
            dailyFixedCost = prefs.getFloat("daily_fixed", 25.0f).toDouble(),
            estimatedDailyKm = prefs.getFloat("daily_km", 200.0f).toDouble()
        )
    }

    fun saveVehicleProfile(profile: VehicleProfile) {
        prefs.edit()
            .putString("vehicle_name", profile.name)
            .putString("fuel_type", profile.fuelType.name)
            .putFloat("fuel_price", profile.fuelPrice.toFloat())
            .putFloat("fuel_consumption", profile.fuelConsumption.toFloat())
            .putFloat("kwh_per_100km", profile.kwhPer100Km.toFloat())
            .putFloat("maint_per_km", profile.maintenanceCostPerKm.toFloat())
            .putFloat("deprec_per_km", profile.depreciationCostPerKm.toFloat())
            .putFloat("daily_fixed", profile.dailyFixedCost.toFloat())
            .putFloat("daily_km", profile.estimatedDailyKm.toFloat())
            .apply()
    }

    // === Autonomia por carga (EV) ===
    fun getAutonomiaCarga(): Double = prefs.getFloat("autonomia_carga", 410f).toDouble()
    fun setAutonomiaCarga(v: Double) = prefs.edit().putFloat("autonomia_carga", v.toFloat()).apply()

    fun getCapacidadeBateria(): Double = prefs.getFloat("capacidade_bateria", 60f).toDouble()
    fun setCapacidadeBateria(v: Double) = prefs.edit().putFloat("capacidade_bateria", v.toFloat()).apply()

    // === Metas do Motorista ===

    fun getDriverPreferences(): DriverPreferences {
        return DriverPreferences(
            minGrossPerKm = prefs.getFloat("min_gross_km", 2.00f).toDouble(),
            minNetPerKm = prefs.getFloat("min_net_km", 1.20f).toDouble(),
            minNetHourlyRate = prefs.getFloat("min_net_hour", 35.00f).toDouble(),
            maxPickupDistanceKm = prefs.getFloat("max_pickup_km", 3.5f).toDouble(),
            minPassengerRating = prefs.getFloat("min_rating", 4.75f).toDouble()
        )
    }

    fun saveDriverPreferences(preferences: DriverPreferences) {
        prefs.edit()
            .putFloat("min_gross_km", preferences.minGrossPerKm.toFloat())
            .putFloat("min_net_km", preferences.minNetPerKm.toFloat())
            .putFloat("min_net_hour", preferences.minNetHourlyRate.toFloat())
            .putFloat("max_pickup_km", preferences.maxPickupDistanceKm.toFloat())
            .putFloat("min_rating", preferences.minPassengerRating.toFloat())
            .apply()
    }

    // === Ganho acumulado (simulado para tela de detalhes) ===
    fun getGanhoSemanal(): Double = prefs.getFloat("ganho_semanal", 3322f).toDouble()
    fun setGanhoSemanal(v: Double) = prefs.edit().putFloat("ganho_semanal", v.toFloat()).apply()
}
