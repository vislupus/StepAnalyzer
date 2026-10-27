package com.vislupus.stepanalyzer.core

import com.vislupus.stepanalyzer.core.algorithms.Algorithms
import com.vislupus.stepanalyzer.core.analysis.AnalysisParams
import com.vislupus.stepanalyzer.core.analysis.Analyzer
import com.vislupus.stepanalyzer.core.analysis.Preprocessor
import com.vislupus.stepanalyzer.core.csv.CsvParser
import com.vislupus.stepanalyzer.core.gpx.Gpx
import com.vislupus.stepanalyzer.core.model.PreprocessOptions
import com.vislupus.stepanalyzer.core.model.SensorKind
import java.io.StringReader
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** End-to-end checks on a synthetic walk: 1.8 Hz steps for 60 s, phone with gravity on z. */
class PipelineTest {

    private fun syntheticCsv(stepHz: Double = 1.8, seconds: Double = 60.0, fs: Double = 200.0, withGyro: Boolean = true): String {
        val sb = StringBuilder("timestamp_ms,sensor,x,y,z\n")
        val n = (seconds * fs).toInt()
        val t0 = 1_782_372_767_801L
        var seed = 12345L
        fun noise(): Double { seed = (seed * 6364136223846793005L + 1442695040888963407L); return ((seed ushr 11) % 10000) / 10000.0 - 0.5 }
        for (i in 0 until n) {
            val t = i / fs
            val ms = t0 + (t * 1000).toLong()
            // vertical bounce at the step frequency plus a weaker stride (half frequency) component
            val vertical = 9.80665 + 2.0 * sin(2 * PI * stepHz * t) + 0.6 * sin(2 * PI * stepHz / 2 * t) + 0.3 * noise()
            val x = 0.4 * sin(2 * PI * stepHz / 2 * t) + 0.2 * noise()
            val y = 0.3 * cos(2 * PI * stepHz * t) + 0.2 * noise()
            sb.append(ms).append(",accel,").append(fmt(x)).append(',').append(fmt(y)).append(',').append(fmt(vertical)).append('\n')
            if (withGyro) {
                // slow baseline rotation plus a step-rate wobble; the offset keeps the magnitude
                // from rectifying the sine (which would double the apparent frequency)
                val g = 1.0 + 0.5 * sin(2 * PI * stepHz * t + 0.4) + 0.05 * noise()
                sb.append(ms).append(",gyro,").append(fmt(g)).append(',').append(fmt(0.1 * noise())).append(',').append(fmt(0.2 * g)).append('\n')
            }
        }
        return sb.toString()
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.5f", v)

    @Test
    fun parsesLongFormatAndDetectsRates() {
        val raw = CsvParser.parse(StringReader(syntheticCsv(seconds = 5.0)))
        assertTrue(raw.streams.containsKey(SensorKind.ACCEL))
        assertTrue(raw.streams.containsKey(SensorKind.GYRO))
        val pre = Preprocessor.preprocess(raw, PreprocessOptions())
        assertEquals(100.0, pre.fs)
        assertTrue(abs(pre.nativeRates.getValue(SensorKind.ACCEL) - 200.0) < 5.0, "native rate ${pre.nativeRates}")
        assertTrue(abs(pre.durationS - 5.0) < 0.05)
    }

    @Test
    fun parsesWideFormat() {
        val sb = StringBuilder("timestamp,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z\n")
        for (i in 0 until 500) {
            val t = i / 100.0
            sb.append(fmt(t)).append(",0.1,0.2,").append(fmt(9.8 + sin(2 * PI * 2 * t))).append(",0,0,0\n")
        }
        val raw = CsvParser.parse(StringReader(sb.toString()))
        assertEquals(500, raw.nRows)
        assertEquals(500, raw.streams.getValue(SensorKind.ACCEL).size)
    }

    @Test
    fun detectsGUnits() {
        val sb = StringBuilder("timestamp_ms,sensor,x,y,z\n")
        for (i in 0 until 1000) {
            val t = i / 100.0
            sb.append(1000 + i * 10).append(",accel,0.01,0.02,").append(fmt(1.0 + 0.2 * sin(2 * PI * 2 * t))).append('\n')
        }
        val pre = Preprocessor.preprocess(CsvParser.parse(StringReader(sb.toString())))
        assertEquals(com.vislupus.stepanalyzer.core.model.AccelUnit.G, pre.accelUnitDetected)
        assertTrue(abs(com.vislupus.stepanalyzer.core.signal.Stats.median(pre.accMagRaw) - 9.8) < 0.3)
    }

    @Test
    fun allAlgorithmsCountCloseToTruthOnSyntheticWalk() {
        val raw = CsvParser.parse(StringReader(syntheticCsv()))
        val pre = Preprocessor.preprocess(raw)
        val result = Analyzer.analyze(pre, null, AnalysisParams(heightM = 1.78))
        val expected = 1.8 * 60.0 // 108 steps
        for (a in result.algorithms) {
            assertTrue(a.available, "${a.id} should be available")
            val err = abs(a.stepCount - expected) / expected
            assertTrue(err < 0.08, "${a.id}: ${a.stepCount} steps vs expected $expected")
            assertTrue(a.confidence in 0.0..1.0)
        }
        assertEquals(Algorithms.all.size, result.algorithms.size)
        assertTrue(result.stepLength.methods.size >= 5)
        assertNotNull(result.stepLength.recommendedM)
        assertTrue(result.summary.globalPeriodicity > 0.5)
    }

    @Test
    fun gyroAlgorithmIsUnavailableWithoutGyro() {
        val raw = CsvParser.parse(StringReader(syntheticCsv(seconds = 20.0, withGyro = false)))
        val pre = Preprocessor.preprocess(raw)
        val result = Analyzer.analyze(pre, null)
        val gyro = result.algorithm("gyro_peak")!!
        assertTrue(!gyro.available)
        assertTrue(result.algorithms.count { it.available } == Algorithms.all.size - 1)
    }

    @Test
    fun parameterChangesChangeResults() {
        val raw = CsvParser.parse(StringReader(syntheticCsv(seconds = 30.0)))
        val pre = Preprocessor.preprocess(raw)
        val loose = Analyzer.analyze(pre, null, AnalysisParams(algorithmParams = mapOf("peak_magnitude" to mapOf("prominence_factor" to 0.05, "prominence_floor" to 0.0, "min_interval_s" to 0.15))))
        val strict = Analyzer.analyze(pre, null, AnalysisParams(algorithmParams = mapOf("peak_magnitude" to mapOf("prominence_factor" to 1.5, "prominence_floor" to 3.0))))
        assertTrue(loose.algorithm("peak_magnitude")!!.stepCount >= strict.algorithm("peak_magnitude")!!.stepCount)
    }

    @Test
    fun gpxParsingAndCleaning() {
        val gpx = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="test"><metadata><name>Walk</name></metadata><trk><name>Walk</name><trkseg>
<trkpt lat="42.2665239" lon="23.1106868"><time>2026-06-26T06:56:47.916Z</time><ele>550.6</ele></trkpt>
<trkpt lat="42.2664943" lon="23.1107129"><time>2026-06-26T06:57:07.938Z</time><ele>550.7</ele></trkpt>
<trkpt lat="42.2664701" lon="23.1107472"><time>2026-06-26T06:57:09.838Z</time><ele>551.7</ele></trkpt>
<trkpt lat="42.3664701" lon="23.1107472"><time>2026-06-26T06:57:10.838Z</time><ele>551.7</ele></trkpt>
</trkseg></trk></gpx>"""
        val track = Gpx.parse(gpx.byteInputStream())
        assertEquals("Walk", track.name)
        assertEquals(4, track.points.size)
        val s = Gpx.summarise(track)!!
        assertTrue(s.hasTime)
        assertEquals(1, s.nRejected, "the 11 km jump in one second must be rejected")
        assertTrue(s.distanceM in 5.0..12.0, "distance ${s.distanceM}")
        assertTrue(s.rawDistanceM > 11000)
        assertEquals(1.1, s.elevationGainM, 1e-6)
        assertTrue(abs(s.durationSec - 22.922) < 1e-3)
    }

    @Test
    fun gpxCalibratesStepLength() {
        val raw = CsvParser.parse(StringReader(syntheticCsv(seconds = 60.0)))
        val pre = Preprocessor.preprocess(raw)
        // fake GPX summary: 80 m walked
        val summary = com.vislupus.stepanalyzer.core.model.GpxSummary(
            hasTime = true, distanceM = 80.0, rawDistanceM = 80.0, durationSec = 60.0, movingSpeedMps = 1.33,
            nPoints = 10, nRejected = 0, elevationGainM = 0.0, lat = DoubleArray(0), lon = DoubleArray(0),
            cumulativeDistanceM = DoubleArray(0), timeSec = DoubleArray(0),
        )
        val result = Analyzer.analyze(pre, summary, AnalysisParams())
        val gpxAvg = result.stepLength.methods.first { it.id == "gpx_avg" }
        assertTrue(abs(gpxAvg.stepLengthM - 80.0 / result.stepLength.referenceStepCount) < 1e-9)
        val weinberg = result.stepLength.methods.first { it.id == "weinberg" }
        assertTrue(weinberg.calibrated)
        assertNotNull(weinberg.calibratedK)
        assertEquals("gpx_avg", result.stepLength.recommendedSource)
    }
}
