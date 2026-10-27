package com.vislupus.stepanalyzer.data

import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.config.AppLanguage
import com.vislupus.stepanalyzer.config.FontScale
import com.vislupus.stepanalyzer.config.ThemeMode
import com.vislupus.stepanalyzer.core.model.AccelUnit
import com.vislupus.stepanalyzer.core.model.GyroUnit
import com.vislupus.stepanalyzer.core.model.PreprocessOptions
import com.vislupus.stepanalyzer.core.model.Sex

/** All user-changeable settings. Persisted as key/value rows in the SQLite `settings` table. */
data class UserSettings(
    // appearance
    val themeMode: ThemeMode = AppConfig.Defaults.THEME,
    val accentId: String = AppConfig.Defaults.ACCENT,
    val fontScale: FontScale = AppConfig.Defaults.FONT_SCALE,
    val language: AppLanguage = AppConfig.Defaults.LANGUAGE,
    // profile
    val heightCm: Int = AppConfig.Defaults.HEIGHT_CM,
    val sex: Sex = AppConfig.Defaults.SEX,
    // analysis defaults
    val fsTargetHz: Double = AppConfig.Analysis.FS_TARGET_HZ,
    val bandLowHz: Double = AppConfig.Analysis.BAND_LOW_HZ,
    val bandHighHz: Double = AppConfig.Analysis.BAND_HIGH_HZ,
    val accelUnit: AccelUnit = AccelUnit.AUTO,
    val gyroUnit: GyroUnit = GyroUnit.AUTO,
    val maxGpsSpeedMps: Double = AppConfig.Analysis.MAX_GPS_SPEED_MPS,
    /** Per-algorithm parameter overrides saved as the new defaults. */
    val algorithmParams: Map<String, Map<String, Double>> = emptyMap(),
    /** Algorithms hidden from the analysis. */
    val disabledAlgorithms: Set<String> = emptySet(),
    // recording
    val sensorPeriodUs: Int = AppConfig.Recording.SENSOR_PERIOD_US,
    val recordGps: Boolean = AppConfig.Recording.RECORD_GPS,
    val gpsIntervalMs: Long = AppConfig.Recording.GPS_INTERVAL_MS,
    val useHardwareStepSensors: Boolean = AppConfig.Recording.USE_HARDWARE_STEP_SENSORS,
    val keepScreenOn: Boolean = AppConfig.Recording.KEEP_SCREEN_ON,
) {
    val heightM: Double get() = heightCm / 100.0

    fun preprocessOptions() = PreprocessOptions(
        fsTarget = fsTargetHz,
        accelUnit = accelUnit,
        gyroUnit = gyroUnit,
        bandLowHz = bandLowHz,
        bandHighHz = bandHighHz,
    )
}
