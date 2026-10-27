package com.vislupus.stepanalyzer.util

import org.json.JSONArray
import org.json.JSONObject

/** Small helpers around org.json for the few structures stored as JSON text in SQLite. */
object Json {

    fun paramsToJson(params: Map<String, Map<String, Double>>): String {
        val root = JSONObject()
        for ((algo, map) in params) {
            val o = JSONObject()
            for ((k, v) in map) o.put(k, v)
            root.put(algo, o)
        }
        return root.toString()
    }

    fun paramsFromJson(text: String?): Map<String, Map<String, Double>> {
        if (text.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(text)
            val out = HashMap<String, Map<String, Double>>()
            for (algo in root.keys()) {
                val o = root.getJSONObject(algo)
                val m = HashMap<String, Double>()
                for (k in o.keys()) m[k] = o.getDouble(k)
                out[algo] = m
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun doubleMapToJson(map: Map<String, Double>): String {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v)
        return o.toString()
    }

    fun stringSetToJson(set: Set<String>): String = JSONArray(set.toList()).toString()

    fun stringSetFromJson(text: String?): Set<String> {
        if (text.isNullOrBlank()) return emptySet()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun stringListToJson(list: List<String>): String = JSONArray(list).toString()
}
