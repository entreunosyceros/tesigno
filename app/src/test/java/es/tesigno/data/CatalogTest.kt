package es.tesigno.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CatalogTest {
    private val source get() = File("src/main/assets/expressions.json").readText()
    @Test fun initialCatalog() {
        val catalog = CatalogReader.parse(source)
        assertEquals(14, catalog.expressions.size)
        assertEquals(listOf("saludos", "conversacion", "necesidades", "vida_cotidiana"), catalog.categories.map { it.id })
        assertEquals(listOf(4, 2, 4, 4), catalog.categories.map { c -> catalog.expressions.count { it.category == c.id } })
    }
    private fun rejects(json: String) { assertTrue(runCatching { CatalogReader.parse(json) }.isFailure) }
    @Test fun malformedJson() { rejects("{") }
    @Test fun emptyCatalog() { rejects("""{"categories":[],"expressions":[]}""") }
    @Test fun duplicateExpression() { rejects(source.replace("\"buenos_dias\"", "\"hola\"")) }
    @Test fun duplicateCategory() { rejects(source.replace("\"conversacion\"", "\"saludos\"")) }
    @Test fun unknownCategory() { rejects(source.replace("\"category\": \"saludos\"", "\"category\": \"otra\"")) }
    @Test fun blankText() { rejects(source.replace("\"Hola\"", "\"  \"")) }
    @Test fun unsafePath() { rejects(source.replace("hola.mp4", "../hola.mp4")) }
    @Test fun missingField() { rejects(source.replace("\"video\":", "\"otro\":")) }
    @Test fun localResolution() {
        assertEquals("asset:///videos/hola.mp4", VideoResolver.resolve("hola.mp4") { it == "videos/hola.mp4" })
        assertNull(VideoResolver.resolve("hola.mp4") { false })
        assertNull(VideoResolver.resolve("https://example.com/video.mp4") { true })
        assertNull(VideoResolver.resolve("../hola.mp4") { true })
    }
}
