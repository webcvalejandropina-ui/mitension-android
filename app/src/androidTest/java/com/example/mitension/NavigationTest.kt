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
    @Test fun bottomMenuShowsIPhoneDashboardAndMedicalPeriodFilters() {
        val context = compose.activity
        compose.onNodeWithText(context.t("ÚLTIMA TOMA")).assertExists()
        compose.onNodeWithText(context.t("MEDIA 7 DÍAS")).assertExists()
        compose.onNodeWithText(context.t("Médico")).performClick()
        compose.onNodeWithText(context.t("Vista médica")).assertExists()
        compose.onNodeWithText(context.t("90 días")).performClick()
        compose.onNodeWithText(context.t("Promedio")).assertExists()
        compose.onNodeWithText(context.t("Histórico")).performClick()
        // Pager may retain the adjacent summary's filter in its composition.
        compose.onAllNodesWithText(context.t("7 días")).onFirst().assertExists()
    }
    @Test fun watchBetaDoesNotReplaceManualEntry() {
        val context = compose.activity
        compose.onNodeWithContentDescription(context.t("Más")).performClick()
        compose.onNodeWithText(context.getString(R.string.wear_beta_badge)).performClick()
        compose.onNodeWithText(context.getString(R.string.wear_no_estimate)).assertExists()
        compose.onNodeWithContentDescription(context.t("Cerrar")).performClick()
        compose.onNodeWithText(context.t("Guardar nueva toma")).performClick()
        compose.onNodeWithText(context.t("Sistólica")).assertExists()
    }
}
