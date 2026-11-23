package es.tesigno

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import es.tesigno.data.*
import es.tesigno.media.MediaFiles
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Technical color-only MP4 lives in the test APK, never the application catalog. */
@RunWith(AndroidJUnit4::class)
class MediaFilesTest {
    private lateinit var root: File
    private lateinit var media: MediaFiles
    @Before fun setup() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(target.cacheDir, "media_test_${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(target) { override fun getFilesDir(): File = root }
        media = MediaFiles(isolated)
    }
    @After fun cleanup() { root.deleteRecursively() }
    private fun validCapture(): String {
        val reference = media.newCapture()
        InstrumentationRegistry.getInstrumentation().context.assets.open("technical_test.mp4").use { input -> media.file(reference)!!.outputStream().use { input.copyTo(it) } }
        return reference
    }
    @Test fun validatesRecordedVideoAndPromotesOnlyOnSave() {
        val capture = validCapture()
        media.validate(capture)
        val saved = media.promote(capture)
        assertNotEquals(capture, saved)
        assertNotNull(media.resolve(saved))
        assertTrue(media.file(capture)!!.exists())
        media.discard(capture)
        assertNotNull(media.resolve(saved))
    }
    @Test fun importsLocalVideoAndSurvivesOriginalRemoval() {
        val source = validCapture()
        val imported = media.import(Uri.fromFile(media.file(source)))
        media.discard(source)
        assertNotNull(media.resolve(imported))
        media.validate(imported)
    }
    @Test fun missingAndEmptyVideosAreHandled() {
        val empty = media.newCapture()
        assertNull(media.resolve(empty))
        assertTrue(runCatching { media.validate(empty) }.isFailure)
        media.discard(empty)
        assertNull(media.resolve(empty))
    }
    @Test fun corruptReplacementLeavesPreviousVideoAvailable() {
        val original = media.promote(validCapture())
        val broken = media.newCapture()
        media.file(broken)!!.writeText("not a video")
        assertTrue(runCatching { media.promote(broken) }.isFailure)
        assertNotNull(media.resolve(original))
    }
    @Test fun cancelDraftAndCleanupRespectReferencedVideos() {
        val stage = validCapture()
        val saved = media.promote(stage)
        media.discard(stage)
        val catalog = Catalog(listOf(Category("test", "Prueba")), listOf(Expression("test", "Prueba", "test", saved)))
        media.cleanup(catalog)
        assertNotNull(media.resolve(saved))
        media.cleanup(catalog.copy(expressions = emptyList()))
        assertNull(media.resolve(saved))
    }
}
