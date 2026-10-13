package org.opendriver.data

import android.content.Context
import android.content.SharedPreferences
import org.opendriver.core.model.DriverPreferences
import org.opendriver.core.model.FuelType
import org.opendriver.core.model.VehicleProfile

/**
 * Repositório simples para carregar e salvar as preferências do motorista
 * e os custos do veículo localmente usando SharedPreferences.
 */
class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("opendriver_prefs", Context.MODE_PRIVATE)

    fun getVehicleProfile(): VehicleProfile {
        val fuelTypeName = prefs.getString("fuel_type", FuelType.GASOLINE.name) ?: FuelType.GASOLINE.name
        val fuelType = try { FuelType.valueOf(fuelTypeName) } catch (_: Exception) { FuelType.GASOLINE }

        return VehicleProfile(
            name = prefs.getString("vehicle_name", "Meu Veículo") ?: "Meu Veículo",
            fuelType = fuelType,
            fuelPrice = prefs.getFloat("fuel_price", 5.85f).toDouble(),
            fuelConsumption = prefs.getFloat("fuel_consumption", 11.5f).toDouble(),
            maintenanceCostPerKm = prefs.getFloat("maint_per_km", 0.20f).toDouble(),
            depreciationCostPerKm = prefs.getFloat("deprec_per_km", 0.15f).toDouble(),
            dailyFixedCost = prefs.getFloat("daily_fixed", 25.0f).toDouble(),
            estimatedDailyKm = prefs.getFloat("daily_km", 120.0f).toDouble()
        )
    }

    fun saveVehicleProfile(profile: VehicleProfile) {
        prefs.edit()
            .putString("vehicle_name", profile.name)
            .putString("fuel_type", profile.fuelType.name)
            .putFloat("fuel_price", profile.fuelPrice.toFloat())
            .putFloat("fuel_consumption", profile.fuelConsumption.toFloat())
            .putFloat("maint_per_km", profile.maintenanceCostPerKm.toFloat())
            .putFloat("deprec_per_km", profile.depreciationCostPerKm.toFloat())
            .putFloat("daily_fixed", profile.dailyFixedCost.toFloat())
            .putFloat("daily_km", profile.estimatedDailyKm.toFloat())
            .apply()
    }

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
}
