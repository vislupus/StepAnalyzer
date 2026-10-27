package com.vislupus.stepanalyzer.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Minimal ViewModel factory so screens can construct view models with constructor arguments. */
class VmFactory<VM : ViewModel>(private val creator: () -> VM) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}
