package dz.meals.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Best-effort startup/crash logger. Mirrors diagnostics to app-private storage
 * and, on Android 10+, the user's Downloads/Meals folder.
 *
 * Never records preferences, meal data, network payloads, or credentials.
 */
object MealsStartupLogger {
    private const val TAG = "MealsStartup"
    private val lock = Any()
    private val once = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var initialized = false
    @Volatile private var internalFile: File? = null
    @Volatile private var downloadsUri: Uri? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            if (initialized) return
            initialized = true
            runCatching {
                val directory = File(context.filesDir, "diagnostics").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                internalFile = File(directory, "Meals-startup-$stamp.txt")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "Meals-startup-$stamp.txt")
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/Meals"
                        )
                    }
                    downloadsUri = context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                    )
                }
            }.onFailure {
                android.util.Log.e(TAG, "Could not prepare diagnostic files", it)
            }
        }
        mark("Diagnostics initialized; Android SDK ${Build.VERSION.SDK_INT}")
    }

    fun mark(message: String) {
        val line = "${timestamp()}  [STARTUP]  $message\n"
        write(line)
        android.util.Log.i(TAG, message)
    }

    fun markOnce(message: String) {
        if (once.add(message)) mark(message)
    }

    fun recordCrash(thread: Thread, throwable: Throwable) {
        val writer = StringWriter()
        PrintWriter(writer).use { throwable.printStackTrace(it) }

        val appVersion = runCatching {
            val info = MealsApplication.instance.packageManager.getPackageInfo(
                MealsApplication.instance.packageName,
                0
            )
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "${info.versionName} ($versionCode)"
        }.getOrDefault("unknown")

        val report = buildString {
            append(timestamp()).append("  [FATAL CRASH]\n")
            append("Thread: ").append(thread.name).append(" (").append(thread.id).append(")\n")
            append("App version: ").append(appVersion).append("\n")
            append(writer.toString()).append("\n")
        }
        write(report)
        android.util.Log.e(TAG, "Uncaught exception on ${thread.name}", throwable)
    }

    private fun write(text: String) {
        synchronized(lock) {
            runCatching {
                internalFile?.appendText(text, Charsets.UTF_8)
            }.onFailure {
                android.util.Log.e(TAG, "Internal log write failed", it)
            }

            runCatching {
                val uri = downloadsUri ?: return@runCatching
                MealsApplication.instance.contentResolver.openOutputStream(uri, "wa")?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                    it.flush()
                } ?: android.util.Log.e(TAG, "Downloads log stream unavailable")
            }.onFailure {
                android.util.Log.e(TAG, "Downloads log write failed", it)
            }
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
}
