package com.vislupus.stepanalyzer.config

import android.hardware.Sensor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vislupus.stepanalyzer.core.config.CoreDefaults
import com.vislupus.stepanalyzer.core.model.Sex

/**
 * THE control panel of the app. Every value that shapes how the application looks or behaves
 * is declared here (or in [Palette] for colours and in core/config/CoreDefaults.kt for the
 * algorithm maths). Texts live in res/values/strings.xml (+ values-bg), icons in res/drawable.
 *
 * Values marked "user setting" are only the initial defaults - the user can change them in
 * the Settings screen and the choice is stored in the SQLite database.
 */
object AppConfig {

    /** Files and storage. Everything stays inside the app sandbox (Context.filesDir). */
    object Storage {
        /** SQLite database file name inside filesDir. */
        const val DATABASE_FILE = "stepanalyzer.db"
        /** Sub-directory of filesDir holding one folder per recording session. */
        const val SESSIONS_DIR = "sessions"
        /** Raw sensor samples of a session (long CSV format: timestamp_ms,sensor,x,y,z). */
        const val CSV_FILE = "sensors.csv"
        /** GPS track of a session. */
        const val GPX_FILE = "track.gpx"
        /** Temporary files prepared for sharing. */
        const val EXPORT_DIR = "export"
        /** Pattern for the automatic name of a new recording (java.time format). */
        const val SESSION_NAME_PATTERN = "yyyy-MM-dd HH:mm"
        /** Pattern used to display dates in lists. */
        const val DATE_PATTERN = "d MMM yyyy, HH:mm"
        /** Creator string written into exported GPX files. */
        const val GPX_CREATOR = "Step Analyzer"
    }

    /** Initial values of the user settings (changeable in the Settings screen). */
    object Defaults {
        val THEME = ThemeMode.SYSTEM
        val ACCENT = Palette.accents.first().id
        val FONT_SCALE = FontScale.DEFAULT
        val LANGUAGE = AppLanguage.ENGLISH
        const val HEIGHT_CM = 175
        val SEX = Sex.UNKNOWN
    }

    /** Sensor + GPS recording. */
    object Recording {
        /** Sampling period requested from SensorManager, microseconds (5 000 = 200 Hz). User setting. */
        const val SENSOR_PERIOD_US = 5_000
        /** Sample-rate choices offered in Settings (Hz). */
        val SENSOR_RATE_CHOICES_HZ = listOf(50, 100, 200)
        /** Which sensors are recorded. The CSV name is what the analyser expects. */
        val SENSORS: List<Pair<Int, String>> = listOf(
            Sensor.TYPE_ACCELEROMETER to "accel",
            Sensor.TYPE_LINEAR_ACCELERATION to "uaccel",
            Sensor.TYPE_GYROSCOPE to "gyro",
            Sensor.TYPE_MAGNETIC_FIELD to "mag",
        )
        /** Record a GPS track alongside the sensors. User setting. */
        const val RECORD_GPS = true
        /** Minimum time between GPS fixes. User setting. */
        const val GPS_INTERVAL_MS = 1_000L
        val GPS_INTERVAL_CHOICES_MS = listOf(1_000L, 2_000L, 5_000L)
        /** Minimum distance between GPS fixes (0 = time based only). */
        const val GPS_MIN_DISTANCE_M = 0f
        /** Also read the phone's own step counter / detector as a reference. User setting. */
        const val USE_HARDWARE_STEP_SENSORS = true
        /** Keep the screen on while the Record screen is visible and recording. User setting. */
        const val KEEP_SCREEN_ON = true
        /** How often the recording service publishes live state to the UI. */
        const val UI_UPDATE_MS = 100L
        /** Seconds of acceleration shown in the live chart. */
        const val LIVE_CHART_SECONDS = 6
        /** Buffer size of the CSV writer (bytes). */
        const val CSV_BUFFER_BYTES = 1 shl 16
        /** Decimal digits written to the CSV for each axis. */
        const val CSV_DECIMALS = 5
        const val NOTIFICATION_CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 1001

