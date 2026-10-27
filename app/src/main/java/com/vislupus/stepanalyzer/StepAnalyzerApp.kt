package com.vislupus.stepanalyzer

import android.app.Application
import com.vislupus.stepanalyzer.data.db.AppDatabase
import com.vislupus.stepanalyzer.data.files.SessionFiles
import com.vislupus.stepanalyzer.data.repo.SessionRepository
import com.vislupus.stepanalyzer.data.repo.SettingsRepository

/** Hand-rolled dependency container (no DI framework needed for an app this size). */
class AppContainer(app: Application) {
    val database: AppDatabase = AppDatabase(app)
    val files: SessionFiles = SessionFiles(app)
    val settings: SettingsRepository = SettingsRepository(database)
    val sessions: SessionRepository = SessionRepository(database, files).also { it.refresh() }
}

class StepAnalyzerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
