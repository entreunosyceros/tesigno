package es.tesigno.data

import org.json.JSONObject

data class Category(val id: String, val text: String, val icon: String = defaultIcon(id))
data class Expression(val id: String, val text: String, val category: String, val video: String)
data class Catalog(val categories: List<Category>, val expressions: List<Expression>)

object CatalogReader {
    private val idPattern = Regex("[a-z][a-z0-9_]*")
    fun parse(json: String, personal: Boolean = false): Catalog {
        val root = JSONObject(json)
        val categories = root.getJSONArray("categories").let { array ->
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                Category(item.getString("id"), item.getString("text"), item.optString("icon", defaultIcon(item.getString("id"))))
            }
        }
        val expressions = root.getJSONArray("expressions").let { array ->
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                Expression(item.getString("id"), item.getString("text"), item.getString("category"), item.getString("video"))
            }
        }
        require(personal || (categories.isNotEmpty() && expressions.isNotEmpty())) { "Catálogo vacío" }
        require(categories.map { it.id }.distinct().size == categories.size) { "Categorías duplicadas" }
        require(expressions.map { it.id }.distinct().size == expressions.size) { "Expresiones duplicadas" }
        require(categories.all { idPattern.matches(it.id) && it.text.isNotBlank() && it.icon in categoryIcons }) { "Categoría incorrecta" }
        require(expressions.all { idPattern.matches(it.id) && it.text.isNotBlank() &&
            it.category in categories.map { category -> category.id } && (VideoResolver.validName(it.video) || (personal && (it.video.isEmpty() || VideoResolver.privateName(it.video) != null))) }) { "Expresión incorrecta" }
        return Catalog(categories, expressions)
    }
}

object VideoResolver {
    fun privateName(reference: String): String? = reference.removePrefix("private:").takeIf {
        reference.startsWith("private:") && Regex("user_[a-f0-9]{32}\\.mp4").matches(it)
    }
    fun validName(name: String) = Regex("[a-z][a-z0-9_]*\\.mp4").matches(name)
    fun resolve(name: String, exists: (String) -> Boolean): String? {
        if (!validName(name)) return null
        val path = "videos/$name"
        return if (exists(path)) "asset:///$path" else null
    }
}
