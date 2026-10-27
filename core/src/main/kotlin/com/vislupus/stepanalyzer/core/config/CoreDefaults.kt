package com.vislupus.stepanalyzer.core.config

import com.vislupus.stepanalyzer.core.model.ParamSpec
import com.vislupus.stepanalyzer.core.model.ParamUnit

/**
 * Every numeric default of the analysis engine lives here, so tuning the algorithms never
 * requires touching the algorithm code itself. The Android app exposes most of these as
 * sliders; the values below are the initial positions and slider ranges.
 */
object CoreDefaults {

    /** Standard gravity used for g <-> m/s^2 conversion. */
    const val G = 9.80665

    /** Preprocessing. */
    object Preprocess {
        const val FS_TARGET_HZ = 100.0
        const val BAND_LOW_HZ = 0.5
        const val BAND_HIGH_HZ = 4.0
        const val FILTER_ORDER = 3
        /** |accel| median in this range means the file is in g, not m/s^2. */
        const val G_UNIT_MIN = 0.3
        const val G_UNIT_MAX = 3.0
        /** 99th percentile of |gyro| above this is treated as deg/s. */
        const val GYRO_DEGPS_THRESHOLD = 60.0
        /** Minimum valid samples a stream needs to be used at all. */
        const val MIN_SAMPLES = 4
    }

    /** Shared helpers used by several algorithms. */
    object Activity {
        /** Window length (s) for the "is the phone moving" gate. */
        const val WINDOW_S = 2.0
        /** Std of the filtered signal below this means no motion. */
        const val STD_FLOOR = 0.15
        /** Inter-step intervals outside this range are ignored when computing CV. */
        const val INTERVAL_MIN_S = 0.2
        const val INTERVAL_MAX_S = 2.5
    }

    /** Algorithm A: peak detection on the band-passed magnitude. */
    object Peak {
        val MIN_INTERVAL_S = ParamSpec("min_interval_s", 0.28, 0.15, 0.80, 0.01, ParamUnit.SECONDS)
        val PROMINENCE_FACTOR = ParamSpec("prominence_factor", 0.35, 0.05, 1.50, 0.05, ParamUnit.FACTOR)
        val PROMINENCE_FLOOR = ParamSpec("prominence_floor", 0.15, 0.0, 2.0, 0.05, ParamUnit.MPS2)
    }

    /** Algorithm B: adaptive peak-valley pairs. */
    object PeakValley {
        val MIN_INTERVAL_S = ParamSpec("min_interval_s", 0.28, 0.15, 0.80, 0.01, ParamUnit.SECONDS)
        val STD_FACTOR = ParamSpec("std_factor", 0.50, 0.05, 2.00, 0.05, ParamUnit.FACTOR)
        val THRESHOLD_FLOOR = ParamSpec("threshold_floor", 0.20, 0.0, 2.0, 0.05, ParamUnit.MPS2)
        val PAIR_WINDOW_S = ParamSpec("pair_window_s", 0.80, 0.2, 2.0, 0.05, ParamUnit.SECONDS)
        val LOCAL_WINDOW_S = ParamSpec("local_window_s", 2.5, 0.5, 10.0, 0.5, ParamUnit.SECONDS)
    }

    /** Algorithm C: zero crossing with peak validation. */
    object ZeroCrossing {
        val MIN_INTERVAL_S = ParamSpec("min_interval_s", 0.28, 0.15, 0.80, 0.01, ParamUnit.SECONDS)
        val THRESHOLD_FACTOR = ParamSpec("threshold_factor", 0.35, 0.05, 1.50, 0.05, ParamUnit.FACTOR)
        val THRESHOLD_FLOOR = ParamSpec("threshold_floor", 0.10, 0.0, 2.0, 0.05, ParamUnit.MPS2)
        val LOOKAHEAD_S = ParamSpec("lookahead_s", 0.60, 0.1, 1.5, 0.05, ParamUnit.SECONDS)
    }