        /** Simple real-time step detector used only for the live counter while recording. */
        object Live {
            /** High-pass (DC removal) time constant in seconds. */
            const val HIGHPASS_TAU_S = 1.0
            /** Low-pass smoothing time constant in seconds. */
            const val SMOOTH_TAU_S = 0.05
            /** Step is counted when the smoothed signal swings above +T after going below -T. */
            const val THRESHOLD_MPS2 = 0.6
            const val MIN_INTERVAL_MS = 280L
        }
    }

    /** Analysis stage defaults (the algorithm maths lives in CoreDefaults). */
    object Analysis {
        const val FS_TARGET_HZ = CoreDefaults.Preprocess.FS_TARGET_HZ
        const val BAND_LOW_HZ = CoreDefaults.Preprocess.BAND_LOW_HZ
        const val BAND_HIGH_HZ = CoreDefaults.Preprocess.BAND_HIGH_HZ
        const val MAX_GPS_SPEED_MPS = CoreDefaults.Gps.MAX_SPEED_MPS
        /** Slider ranges for the band-pass edges. */
        const val BAND_LOW_MIN = 0.1
        const val BAND_LOW_MAX = 2.0
        const val BAND_HIGH_MIN = 2.5
        const val BAND_HIGH_MAX = 10.0
        val FS_CHOICES_HZ = listOf(50.0, 100.0, 200.0)
        /** Max points drawn in a chart (min/max bucket downsampling keeps the peaks). */
        const val CHART_MAX_POINTS = 1500
        /** Delay after the last slider movement before the algorithms re-run. */
        const val PARAM_DEBOUNCE_MS = 120L
        /** Store every analysis run automatically in the database. */
        const val AUTO_SAVE_RUNS = true
        /** Default visible window of the signal chart in seconds. */
        const val DEFAULT_WINDOW_S = 30.0
    }

    /** Layout and sizing. Designed for 1080 x 2340 px @ ~395 dpi (about 393 x 851 dp). */
    object Ui {
        val screenPadding = 16.dp
        val cardCorner = 20.dp
        val cardSpacing = 12.dp
        val cardPadding = 16.dp
        val smallCorner = 12.dp
        val iconSize = 22.dp
        val signalChartHeight = 220.dp
        val cadenceChartHeight = 140.dp
        val liveChartHeight = 150.dp
        val mapHeight = 220.dp
        val compareBarHeight = 22.dp
        val confidenceBarHeight = 6.dp
        val bigNumberSize = 64.sp
        val recordButtonSize = 96.dp
        /** Duration of UI transitions in milliseconds. */
        const val ANIMATION_MS = 220
        /** Show algorithms that cannot run on a recording (e.g. no gyroscope) greyed out. */
        const val SHOW_UNAVAILABLE_ALGORITHMS = true
        /** Line widths in the charts (dp). */
        val chartLineWidth = 1.6.dp
        val chartThinLineWidth = 1.0.dp
        val chartMarkerRadius = 3.dp
    }

    /** Feature switches. */
    object Features {
        const val ENABLE_IMPORT = true
        const val ENABLE_SHARE = true
        const val ENABLE_GPS = true
        const val STORE_GPS_POINTS_IN_DB = true
        const val STORE_STEP_INDICES_IN_DB = true
        const val SHOW_ALGORITHM_INFO = true
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Font size multiplier applied on top of the system font scale. */
enum class FontScale(val factor: Float) { SMALL(0.88f), DEFAULT(1.0f), LARGE(1.14f), XLARGE(1.3f) }

/** In-app language. SYSTEM follows the phone, otherwise a fixed BCP-47 tag. */
enum class AppLanguage(val tag: String) { SYSTEM(""), ENGLISH("en"), BULGARIAN("bg") }
