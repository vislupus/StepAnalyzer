package com.vislupus.stepanalyzer.data.repo

import com.vislupus.stepanalyzer.config.AppLanguage
import com.vislupus.stepanalyzer.config.FontScale
import com.vislupus.stepanalyzer.config.ThemeMode
import com.vislupus.stepanalyzer.core.model.AccelUnit
import com.vislupus.stepanalyzer.core.model.GyroUnit
import com.vislupus.stepanalyzer.core.model.Sex
import com.vislupus.stepanalyzer.data.UserSettings
import com.vislupus.stepanalyzer.data.db.AppDatabase
import com.vislupus.stepanalyzer.util.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Loads the user settings from the `settings` table once, keeps them in a StateFlow for the
 * UI and writes every change back immediately.
 */
class SettingsRepository(private val db: AppDatabase) {

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<UserSettings> get() = _settings
    val current: UserSettings get() = _settings.value

    private inline fun <reified E : Enum<E>> enumOf(value: String?, default: E): E =
        value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    private fun load(): UserSettings {
        val m = db.allSettings()
        val d = UserSettings()
        return UserSettings(
            themeMode = enumOf(m[K_THEME], d.themeMode),
            accentId = m[K_ACCENT] ?: d.accentId,
            fontScale = enumOf(m[K_FONT_SCALE], d.fontScale),
            language = enumOf(m[K_LANGUAGE], d.language),
            heightCm = m[K_HEIGHT_CM]?.toIntOrNull() ?: d.heightCm,
            sex = enumOf(m[K_SEX], d.sex),
            fsTargetHz = m[K_FS_TARGET]?.toDoubleOrNull() ?: d.fsTargetHz,
            bandLowHz = m[K_BAND_LOW]?.toDoubleOrNull() ?: d.bandLowHz,
            bandHighHz = m[K_BAND_HIGH]?.toDoubleOrNull() ?: d.bandHighHz,
            accelUnit = enumOf(m[K_ACCEL_UNIT], d.accelUnit),
            gyroUnit = enumOf(m[K_GYRO_UNIT], d.gyroUnit),
            maxGpsSpeedMps = m[K_MAX_GPS_SPEED]?.toDoubleOrNull() ?: d.maxGpsSpeedMps,
            algorithmParams = Json.paramsFromJson(m[K_ALGO_PARAMS]),
            disabledAlgorithms = Json.stringSetFromJson(m[K_DISABLED_ALGOS]),
            sensorPeriodUs = m[K_SENSOR_PERIOD]?.toIntOrNull() ?: d.sensorPeriodUs,
            recordGps = m[K_RECORD_GPS]?.toBooleanStrictOrNull() ?: d.recordGps,
            gpsIntervalMs = m[K_GPS_INTERVAL]?.toLongOrNull() ?: d.gpsIntervalMs,
            useHardwareStepSensors = m[K_HW_STEPS]?.toBooleanStrictOrNull() ?: d.useHardwareStepSensors,
            keepScreenOn = m[K_KEEP_SCREEN_ON]?.toBooleanStrictOrNull() ?: d.keepScreenOn,
        )
    }

    private fun UserSettings.toMap(): Map<String, String> = mapOf(
        K_THEME to themeMode.name,
        K_ACCENT to accentId,
        K_FONT_SCALE to fontScale.name,
        K_LANGUAGE to language.name,
        K_HEIGHT_CM to heightCm.toString(),
        K_SEX to sex.name,
        K_FS_TARGET to fsTargetHz.toString(),
        K_BAND_LOW to bandLowHz.toString(),
        K_BAND_HIGH to bandHighHz.toString(),
        K_ACCEL_UNIT to accelUnit.name,
        K_GYRO_UNIT to gyroUnit.name,
        K_MAX_GPS_SPEED to maxGpsSpeedMps.toString(),
        K_ALGO_PARAMS to Json.paramsToJson(algorithmParams),
        K_DISABLED_ALGOS to Json.stringSetToJson(disabledAlgorithms),
        K_SENSOR_PERIOD to sensorPeriodUs.toString(),
        K_RECORD_GPS to recordGps.toString(),
        K_GPS_INTERVAL to gpsIntervalMs.toString(),
        K_HW_STEPS to useHardwareStepSensors.toString(),
        K_KEEP_SCREEN_ON to keepScreenOn.toString(),
    )

    /** Apply a change and persist it. Safe to call from any thread. */
    @Synchronized
    fun update(transform: (UserSettings) -> UserSettings) {
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        _settings.value = updated
        db.putSettings(updated.toMap())
    }

    fun setTheme(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setAccent(id: String) = update { it.copy(accentId = id) }
    fun setFontScale(scale: FontScale) = update { it.copy(fontScale = scale) }
    fun setLanguage(language: AppLanguage) = update { it.copy(language = language) }
    fun setHeight(cm: Int) = update { it.copy(heightCm = cm) }
    fun setSex(sex: Sex) = update { it.copy(sex = sex) }
    fun setAccelUnit(unit: AccelUnit) = update { it.copy(accelUnit = unit) }
    fun setGyroUnit(unit: GyroUnit) = update { it.copy(gyroUnit = unit) }

    /** Store the given algorithm parameters as the new defaults for future analyses. */
    fun saveAlgorithmParams(algorithmId: String, params: Map<String, Double>) = update {
        it.copy(algorithmParams = it.algorithmParams + (algorithmId to params))
    }

    fun resetAlgorithmParams(algorithmId: String) = update {
        it.copy(algorithmParams = it.algorithmParams - algorithmId)
    }

    fun setAlgorithmEnabled(algorithmId: String, enabled: Boolean) = update {
        it.copy(disabledAlgorithms = if (enabled) it.disabledAlgorithms - algorithmId else it.disabledAlgorithms + algorithmId)
    }

    /** Reload from the database (after clearAll). */
    @Synchronized
    fun reload() {
        _settings.value = load()
    }

    companion object {
        const val K_THEME = "theme_mode"
        const val K_ACCENT = "accent"
        const val K_FONT_SCALE = "font_scale"
        const val K_LANGUAGE = "language"
        const val K_HEIGHT_CM = "height_cm"
        const val K_SEX = "sex"
        const val K_FS_TARGET = "fs_target_hz"
        const val K_BAND_LOW = "band_low_hz"
        const val K_BAND_HIGH = "band_high_hz"
        const val K_ACCEL_UNIT = "accel_unit"
        const val K_GYRO_UNIT = "gyro_unit"
        const val K_MAX_GPS_SPEED = "max_gps_speed_mps"
        const val K_ALGO_PARAMS = "algorithm_params"
        const val K_DISABLED_ALGOS = "disabled_algorithms"
        const val K_SENSOR_PERIOD = "sensor_period_us"
        const val K_RECORD_GPS = "record_gps"
        const val K_GPS_INTERVAL = "gps_interval_ms"
        const val K_HW_STEPS = "use_hardware_step_sensors"
        const val K_KEEP_SCREEN_ON = "keep_screen_on"
    }
}
