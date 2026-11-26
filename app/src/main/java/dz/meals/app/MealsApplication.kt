package dz.meals.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MealsApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            OkHttpClient.Builder()
                                .connectTimeout(10, TimeUnit.SECONDS)
                                .readTimeout(20, TimeUnit.SECONDS)
                                .callTimeout(25, TimeUnit.SECONDS)
                                .retryOnConnectionFailure(true)
                                .addInterceptor { chain ->
                                    val request = chain.request().newBuilder()
                                        .header(
                                            "User-Agent",
                                            "Midati-Android/1.0 (https://github.com/ni3yyn/Meals)"
                                        )
                                        .build()
                                    chain.proceed(request)
                                }
                                .build()
                        }
                    )
                )
            }
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
