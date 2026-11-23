package es.tesigno.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ContentStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val bundled get() = CatalogReader.parse(File("src/main/assets/expressions.json").readText())
    private fun disk() = SnapshotFile(File(folder.root, "catalog.json"))
    private fun store() = ContentStore(disk(), bundled)
    @Test fun onlyOneCategoryCanBeExpanded() {
        var current: String? = null
        current = toggleCategory(current, "saludos")
        assertEquals("saludos", current)
        current = toggleCategory(current, "necesidades")
        assertEquals("necesidades", current)
        assertNotEquals("saludos", current)
    }
    @Test fun pressingOpenCategoryCollapsesIt() { assertNull(toggleCategory("saludos", "saludos")) }
    @Test fun initialAccordionIsCollapsed() { val current: String? = null; assertNull(current); assertEquals("saludos", toggleCategory(current, "saludos")) }
    @Test fun createsCategoryAndRecoversAfterRecreation() {
        store().saveCategory(Category("nueva", "Nueva", "star"))
        assertEquals("Nueva", store().catalog.categories.last().text)
    }
    @Test fun editsCategoryWithStableId() {
        val s = store(); s.saveCategory(Category("saludos", "Mis saludos", "heart"))
        val restored = store()
        assertEquals("Mis saludos", restored.catalog.categories.first().text)
        assertEquals("heart", restored.catalog.categories.first().icon)
        assertEquals(4, restored.catalog.expressions.count { it.category == "saludos" })
    }
    @Test fun createsExpressionWithoutVideo() {
        val s = store(); s.saveExpression(Expression("nueva", "Vamos a pasear", "saludos", ""))
        assertEquals("", store().catalog.expressions.last().video)
    }
    @Test fun changesCategoryWithoutChangingVideoAssociation() {
        val s = store(); val hola = s.catalog.expressions.first()
        s.saveExpression(hola.copy(category = "conversacion", text = "Hola de nuevo"))
        val restored = store().catalog.expressions.first { it.id == hola.id }
        assertEquals("conversacion", restored.category)
        assertEquals("hola.mp4", restored.video)
    }
    @Test fun deletesExpressionPersistently() {
        store().deleteExpression("hola")
        assertFalse(store().catalog.expressions.any { it.id == "hola" })
    }
    @Test fun movesCategoryContentsBeforeDeleting() {
        val s = store(); s.deleteCategory("saludos", "conversacion")
        val restored = store().catalog
        assertFalse(restored.categories.any { it.id == "saludos" })
        assertEquals(6, restored.expressions.count { it.category == "conversacion" })
        assertEquals(14, restored.expressions.size)
    }
    @Test fun cascadeDeletionDoesNotResurrectBundledContent() {
        store().deleteCategory("saludos")
        assertFalse(store().catalog.categories.any { it.id == "saludos" })
        assertFalse(store().catalog.expressions.any { it.id == "hola" })
    }
    @Test fun canDeleteAllAndKeepEmptyCatalog() {
        val s = store(); s.catalog.categories.toList().forEach { s.deleteCategory(it.id) }
        assertTrue(store().catalog.categories.isEmpty())
        assertTrue(store().catalog.expressions.isEmpty())
    }
    @Test fun preservesPrivateVideoReferenceAfterRestart() {
        val reference = "private:${newContentId()}.mp4"
        store().saveExpression(Expression("propia", "Mi vídeo", "saludos", reference))
        assertEquals(reference, store().catalog.expressions.last().video)
    }
    @Test fun videoResolverRejectsUnsafePrivatePaths() {
        assertNull(VideoResolver.privateName("private:../hola.mp4"))
        assertNull(VideoResolver.resolve("hola.mp4") { false })
        assertNotNull(VideoResolver.privateName("private:${newContentId()}.mp4"))
    }
    @Test fun ordersCategoriesAndExpressionsPersistently() {
        val s = store(); s.moveCategory("saludos", 1); s.moveExpression("hola", 1)
        val restored = store().catalog
        assertEquals("conversacion", restored.categories.first().id)
        assertEquals("buenos_dias", restored.expressions.filter { it.category == "saludos" }.first().id)
    }
    @Test fun avoidsDuplicateCategoryNamesAndBlankNames() {
        val s = store()
        assertTrue(runCatching { s.saveCategory(Category("otra", " saludos ")) }.isFailure)
        assertTrue(runCatching { s.saveCategory(Category("otra", "  ")) }.isFailure)
        assertEquals(4, store().catalog.categories.size)
    }
    @Test fun rejectsUnknownCategoryAndUncommittedStagePath() {
        val s = store()
        assertTrue(runCatching { s.saveExpression(Expression("otra", "Otra", "no_existe", "")) }.isFailure)
        assertTrue(runCatching { s.saveExpression(Expression("otra", "Otra", "saludos", "stage:${newContentId()}.mp4")) }.isFailure)
        assertEquals(14, store().catalog.expressions.size)
    }
    @Test fun failedSaveKeepsPreviousMemoryAndDisk() {
        val actual = disk()
        var fail = false
        val failing = object : SnapshotStorage {
            override fun read() = actual.read()
            override fun write(text: String) { if (fail) throw IOException("Sin espacio"); actual.write(text) }
        }
        val s = ContentStore(failing, bundled)
        val before = s.catalog
        fail = true
        assertTrue(runCatching { s.deleteExpression("hola") }.isFailure)
        assertEquals(before, s.catalog)
        assertEquals(before, store().catalog)
    }
    @Test fun corruptSnapshotIsNeverReplacedWithDefaults() {
        val file = File(folder.root, "catalog.json"); file.writeText("{incorrecto")
        assertTrue(runCatching { store() }.isFailure)
        assertEquals("{incorrecto", file.readText())
    }
    @Test fun bundleUpdateAddsNewEntriesWithoutOverwritingPersonalEdits() {
        val s = store(); s.saveCategory(Category("saludos", "Personal")); s.deleteExpression("hola")
        val expanded = bundled.copy(expressions = bundled.expressions + Expression("nuevo", "Nuevo", "saludos", "nuevo.mp4"))
        val updated = ContentStore(disk(), expanded)
        assertEquals("Personal", updated.catalog.categories.first().text)
        assertTrue(updated.catalog.expressions.any { it.id == "nuevo" })
        assertFalse(updated.catalog.expressions.any { it.id == "hola" })
    }
}
