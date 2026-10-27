package com.vislupus.stepanalyzer.util

import android.content.Context
import androidx.annotation.StringRes
import com.vislupus.stepanalyzer.R
import com.vislupus.stepanalyzer.core.algorithms.AutocorrelationAlgorithm
import com.vislupus.stepanalyzer.core.algorithms.DynamicThresholdAlgorithm
import com.vislupus.stepanalyzer.core.algorithms.FftCadenceAlgorithm
import com.vislupus.stepanalyzer.core.algorithms.GyroPeakAlgorithm
import com.vislupus.stepanalyzer.core.algorithms.HysteresisAlgorithm
import com.vislupus.stepanalyzer.core.algorithms.PanTompkinsAlgorithm
import com.vislupus.stepanalyzer.core.algorithms.PeakAlgorithm
import com.vislupus.stepanalyzer.core.algorithms.PeakValleyAlgorithm
import com.vislupus.stepanalyzer.core.algorithms.VerticalAccelAlgorithm
import com.vislupus.stepanalyzer.core.algorithms.ZeroCrossingAlgorithm
import com.vislupus.stepanalyzer.core.model.AlgorithmFamily
import com.vislupus.stepanalyzer.core.model.Note
import com.vislupus.stepanalyzer.core.model.ParamUnit
import com.vislupus.stepanalyzer.core.model.Requirement
import com.vislupus.stepanalyzer.core.model.SensorKind
import com.vislupus.stepanalyzer.core.model.StepLengthFamily
import com.vislupus.stepanalyzer.core.steplength.StepLengthEstimator

/** Maps the language-neutral ids of the core module to localised string resources. */
object AlgorithmText {

    @StringRes
    fun name(id: String): Int = when (id) {
        PeakAlgorithm.ID -> R.string.algo_peak_magnitude_name
        PeakValleyAlgorithm.ID -> R.string.algo_adaptive_peak_valley_name
        ZeroCrossingAlgorithm.ID -> R.string.algo_zero_crossing_name
        AutocorrelationAlgorithm.ID -> R.string.algo_autocorrelation_name
        FftCadenceAlgorithm.ID -> R.string.algo_fft_cadence_name
        DynamicThresholdAlgorithm.ID -> R.string.algo_dynamic_threshold_name
        PanTompkinsAlgorithm.ID -> R.string.algo_pan_tompkins_name
        HysteresisAlgorithm.ID -> R.string.algo_hysteresis_name
        GyroPeakAlgorithm.ID -> R.string.algo_gyro_peak_name
        VerticalAccelAlgorithm.ID -> R.string.algo_vertical_accel_name
        else -> R.string.algo_unknown
    }

    @StringRes
    fun shortName(id: String): Int = when (id) {
        PeakAlgorithm.ID -> R.string.algo_peak_magnitude_short
        PeakValleyAlgorithm.ID -> R.string.algo_adaptive_peak_valley_short
        ZeroCrossingAlgorithm.ID -> R.string.algo_zero_crossing_short
        AutocorrelationAlgorithm.ID -> R.string.algo_autocorrelation_short
        FftCadenceAlgorithm.ID -> R.string.algo_fft_cadence_short
        DynamicThresholdAlgorithm.ID -> R.string.algo_dynamic_threshold_short
        PanTompkinsAlgorithm.ID -> R.string.algo_pan_tompkins_short
        HysteresisAlgorithm.ID -> R.string.algo_hysteresis_short
        GyroPeakAlgorithm.ID -> R.string.algo_gyro_peak_short
        VerticalAccelAlgorithm.ID -> R.string.algo_vertical_accel_short
        else -> R.string.algo_unknown
    }

    @StringRes
    fun description(id: String): Int = when (id) {
        PeakAlgorithm.ID -> R.string.algo_peak_magnitude_desc
        PeakValleyAlgorithm.ID -> R.string.algo_adaptive_peak_valley_desc
        ZeroCrossingAlgorithm.ID -> R.string.algo_zero_crossing_desc
        AutocorrelationAlgorithm.ID -> R.string.algo_autocorrelation_desc
        FftCadenceAlgorithm.ID -> R.string.algo_fft_cadence_desc
        DynamicThresholdAlgorithm.ID -> R.string.algo_dynamic_threshold_desc
        PanTompkinsAlgorithm.ID -> R.string.algo_pan_tompkins_desc
        HysteresisAlgorithm.ID -> R.string.algo_hysteresis_desc
        GyroPeakAlgorithm.ID -> R.string.algo_gyro_peak_desc
        VerticalAccelAlgorithm.ID -> R.string.algo_vertical_accel_desc
        else -> R.string.algo_unknown
    }

    @StringRes
    fun family(family: AlgorithmFamily): Int = when (family) {
        AlgorithmFamily.PEAK -> R.string.family_peak
        AlgorithmFamily.ZERO_CROSSING -> R.string.family_zero_crossing
        AlgorithmFamily.FREQUENCY -> R.string.family_frequency
        AlgorithmFamily.THRESHOLD -> R.string.family_threshold
    }

