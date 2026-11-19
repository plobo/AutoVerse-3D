package com.autoverse3d

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Critical journeys: main navigation, opening UrbanX, switching explorer modes, accessible lists. */
@HiltAndroidTest
class CoreJourneyTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Before fun init() = hilt.inject()

    private fun waitForText(text: String) = compose.waitUntil(20_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun mainNavigationShowsEveryTab() {
        waitForText("Explore in 3D")
        listOf("showroom" to "Coming soon", "explore" to "Mechanical explorer", "garage" to "Saved configurations", "learn" to "Electric vehicle fundamentals", "assistant" to "AI Assistant").forEach { (tab, text) ->
            compose.onNodeWithTag("nav_$tab").performClick()
            waitForText(text)
        }
    }

    @Test fun exploreVehicleAndUseAccessibleComponentList() {
        waitForText("Explore in 3D")
        compose.onNodeWithText("Explore in 3D").performClick()
        waitForText("Paint")
        compose.onNodeWithTag("mode_Mechanical").performClick()
        waitForText("Exploded view")
        compose.onNodeWithText("Explode: Full mechanical assembly").performClick()
        compose.onNodeWithText("Assemble full mechanical assembly").assertIsDisplayed()
        compose.onNodeWithText("Reset assembly").performClick()
        compose.onNodeWithTag("mode_Interior").performClick()
        waitForText("Cabin features")
        compose.onNodeWithText("Driver").assertIsDisplayed()
    }
}
