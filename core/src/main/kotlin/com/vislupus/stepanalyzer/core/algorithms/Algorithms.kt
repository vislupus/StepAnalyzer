package com.vislupus.stepanalyzer.core.algorithms

/** Registry of every step-counting algorithm, in display order. */
object Algorithms {
    val all: List<StepAlgorithm> = listOf(
        PeakAlgorithm,
        PeakValleyAlgorithm,
        ZeroCrossingAlgorithm,
        AutocorrelationAlgorithm,
        FftCadenceAlgorithm,
        DynamicThresholdAlgorithm,
        PanTompkinsAlgorithm,
        HysteresisAlgorithm,
        GyroPeakAlgorithm,
        VerticalAccelAlgorithm,
    )

    /** The five algorithms of the original analyser. */
    val classic: List<String> = listOf(
        PeakAlgorithm.ID, PeakValleyAlgorithm.ID, ZeroCrossingAlgorithm.ID,
        AutocorrelationAlgorithm.ID, FftCadenceAlgorithm.ID,
    )

    fun byId(id: String): StepAlgorithm? = all.firstOrNull { it.id == id }

    /** Default parameters for every algorithm keyed by algorithm id. */
    fun defaultParams(): Map<String, Map<String, Double>> = all.associate { it.id to it.defaults() }
}
