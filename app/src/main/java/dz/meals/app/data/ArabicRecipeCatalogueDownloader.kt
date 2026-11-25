package dz.meals.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ArabicCatalogueDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long
) {
    val fraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

/**
 * Downloads a pinned, immutable Arabic catalogue revision outside the APK.
 * The Git blob SHA is verified before a cached file is reused or a new file
 * becomes visible. Interrupted transfers remain isolated as a .part file.
 */
class ArabicRecipeCatalogueDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.MINUTES)
        .build()

    private val catalogueDirectory = File(appContext.filesDir, "recipe-catalogue/ar")
    private val targetFile = File(catalogueDirectory, "recipes-$REVISION.json")
    private val temporaryFile = File(catalogueDirectory, "recipes-$REVISION.json.part")

    suspend fun download(
        onProgress: (ArabicCatalogueDownloadProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (isValidFile(targetFile)) {
            withContext(Dispatchers.Main.immediate) {
                onProgress(ArabicCatalogueDownloadProgress(EXPECTED_BYTES, EXPECTED_BYTES))
            }
            return@withContext targetFile
        }

        catalogueDirectory.mkdirs()
        temporaryFile.delete()

        val request = Request.Builder()
            .url(DOWNLOAD_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "Midati-Android-Recipe-Catalogue")
            .build()
        val call = client.newCall(request)

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("تعذر تنزيل مكتبة الوصفات (HTTP ${response.code}).")
                }
                val body = response.body ?: throw IOException("لم يصل محتوى مكتبة الوصفات.")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: EXPECTED_BYTES
                if (totalBytes != EXPECTED_BYTES) {
                    throw IOException("حجم مكتبة الوصفات غير متوقع؛ أوقفنا التنزيل لحماية البيانات.")
                }

                var downloaded = 0L
                var lastReportAt = 0L
                body.byteStream().use { input ->
                    FileOutputStream(temporaryFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.nanoTime()
                            if (now - lastReportAt >= PROGRESS_INTERVAL_NANOS) {
                                lastReportAt = now
                                withContext(Dispatchers.Main.immediate) {
                                    onProgress(ArabicCatalogueDownloadProgress(downloaded, totalBytes))
                                }
                            }
                        }
                        output.fd.sync()
                    }
                }

                if (downloaded != EXPECTED_BYTES || temporaryFile.length() != EXPECTED_BYTES) {
                    throw IOException("اكتمل جزء من التنزيل فقط؛ يمكنك إعادة المحاولة بأمان.")
                }
                if (!hasExpectedGitBlobSha(temporaryFile)) {
                    throw IOException("تعذر التحقق من سلامة ملف الوصفات؛ لم نثبّت الملف.")
                }

                if (targetFile.exists() && !targetFile.delete()) {
                    throw IOException("تعذر استبدال النسخة المحلية القديمة من مكتبة الوصفات.")
                }
                if (!temporaryFile.renameTo(targetFile)) {
                    temporaryFile.copyTo(targetFile, overwrite = true)
                    temporaryFile.delete()
                }
                withContext(Dispatchers.Main.immediate) {
                    onProgress(ArabicCatalogueDownloadProgress(EXPECTED_BYTES, EXPECTED_BYTES))
                }
                targetFile
            }
        } catch (error: Exception) {
            call.cancel()
            temporaryFile.delete()
            throw error
        }
    }

    private fun isValidFile(file: File): Boolean =
        file.isFile && file.length() == EXPECTED_BYTES && hasExpectedGitBlobSha(file)

    private fun hasExpectedGitBlobSha(file: File): Boolean {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${file.length()}\u0000".toByteArray(Charsets.UTF_8))
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return actual.equals(EXPECTED_GIT_BLOB_SHA, ignoreCase = true)
    }

    companion object {
        const val REVISION = "9f3cb5a3f246676be7f0518f0fc115c86bd6a063"
        const val EXPECTED_RECIPE_COUNT = 912
        const val EXPECTED_BYTES = 12_843_193L
        const val EXPECTED_GIT_BLOB_SHA = "ddaea777653de7a9252ae6096453389889406bb7"
        const val DOWNLOAD_URL =
            "https://raw.githubusercontent.com/ni3yyn/Meals/$REVISION/data/recipes/ar/recipes.json"
        private const val PROGRESS_INTERVAL_NANOS = 120_000_000L
    }
}
