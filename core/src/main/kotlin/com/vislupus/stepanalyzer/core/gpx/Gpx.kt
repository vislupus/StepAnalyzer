package com.vislupus.stepanalyzer.core.gpx

import com.vislupus.stepanalyzer.core.config.CoreDefaults
import com.vislupus.stepanalyzer.core.model.GpxSummary
import java.io.InputStream
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A single GPS fix. `ele` and `timeSec` are NaN when missing. */
data class GpxPoint(val lat: Double, val lon: Double, val ele: Double, val timeSec: Double)

/** Parsed GPX track (track points, falling back to route points / waypoints). */
class GpxTrack(val name: String?, val points: List<GpxPoint>) {
    val hasTime: Boolean get() = points.any { !it.timeSec.isNaN() }
}

object Gpx {

    /** Great-circle distance in metres (haversine). */
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLmb = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2) * sin(dPhi / 2) + cos(p1) * cos(p2) * sin(dLmb / 2) * sin(dLmb / 2)
        return 2 * CoreDefaults.Gps.EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    fun parse(input: InputStream): GpxTrack {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (_: Exception) {
            // Parser does not support the feature; continue without it.
        }
        val doc = factory.newDocumentBuilder().parse(input)
        val root = doc.documentElement
        var name: String? = null
        val nameNodes = root.getElementsByTagNameNS("*", "name")
        if (nameNodes.length > 0) name = nameNodes.item(0).textContent?.trim()

        val points = ArrayList<GpxPoint>()
        for (tag in listOf("trkpt", "rtept", "wpt")) {
            val nodes = root.getElementsByTagNameNS("*", tag)
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? org.w3c.dom.Element ?: continue
                val lat = el.getAttribute("lat").toDoubleOrNull() ?: continue
                val lon = el.getAttribute("lon").toDoubleOrNull() ?: continue
                var ele = Double.NaN
                var time = Double.NaN
                val children = el.childNodes
                for (c in 0 until children.length) {
                    val child = children.item(c) as? org.w3c.dom.Element ?: continue
                    when (child.localName ?: child.tagName) {
                        "ele" -> ele = child.textContent.trim().toDoubleOrNull() ?: Double.NaN
                        "time" -> time = parseIsoTime(child.textContent.trim())
                    }
                }
                points.add(GpxPoint(lat, lon, ele, time))
            }
            if (points.isNotEmpty()) break
        }
        return GpxTrack(name, points)
    }

    fun parseIsoTime(s: String): Double {
        if (s.isEmpty()) return Double.NaN
        return try {
            OffsetDateTime.parse(s).toInstant().let { it.epochSecond + it.nano / 1e9 }
        } catch (e: Exception) {
            try {
                LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).let { it.epochSecond + it.nano / 1e9 }
            } catch (e2: Exception) {
                Double.NaN
            }
        }
    }

    /**
     * Total distance after rejecting segments with non-positive time deltas or implausible
     * speed, plus duration, moving speed, elevation gain and the cumulative distance curve.
     * Returns null when the track has fewer than two points.
     */
    fun summarise(track: GpxTrack, maxSpeedMps: Double = CoreDefaults.Gps.MAX_SPEED_MPS): GpxSummary? {
        val pts = track.points
        val n = pts.size
        if (n < 2) return null
        val hasTime = track.hasTime
        val segD = DoubleArray(n)
        val segDt = DoubleArray(n) { Double.NaN }
        val keep = BooleanArray(n) { true }
        var raw = 0.0
        for (i in 1 until n) {
            val d = haversine(pts[i - 1].lat, pts[i - 1].lon, pts[i].lat, pts[i].lon)
            segD[i] = d
            raw += d
            val t0 = pts[i - 1].timeSec
            val t1 = pts[i].timeSec
            if (hasTime && !t0.isNaN() && !t1.isNaN()) {
                val dt = t1 - t0
                segDt[i] = dt
                if (dt <= 0 || d / dt > maxSpeedMps) keep[i] = false
            }
        }
        var clean = 0.0
        for (i in 0 until n) if (keep[i]) clean += segD[i]
        var duration = 0.0
        var movingSpeed = 0.0
        if (hasTime) {
            var tMin = Double.POSITIVE_INFINITY
            var tMax = Double.NEGATIVE_INFINITY
            for (p in pts) if (!p.timeSec.isNaN()) { if (p.timeSec < tMin) tMin = p.timeSec; if (p.timeSec > tMax) tMax = p.timeSec }
            duration = tMax - tMin
            var movingDt = 0.0
            for (i in 0 until n) if (keep[i] && !segDt[i].isNaN()) movingDt += segDt[i]
            movingSpeed = if (movingDt > 0) clean / movingDt else 0.0
        }
        val cum = DoubleArray(n)
        var acc = 0.0
        for (i in 0 until n) { if (keep[i]) acc += segD[i]; cum[i] = acc }
        var gain = 0.0
        var prevEle = Double.NaN
        for (p in pts) {
            if (p.ele.isNaN()) continue
            if (!prevEle.isNaN() && p.ele > prevEle) gain += p.ele - prevEle
            prevEle = p.ele
        }
        return GpxSummary(
            hasTime = hasTime,
            distanceM = clean,
            rawDistanceM = raw,
            durationSec = duration,
            movingSpeedMps = movingSpeed,
            nPoints = n,
            nRejected = keep.count { !it },
            elevationGainM = gain,
            lat = DoubleArray(n) { pts[it].lat },
            lon = DoubleArray(n) { pts[it].lon },
            cumulativeDistanceM = cum,
            timeSec = DoubleArray(n) { pts[it].timeSec },
        )
    }
}
