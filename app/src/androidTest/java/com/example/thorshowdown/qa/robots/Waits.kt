package com.example.thorshowdown.qa.robots

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule

/** A cold start on the emulator can take ~10 s before the first frame (Keystore, WebView). */
private const val SCREEN_TIMEOUT_MILLIS = 20_000L

/** Waits until a node tagged [tag] exists on either screen. */
@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.awaitTag(tag: String) {
    waitUntilAtLeastOneExists(hasTestTag(tag), SCREEN_TIMEOUT_MILLIS)
}
