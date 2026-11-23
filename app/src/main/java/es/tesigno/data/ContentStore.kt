package es.tesigno.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** A single nullable ID makes two simultaneously expanded categories impossible. */
fun toggleCategory(current: String?, pressed: String): String? = if (current == pressed) null else pressed

val categoryIcons = linkedMapOf("chat" to "Conversación", "hand" to "Saludos", "cup" to "Necesidades", "home" to "Vida cotidiana", "heart" to "Afecto", "star" to "Otros")
fun defaultIcon(id: String) = when (id) { "saludos" -> "hand"; "necesidades" -> "cup"; "vida_cotidiana" -> "home"; else -> "chat" }
fun newContentId() = "user_" + UUID.randomUUID().toString().replace("-", "")

/** Snapshot includes known bundled IDs, so updates cannot resurrect intentionally deleted entries. */
data class ContentSnapshot(val catalog: Catalog, val knownCategories: Set<String>, val knownExpressions: Set<String>)

object ContentCodec {
    fun encode(snapshot: ContentSnapshot): String = JSONObject().apply {
        put("schema", 1)
        put("categories", JSONArray().apply { snapshot.catalog.categories.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("icon", it.icon)) } })
        put("expressions", JSONArray().apply { snapshot.catalog.expressions.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("category", it.category).put("video", it.video)) } })
        put("knownCategories", JSONArray(snapshot.knownCategories.toList()))
        put("knownExpressions", JSONArray(snapshot.knownExpressions.toList()))
    }.toString(2)
    fun decode(text: String): ContentSnapshot {
        val root = JSONObject(text)
        require(root.getInt("schema") == 1) { "Versión de catálogo no compatible" }
        fun ids(key: String) = root.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        return ContentSnapshot(CatalogReader.parse(text, personal = true), ids("knownCategories"), ids("knownExpressions"))
    }
}

/** Same-directory fsync + rename: failed writes leave the previous snapshot intact. */
interface SnapshotStorage { fun read(): String?; fun write(text: String) }

class SnapshotFile(private val file: File) : SnapshotStorage {
    override fun read(): String? = if (file.exists()) file.readText() else null
    override fun write(text: String) {
        file.parentFile?.mkdirs()
        val temp = File.createTempFile("catalog_", ".tmp", file.parentFile)
        try {
            temp.outputStream().use { output -> output.write(text.toByteArray(Charsets.UTF_8)); output.fd.sync() }
            if (!temp.renameTo(file)) throw IOException("No se ha podido guardar el catálogo")
        } finally { temp.delete() }
    }
}

class ContentStore(private val disk: SnapshotStorage, bundled: Catalog) {
    var snapshot: ContentSnapshot
        private set
    val catalog get() = snapshot.catalog
    init {
        val saved = disk.read()?.let(ContentCodec::decode)
        snapshot = if (saved == null) ContentSnapshot(bundled, bundled.categories.map { it.id }.toSet(), bundled.expressions.map { it.id }.toSet())
        else {
            val categories = saved.catalog.categories + bundled.categories.filter { it.id !in saved.knownCategories }
            val expressions = saved.catalog.expressions + bundled.expressions.filter { it.id !in saved.knownExpressions && it.category in categories.map { c -> c.id } }
            ContentSnapshot(Catalog(categories, expressions), saved.knownCategories + bundled.categories.map { it.id }, saved.knownExpressions + bundled.expressions.map { it.id })
        }
        disk.write(ContentCodec.encode(snapshot))
    }
    @Synchronized private fun commit(next: Catalog) {
        val updated = snapshot.copy(catalog = next)
        // Round trip validates referential integrity before touching disk or exposing changes.
        ContentCodec.decode(ContentCodec.encode(updated))
        disk.write(ContentCodec.encode(updated))
        snapshot = updated
    }
    fun saveCategory(category: Category) {
        require(category.text.isNotBlank()) { "Escribe un nombre" }
        require(catalog.categories.none { it.id != category.id && it.text.trim().equals(category.text.trim(), true) }) { "Ya existe una categoría con ese nombre" }
        val clean = category.copy(text = category.text.trim())
        val next = if (catalog.categories.any { it.id == clean.id }) catalog.categories.map { if (it.id == clean.id) clean else it } else catalog.categories + clean
        commit(catalog.copy(categories = next))
    }
    fun saveExpression(expression: Expression) {
        require(expression.text.isNotBlank()) { "Escribe el texto de la expresión" }
        require(catalog.categories.any { it.id == expression.category }) { "Elige una categoría" }
        require(catalog.expressions.none { it.id != expression.id && it.category == expression.category && it.text.trim().equals(expression.text.trim(), true) }) { "Ya existe esa expresión en la categoría" }
        val clean = expression.copy(text = expression.text.trim())
        val next = if (catalog.expressions.any { it.id == clean.id }) catalog.expressions.map { if (it.id == clean.id) clean else it } else catalog.expressions + clean
        commit(catalog.copy(expressions = next))
    }
    fun deleteExpression(id: String) = commit(catalog.copy(expressions = catalog.expressions.filterNot { it.id == id }))
    fun deleteCategory(id: String, moveTo: String? = null) {
        require(moveTo == null || (moveTo != id && catalog.categories.any { it.id == moveTo })) { "Categoría de destino incorrecta" }
        val expressions = if (moveTo == null) catalog.expressions.filterNot { it.category == id } else catalog.expressions.map { if (it.category == id) it.copy(category = moveTo) else it }
        commit(Catalog(catalog.categories.filterNot { it.id == id }, expressions))
    }
    fun moveCategory(id: String, direction: Int) {
        val list = catalog.categories.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = index + direction
        if (index >= 0 && target in list.indices) { java.util.Collections.swap(list, index, target); commit(catalog.copy(categories = list)) }
    }
    fun moveExpression(id: String, direction: Int) {
        val expression = catalog.expressions.find { it.id == id } ?: return
        val siblings = catalog.expressions.filter { it.category == expression.category }
        val target = siblings.indexOfFirst { it.id == id } + direction
        if (target !in siblings.indices) return
        val list = catalog.expressions.toMutableList()
        java.util.Collections.swap(list, list.indexOf(expression), list.indexOf(siblings[target]))
        commit(catalog.copy(expressions = list))
    }
}
