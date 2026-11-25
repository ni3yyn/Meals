package dz.meals.app

import android.app.Application

class MealsApplication : Application() {
    override fun onCreate() {
        // Install diagnostics before any app-specific startup work.
        instance = this
        MealsStartupLogger.initialize(this)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { MealsStartupLogger.recordCrash(thread, throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }

        MealsStartupLogger.mark("Application.onCreate completed")
        super.onCreate()
    }

    companion object {
        @Volatile
        lateinit var instance: MealsApplication
            private set
    }
}
