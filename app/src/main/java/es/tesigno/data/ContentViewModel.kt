package es.tesigno.data

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import es.tesigno.media.MediaFiles
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class ContentViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("settings", 0)
    var darkTheme by mutableStateOf(preferences.getBoolean("darkTheme", true)); private set
    fun setDarkMode(enabled: Boolean) { darkTheme = enabled; preferences.edit().putBoolean("darkTheme", enabled).apply() }
    val media = MediaFiles(application)
    private lateinit var store: ContentStore
    private val ready = CompletableDeferred<Unit>()
    var catalog by mutableStateOf<Catalog?>(null); private set
    var busy by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var draft by mutableStateOf(saved.get<String>("draft")?.let { text -> runCatching {
        val j = JSONObject(text); Expression(j.getString("id"), j.getString("text"), j.getString("category"), j.getString("video"))
    }.getOrNull() }); private set
    private var pendingCapture: String?
        get() = saved["capture"]
        set(value) { saved["capture"] = value }
    init {
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val bundled = application.assets.open("expressions.json").bufferedReader().use { CatalogReader.parse(it.readText()) }
                    ContentStore(SnapshotFile(File(application.filesDir, "catalog.json")), bundled).also {
                        media.cleanup(it.catalog, setOfNotNull(draft?.video, pendingCapture))
                    }
                }
                store = loaded
                catalog = store.catalog
            } catch (_: Exception) { error = "No se ha podido abrir el catálogo. No se han borrado tus datos. Comprueba el espacio disponible y vuelve a abrir TeSigno." }
            finally { busy = false; ready.complete(Unit) }
        }
    }
    fun dismissError() { error = null }
    fun reportError(message: String) { error = message }
    private fun updateDraft(value: Expression?) {
        draft = value
        saved["draft"] = value?.let { JSONObject().put("id", it.id).put("text", it.text).put("category", it.category).put("video", it.video).toString() }
    }
    fun beginExpression(expression: Expression? = null) {
        cancelDraft()
        updateDraft(expression ?: Expression(newContentId(), "", catalog?.categories?.firstOrNull()?.id ?: "", ""))
    }
    fun changeDraft(text: String? = null, category: String? = null) { draft?.let { updateDraft(it.copy(text = text ?: it.text, category = category ?: it.category)) } }
    fun removeDraftVideo() { draft?.let { if (it.video.startsWith("stage:")) media.discard(it.video); updateDraft(it.copy(video = "")) } }
    fun cancelDraft() {
        draft?.video?.takeIf { it.startsWith("stage:") }?.let(media::discard)
        pendingCapture?.let(media::discard)
        pendingCapture = null
        updateDraft(null)
    }
    fun mutate(onDone: () -> Unit = {}, action: ContentStore.() -> Unit) {
        if (busy || catalog == null) return
        busy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { store.action() }
                catalog = store.catalog
                withContext(Dispatchers.IO) { runCatching { media.cleanup(store.catalog, setOfNotNull(draft?.video, pendingCapture)) } }
                onDone()
            } catch (e: Exception) { error = e.message ?: "No se ha podido guardar. Comprueba el espacio disponible." }
            finally { busy = false }
        }
    }
    fun saveDraft(onDone: () -> Unit) {
        val value = draft ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            var promoted = value.video
            var committed = false
            try {
                withContext(Dispatchers.IO) {
                    promoted = media.promote(value.video)
                    store.saveExpression(value.copy(video = promoted))
                    committed = true
                }
                catalog = store.catalog
                if (value.video.startsWith("stage:")) media.discard(value.video)
                updateDraft(null)
                withContext(Dispatchers.IO) { runCatching { media.cleanup(store.catalog) } }
                onDone()
            } catch (e: Exception) {
                if (!committed && promoted != value.video) media.discard(promoted)
                error = e.message ?: "No se ha podido guardar la expresión. Se mantiene el vídeo anterior."
            } finally { busy = false }
        }
    }
    fun prepareCapture(): Uri? {
        if (busy || pendingCapture != null) return null
        return try { val reference = media.newCapture(); pendingCapture = reference; media.captureUri(reference) }
        catch (_: Exception) { cancelCapture(); error = "No se puede preparar la grabación. Comprueba el espacio disponible."; null }
    }
    fun cancelCapture() { pendingCapture?.let(media::discard); pendingCapture = null }
    fun finishCapture(success: Boolean) {
        val reference = pendingCapture ?: return
        pendingCapture = null
        if (!success) { media.discard(reference); return }
        acceptVideo { try { media.validate(reference); reference } catch (e: Exception) { media.discard(reference); throw e } }
    }
    fun importVideo(uri: Uri) = acceptVideo { media.import(uri) }
    private fun acceptVideo(load: () -> String) {
        viewModelScope.launch {
            // A camera result may arrive while the process is restoring its catalog.
            ready.await()
            if (busy || catalog == null) return@launch
            busy = true
            try {
                val reference = withContext(Dispatchers.IO) { load() }
                val previous = draft
                if (previous == null) media.discard(reference)
                else {
                    if (previous.video.startsWith("stage:")) media.discard(previous.video)
                    updateDraft(previous.copy(video = reference))
                }
            } catch (e: Exception) { error = "No se ha podido incorporar el vídeo. Se mantiene el anterior. ${e.message.orEmpty()}" }
            finally { busy = false }
        }
    }
}
