package dz.meals.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache

class MealsApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("midati_recipe_images"))
                    .maxSizePercent(0.04)
                    .build()
            }
            .crossfade(180)
            .build()

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