    @StringRes
    fun param(key: String): Int = when (key) {
        "min_interval_s" -> R.string.param_min_interval_s
        "prominence_factor" -> R.string.param_prominence_factor
        "prominence_floor" -> R.string.param_prominence_floor
        "std_factor" -> R.string.param_std_factor
        "threshold_floor" -> R.string.param_threshold_floor
        "pair_window_s" -> R.string.param_pair_window_s
        "local_window_s" -> R.string.param_local_window_s
        "threshold_factor" -> R.string.param_threshold_factor
        "lookahead_s" -> R.string.param_lookahead_s
        "window_s" -> R.string.param_window_s
        "hop_s" -> R.string.param_hop_s
        "f_min" -> R.string.param_f_min
        "f_max" -> R.string.param_f_max
        "std_floor" -> R.string.param_std_floor
        "stride_floor" -> R.string.param_stride_floor
        "stride_ratio" -> R.string.param_stride_ratio
        "smooth_s" -> R.string.param_smooth_s
        "threshold_window_s" -> R.string.param_threshold_window_s
        "min_amplitude" -> R.string.param_min_amplitude
        "band_low_hz" -> R.string.param_band_low_hz
        "integration_window_s" -> R.string.param_integration_window_s
        "threshold_fraction" -> R.string.param_threshold_fraction
        "learning_rate" -> R.string.param_learning_rate
        "rectify" -> R.string.param_rectify
        "gravity_cutoff_hz" -> R.string.param_gravity_cutoff_hz
        else -> R.string.param_unknown
    }

    fun unitSuffix(unit: ParamUnit): String = when (unit) {
        ParamUnit.SECONDS -> " s"
        ParamUnit.HERTZ -> " Hz"
        ParamUnit.FACTOR -> " ×"
        ParamUnit.MPS2 -> " m/s²"
        ParamUnit.RADPS -> " rad/s"
        ParamUnit.SAMPLES -> ""
        ParamUnit.NONE -> ""
    }

    @StringRes
    fun methodName(id: String): Int = when (id) {
        StepLengthEstimator.GPX_AVG -> R.string.method_gpx_avg_name
        StepLengthEstimator.WEINBERG -> R.string.method_weinberg_name
        StepLengthEstimator.KIM -> R.string.method_kim_name
        StepLengthEstimator.SCARLETT -> R.string.method_scarlett_name
        StepLengthEstimator.HEIGHT_FACTOR -> R.string.method_height_factor_name
        StepLengthEstimator.CADENCE_LINEAR -> R.string.method_cadence_linear_name
        StepLengthEstimator.HEIGHT_CADENCE -> R.string.method_height_cadence_name
        else -> R.string.algo_unknown
    }

    @StringRes
    fun methodDescription(id: String): Int = when (id) {
        StepLengthEstimator.GPX_AVG -> R.string.method_gpx_avg_desc
        StepLengthEstimator.WEINBERG -> R.string.method_weinberg_desc
        StepLengthEstimator.KIM -> R.string.method_kim_desc
        StepLengthEstimator.SCARLETT -> R.string.method_scarlett_desc
        StepLengthEstimator.HEIGHT_FACTOR -> R.string.method_height_factor_desc
        StepLengthEstimator.CADENCE_LINEAR -> R.string.method_cadence_linear_desc
        StepLengthEstimator.HEIGHT_CADENCE -> R.string.method_height_cadence_desc
        else -> R.string.algo_unknown
    }

    @StringRes
    fun methodFamily(family: StepLengthFamily): Int = when (family) {
        StepLengthFamily.GPS -> R.string.method_family_gps
        StepLengthFamily.AMPLITUDE -> R.string.method_family_amplitude
        StepLengthFamily.BIOMECHANICAL -> R.string.method_family_biomechanical
    }

    @StringRes
    fun requirement(req: Requirement): Int = when (req) {
        Requirement.GPX -> R.string.req_gpx
        Requirement.ACCELEROMETER -> R.string.req_accelerometer
        Requirement.ACCELEROMETER_AND_CALIBRATION -> R.string.req_accelerometer_calibration
        Requirement.HEIGHT -> R.string.req_height
        Requirement.HEIGHT_DEFAULT -> R.string.req_height_default
        Requirement.NONE -> R.string.req_none
    }

    @StringRes
    fun sensor(kind: SensorKind): Int = when (kind) {
        SensorKind.ACCEL -> R.string.sensor_accel
        SensorKind.UACCEL -> R.string.sensor_uaccel
        SensorKind.GYRO -> R.string.sensor_gyro
        SensorKind.MAG -> R.string.sensor_mag
    }

    fun note(context: Context, note: Note): String = when (note) {
        is Note.StrongPeriodicity -> context.getString(R.string.note_strong_periodicity)
        is Note.IntervalCv -> context.getString(R.string.note_interval_cv, Formatters.number(note.cv, 2))
        is Note.GpxStepLength -> context.getString(R.string.note_gpx_step_length, Formatters.number(note.metres, 2))
        is Note.OutOfPlausibleRange -> context.getString(R.string.note_out_of_range)
        is Note.FrequencyMethod -> context.getString(R.string.note_frequency_method)
        is Note.RequiresSensor -> context.getString(R.string.note_requires_sensor, context.getString(sensor(note.sensor)))
        is Note.StrideLocked -> context.getString(R.string.note_stride_guard, note.windows)
    }
}
