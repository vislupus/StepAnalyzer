package com.vislupus.stepanalyzer.ui.sessions

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vislupus.stepanalyzer.AppContainer
import com.vislupus.stepanalyzer.data.db.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionsViewModel(private val container: AppContainer) : ViewModel() {
    val sessions: StateFlow<List<Session>> get() = container.sessions.sessions

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> get() = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> get() = _message

    /** Id of the session created by the last import (for navigation). */
    private val _imported = MutableStateFlow<Long?>(null)
    val imported: StateFlow<Long?> get() = _imported

    init {
        viewModelScope.launch(Dispatchers.IO) { container.sessions.refresh() }
    }

    fun consumeImported() { _imported.value = null }
    fun consumeMessage() { _message.value = null }

    fun rename(id: Long, name: String) = viewModelScope.launch(Dispatchers.IO) { container.sessions.rename(id, name) }

    fun delete(id: Long) = viewModelScope.launch(Dispatchers.IO) { container.sessions.delete(id) }

    fun importCsv(context: Context, uri: Uri) = viewModelScope.launch {
        _busy.value = true
        try {
            val id = withContext(Dispatchers.IO) {
                val name = displayName(context, uri) ?: "import"
                context.contentResolver.openInputStream(uri)?.use { input ->
                    container.sessions.importCsv(name.removeSuffix(".csv").removeSuffix(".CSV"), input, null)
                } ?: throw IllegalStateException("Cannot open $uri")
            }
            _imported.value = id
        } catch (e: Exception) {
            _message.value = e.message ?: e.toString()
        } finally {
            _busy.value = false
        }
    }

    fun attachGpx(context: Context, sessionId: Long, uri: Uri) = viewModelScope.launch {
        _busy.value = true
        try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { container.sessions.attachGpx(sessionId, it) }
                    ?: throw IllegalStateException("Cannot open $uri")
            }
        } catch (e: Exception) {
            _message.value = e.message ?: e.toString()
        } finally {
            _busy.value = false
        }
    }

    companion object {
        fun displayName(context: Context, uri: Uri): String? {
            val fromProvider: String? = context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            return fromProvider ?: uri.lastPathSegment
        }
    }
}
