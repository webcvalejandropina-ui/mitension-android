package com.example.mitension

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Navigation checks never grant notification/alarm permission or save medical records. */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun moreShowsGuideAndImportFormat() {
        val context = compose.activity
        compose.onNodeWithContentDescription(context.t("Más")).performClick()
        compose.onNodeWithText(context.t("Guía y privacidad")).performClick()
        compose.onNodeWithText(context.t("1. Prepara el tensiómetro").replace("1. Prepara el tensiómetro", "1. ${context.t("Prepara el tensiómetro")}")).assertExists()
        compose.onNodeWithContentDescription(context.t("Cerrar")).performClick()
        compose.onNodeWithContentDescription(context.t("Más")).performClick()
        compose.onNodeWithText(context.t("Importar registros de Excel")).performClick()
        compose.onNodeWithText(context.t("Formato del archivo")).assertExists()
    }
    @Test fun readingFormHasOneOrThreeAndIndividualNumericFields() {
        val context = compose.activity
        compose.onNodeWithText(context.t("Guardar nueva toma")).performClick()
        compose.onNodeWithText("3 ${context.t("Mediciones")}").assertExists()
        compose.onNodeWithText(context.t("Sistólica")).assertExists()
        compose.onNodeWithText(context.t("Diastólica")).assertExists()
    }
}