    /** Algorithm D: windowed autocorrelation cadence. */
    object Autocorrelation {
        val WINDOW_S = ParamSpec("window_s", 6.0, 2.0, 20.0, 0.5, ParamUnit.SECONDS)
        val HOP_S = ParamSpec("hop_s", 3.0, 0.5, 10.0, 0.5, ParamUnit.SECONDS)
        val F_MIN = ParamSpec("f_min", 1.3, 0.5, 2.5, 0.05, ParamUnit.HERTZ)
        val F_MAX = ParamSpec("f_max", 4.0, 2.0, 6.0, 0.1, ParamUnit.HERTZ)
        val STD_FLOOR = ParamSpec("std_floor", 0.15, 0.0, 1.0, 0.01, ParamUnit.MPS2)
    }

    /** Algorithm E: windowed FFT cadence with stride guard. */
    object FftCadence {
        val WINDOW_S = ParamSpec("window_s", 6.0, 2.0, 20.0, 0.5, ParamUnit.SECONDS)
        val HOP_S = ParamSpec("hop_s", 3.0, 0.5, 10.0, 0.5, ParamUnit.SECONDS)
        val F_MIN = ParamSpec("f_min", 1.3, 0.5, 2.5, 0.05, ParamUnit.HERTZ)
        val F_MAX = ParamSpec("f_max", 4.0, 2.0, 6.0, 0.1, ParamUnit.HERTZ)
        val STD_FLOOR = ParamSpec("std_floor", 0.15, 0.0, 1.0, 0.01, ParamUnit.MPS2)
        val STRIDE_FLOOR = ParamSpec("stride_floor", 0.65, 0.3, 1.3, 0.05, ParamUnit.HERTZ)
        val STRIDE_RATIO = ParamSpec("stride_ratio", 1.6, 1.0, 4.0, 0.1, ParamUnit.FACTOR)
    }

    /** Algorithm F: dynamic (max/min midpoint) threshold crossing - the classic pedometer. */
    object DynamicThreshold {
        val SMOOTH_S = ParamSpec("smooth_s", 0.04, 0.0, 0.30, 0.01, ParamUnit.SECONDS)
        val THRESHOLD_WINDOW_S = ParamSpec("threshold_window_s", 0.5, 0.25, 4.0, 0.25, ParamUnit.SECONDS)
        val MIN_AMPLITUDE = ParamSpec("min_amplitude", 0.3, 0.0, 5.0, 0.1, ParamUnit.MPS2)
        val MIN_INTERVAL_S = ParamSpec("min_interval_s", 0.28, 0.15, 0.80, 0.01, ParamUnit.SECONDS)
    }

    /** Algorithm G: derivative -> square -> integrate (Pan-Tompkins style). */
    object PanTompkins {
        /** Lower edge of the algorithm's own band-pass (removes the stride component). */
        val BAND_LOW_HZ = ParamSpec("band_low_hz", 1.2, 0.3, 2.5, 0.1, ParamUnit.HERTZ)
        val INTEGRATION_WINDOW_S = ParamSpec("integration_window_s", 0.15, 0.05, 0.60, 0.01, ParamUnit.SECONDS)
        val MIN_INTERVAL_S = ParamSpec("min_interval_s", 0.28, 0.15, 0.80, 0.01, ParamUnit.SECONDS)
        val THRESHOLD_FRACTION = ParamSpec("threshold_fraction", 0.15, 0.05, 0.90, 0.05, ParamUnit.FACTOR)
        val LEARNING_RATE = ParamSpec("learning_rate", 0.125, 0.01, 0.50, 0.005, ParamUnit.FACTOR)
        /** 1 = keep only the rising slope of the derivative, 0 = use both slopes. */
        val RECTIFY = ParamSpec("rectify", 1.0, 0.0, 1.0, 1.0, ParamUnit.NONE)
    }

