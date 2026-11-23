package es.tesigno.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import es.tesigno.data.*
import java.io.File
import java.io.IOException

class MediaFiles(private val context: Context) {
    private val videos = File(context.filesDir, "videos").apply { mkdirs() }
    private val staging = File(context.filesDir, "staging").apply { mkdirs() }
    private val maxBytes = 500L * 1024 * 1024
    fun newCapture(): String = "stage:${newContentId()}.mp4".also { file(it)!!.createNewFile() }
    fun captureUri(reference: String): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file(reference)!!)
    fun file(reference: String): File? {
        val privateName = VideoResolver.privateName(reference)
        if (privateName != null) return File(videos, privateName)
        if (reference.startsWith("stage:")) {
            val name = VideoResolver.privateName("private:" + reference.removePrefix("stage:")) ?: return null
            return File(staging, name)
        }
        return null
    }
    fun resolve(reference: String): String? {
        if (reference.startsWith("private:") || reference.startsWith("stage:")) return file(reference)?.takeIf { it.isFile && it.length() > 0 }?.let { Uri.fromFile(it).toString() }
        return VideoResolver.resolve(reference) { path -> try { context.assets.open(path).use { }; true } catch (_: IOException) { false } }
    }
    fun validate(reference: String) {
        val file = file(reference) ?: throw IOException("No se encuentra el vídeo")
        require(file.length() in 1..maxBytes) { "El vídeo está vacío o supera 500 MB" }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "El archivo no contiene un vídeo compatible" }
        } finally { retriever.release() }
    }
    fun import(uri: Uri): String {
        val reference = newCapture()
        try {
            val type = context.contentResolver.getType(uri)
            require(type == null || type.startsWith("video/")) { "Elige un archivo de vídeo" }
            val target = file(reference)!!
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        require(total <= maxBytes) { "El vídeo supera 500 MB" }
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            } ?: throw IOException("No se ha podido abrir el vídeo")
            validate(reference)
            return reference
        } catch (e: Exception) { discard(reference); throw e }
    }
    // Copy before the catalog transaction; keep the draft if saving fails.
    fun promote(reference: String): String {
        if (!reference.startsWith("stage:")) return reference
        validate(reference)
        val destination = "private:${newContentId()}.mp4"
        try {
            file(reference)!!.inputStream().use { input -> file(destination)!!.outputStream().use { output -> input.copyTo(output); output.fd.sync() } }
            return destination
        } catch (e: Exception) { discard(destination); throw e }
    }
    fun discard(reference: String) { file(reference)?.delete() }
    fun cleanup(catalog: Catalog, protectedStages: Set<String> = emptySet()) {
        val used = catalog.expressions.map { it.video }.toSet()
        videos.listFiles()?.filter { "private:${it.name}" !in used }?.forEach { it.delete() }
        val yesterday = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        staging.listFiles()?.filter { it.lastModified() < yesterday && "stage:${it.name}" !in protectedStages }?.forEach { it.delete() }
    }
}
