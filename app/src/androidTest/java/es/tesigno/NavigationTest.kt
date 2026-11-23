package es.tesigno

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready() {
        compose.waitUntil(60_000) { compose.onAllNodesWithText("⚙ Ajustes").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun accordionOpensOneCategoryAndPreservesItAcrossRecreation() {
        ready()
        compose.onNodeWithText("Saludos").performScrollTo().performClick()
        compose.onNodeWithText("Hola").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Necesidades").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hola").assertDoesNotExist()
        compose.onNodeWithText("Comer").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Comer").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Necesidades").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Comer").assertDoesNotExist()
    }
    @Test fun cancelCategoryDeletionKeepsItsExpressions() {
        ready()
        compose.onNodeWithText("⚙ Ajustes").performClick()
        compose.onNodeWithText("Gestionar categorías y expresiones").performClick()
        compose.onNodeWithText("Categorías").performClick()
        compose.onNodeWithText("Eliminar Saludos").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Editar Saludos").performScrollTo().assertIsDisplayed()
        org.junit.Assert.assertTrue(compose.onAllNodesWithText("4 expresiones · Incorporada").fetchSemanticsNodes().isNotEmpty())
    }
}