    /** Algorithm H: peaks on the band-passed gyroscope magnitude. */
    object GyroPeak {
        val MIN_INTERVAL_S = ParamSpec("min_interval_s", 0.28, 0.15, 0.80, 0.01, ParamUnit.SECONDS)
        val PROMINENCE_FACTOR = ParamSpec("prominence_factor", 0.70, 0.05, 2.00, 0.05, ParamUnit.FACTOR)
        val PROMINENCE_FLOOR = ParamSpec("prominence_floor", 0.05, 0.0, 1.0, 0.01, ParamUnit.RADPS)
    }

    /** Algorithm I: vertical acceleration via gravity projection. */
    object VerticalAccel {
        val GRAVITY_CUTOFF_HZ = ParamSpec("gravity_cutoff_hz", 0.3, 0.05, 1.5, 0.05, ParamUnit.HERTZ)
        val MIN_INTERVAL_S = ParamSpec("min_interval_s", 0.28, 0.15, 0.80, 0.01, ParamUnit.SECONDS)
        val PROMINENCE_FACTOR = ParamSpec("prominence_factor", 0.35, 0.05, 1.50, 0.05, ParamUnit.FACTOR)
        val PROMINENCE_FLOOR = ParamSpec("prominence_floor", 0.15, 0.0, 2.0, 0.05, ParamUnit.MPS2)
    }

    /** Algorithm J: Schmitt-trigger (hysteresis) state machine. */
    object Hysteresis {
        val THRESHOLD_FACTOR = ParamSpec("threshold_factor", 0.50, 0.05, 2.00, 0.05, ParamUnit.FACTOR)
        val THRESHOLD_FLOOR = ParamSpec("threshold_floor", 0.15, 0.0, 2.0, 0.05, ParamUnit.MPS2)
        val MIN_INTERVAL_S = ParamSpec("min_interval_s", 0.28, 0.15, 0.80, 0.01, ParamUnit.SECONDS)
        val LOCAL_WINDOW_S = ParamSpec("local_window_s", 2.5, 0.5, 10.0, 0.5, ParamUnit.SECONDS)
    }

    /** Step-length models. */
    object StepLength {
        const val K_WEINBERG = 0.51
        const val K_KIM = 0.50
        const val K_SCARLETT = 0.50
        const val HEIGHT_FACTOR_MALE = 0.415
        const val HEIGHT_FACTOR_FEMALE = 0.413
        const val HEIGHT_FACTOR_UNKNOWN = 0.414
        /** L = CADENCE_A * f_step + CADENCE_B */
        const val CADENCE_A = 0.35
        const val CADENCE_B = 0.10
        /** L = h * (HEIGHT_CADENCE_A * f_step + HEIGHT_CADENCE_B) */
        const val HEIGHT_CADENCE_A = 0.15
        const val HEIGHT_CADENCE_B = 0.15
        const val DEFAULT_HEIGHT_M = 1.70
        /** Duration (s) assumed for the last step segment. */
        const val LAST_SEGMENT_S = 0.55
    }

    /** Confidence scoring and recommendation. */
    object Confidence {
        const val PLAUSIBLE_MIN_M = 0.25
        const val PLAUSIBLE_MAX_M = 2.2
        const val IDEAL_MIN_M = 0.45
        const val IDEAL_MAX_M = 0.95
        // weights with GPX
        const val W_INTERVAL_GPX = 0.30
        const val W_PERIOD_GPX = 0.22
        const val W_GPX = 0.28
        const val W_AMP_GPX = 0.20
        // weights without GPX
        const val W_INTERVAL = 0.42
        const val W_PERIOD = 0.33
        const val W_AMP = 0.25
        /** Periodicity above this earns the "strong periodicity" note. */
        const val STRONG_PERIODICITY = 0.6
        /** A peak-based algorithm within this margin of the best beats a frequency one as reference. */
        const val REFERENCE_MARGIN = 0.1
    }

    /** GPX cleaning. */
    object Gps {
        const val MAX_SPEED_MPS = 8.0
        const val EARTH_RADIUS_M = 6371000.0
    }
}
