package com.vislupus.stepanalyzer.core.cli

import com.vislupus.stepanalyzer.core.analysis.AnalysisParams
import com.vislupus.stepanalyzer.core.analysis.Analyzer
import com.vislupus.stepanalyzer.core.analysis.Preprocessor
import com.vislupus.stepanalyzer.core.csv.CsvParser
import com.vislupus.stepanalyzer.core.gpx.Gpx
import com.vislupus.stepanalyzer.core.model.PreprocessOptions
import com.vislupus.stepanalyzer.core.model.Sex
import java.io.File
import java.util.Locale

/**
 * Desktop runner for quick experiments without the phone:
 *
 *     ./gradlew :core:run --args="sensors.csv [track.gpx] [--height 178] [--sex male]"
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage: sensors.csv [track.gpx] [--height CM] [--sex male|female] [--band LOW HIGH] [--fs HZ] [--param algo.key=value]... [--only id1,id2]")
        return
    }
    var csv: File? = null
    var gpx: File? = null
    var heightM: Double? = null
    var sex = Sex.UNKNOWN
    var bandLow = 0.5
    var bandHigh = 4.0
    var fs = 100.0
    val overrides = HashMap<String, HashMap<String, Double>>()
    var only: Set<String>? = null
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--height" -> { heightM = args[++i].toDouble() / 100.0 }
            "--sex" -> { sex = Sex.valueOf(args[++i].uppercase()) }
            "--band" -> { bandLow = args[++i].toDouble(); bandHigh = args[++i].toDouble() }
            "--fs" -> { fs = args[++i].toDouble() }
            // --param algorithm_id.param_key=value  (repeatable)
            "--param" -> {
                val spec = args[++i]
                val (path, value) = spec.split("=", limit = 2)
                val (algo, key) = path.split(".", limit = 2)
                overrides.getOrPut(algo) { HashMap() }[key] = value.toDouble()
            }
            "--only" -> { only = args[++i].split(",").toSet() }
            else -> if (args[i].lowercase().endsWith(".gpx")) gpx = File(args[i]) else csv = File(args[i])
        }
        i++
    }
    requireNotNull(csv) { "a CSV file is required" }

    val t0 = System.currentTimeMillis()
    val raw = csv.bufferedReader().use { CsvParser.parse(it) }
    val t1 = System.currentTimeMillis()
    val pre = Preprocessor.preprocess(raw, PreprocessOptions(fsTarget = fs, bandLowHz = bandLow, bandHighHz = bandHigh))
    val t2 = System.currentTimeMillis()
    val track = gpx?.inputStream()?.use { Gpx.parse(it) }
    val summary = track?.let { Gpx.summarise(it) }
    val result = Analyzer.analyze(pre, summary, AnalysisParams(algorithmParams = overrides, enabledAlgorithms = only, heightM = heightM, sex = sex))
    val t3 = System.currentTimeMillis()

    fun f(v: Double, d: Int = 2) = String.format(Locale.US, "%.${d}f", v)
    println("file: rows=${result.fileInfo.rows} duration=${f(result.fileInfo.durationSec, 1)}s fs=${f(pre.fs, 0)}Hz sensors=${result.fileInfo.sensorsFound.map { it.csvName }}")
    println("native rates: " + result.fileInfo.nativeRatesHz.entries.map { "${it.key.csvName}=${f(it.value, 1)}" }.joinToString(" "))
    println("units: accel=${pre.accelUnitDetected} gyro=${pre.gyroUnitDetected} source=${pre.dynamicSource.csvName}")
    println("timing: parse=${t1 - t0}ms preprocess=${t2 - t1}ms analysis=${t3 - t2}ms")
    summary?.let {
        println("gpx: distance=${f(it.distanceM, 1)}m raw=${f(it.rawDistanceM, 1)}m duration=${f(it.durationSec, 1)}s speed=${f(it.movingSpeedKmh)}km/h points=${it.nPoints} rejected=${it.nRejected} gain=${f(it.elevationGainM, 1)}m")
    }
    println("global periodicity: ${f(result.summary.globalPeriodicity)}")
    println()
    println(String.format(Locale.US, "%-22s %6s %8s %8s %6s  %s", "algorithm", "steps", "cadence", "L_gpx", "conf", "notes"))
    for (a in result.algorithms) {
        if (!a.available) { println(String.format(Locale.US, "%-22s %6s  unavailable: %s", a.id, "-", a.notes)); continue }
        println(String.format(Locale.US, "%-22s %6d %8.1f %8s %6.2f  %s", a.id, a.stepCount, a.cadenceSpm,
            a.avgStepLengthGpxM?.let { f(it, 3) } ?: "-", a.confidence, a.notes.joinToString(", ") { noteText(it) }))
    }
    println()
    println("recommended: ${result.recommended.algorithmId} -> ${result.recommended.stepCount} steps (confidence ${f(result.recommended.confidence)})")
    println("summary: min=${result.summary.minSteps} max=${result.summary.maxSteps} median=${result.summary.medianSteps} spread=${result.summary.spread}")
    println("reference for step length: ${result.referenceAlgorithmId} (${result.stepLength.referenceStepCount} steps), height used ${f(result.stepLength.heightUsedM)}m${if (result.stepLength.heightWasDefault) " (default)" else ""}")
    for (m in result.stepLength.methods) {
        println(String.format(Locale.US, "  %-16s L=%6.3f m  D=%8s  calibrated=%-5s %s", m.id, m.stepLengthM,
            m.distanceM?.let { f(it, 1) } ?: "-", m.calibrated,
            if (m.calibratedK != null) "K_cal=${f(m.calibratedK, 3)} (K_default=${m.defaultK} -> ${f(m.uncalibratedM ?: 0.0)} m)" else ""))
    }
    println("recommended step length: ${result.stepLength.recommendedM?.let { f(it, 3) }} m (${result.stepLength.recommendedSource})")
}

private fun noteText(n: com.vislupus.stepanalyzer.core.model.Note): String = when (n) {
    is com.vislupus.stepanalyzer.core.model.Note.StrongPeriodicity -> "strong periodicity"
    is com.vislupus.stepanalyzer.core.model.Note.IntervalCv -> "CV=" + String.format(Locale.US, "%.2f", n.cv)
    is com.vislupus.stepanalyzer.core.model.Note.GpxStepLength -> "L_gpx=" + String.format(Locale.US, "%.2f", n.metres)
    is com.vislupus.stepanalyzer.core.model.Note.OutOfPlausibleRange -> "out of plausible range"
    is com.vislupus.stepanalyzer.core.model.Note.FrequencyMethod -> "frequency method"
    is com.vislupus.stepanalyzer.core.model.Note.RequiresSensor -> "requires ${n.sensor.csvName}"
    is com.vislupus.stepanalyzer.core.model.Note.StrideLocked -> "stride guard x${n.windows}"
}
