package com.example.thorshowdown.qa

/**
 * Fake identity used by every workflow test. The secrets are distinctive so the QA runner can
 * search logcat for them: any hit means a secret leaked into the logs.
 */
object Canaries {
    const val USERNAME = "qa-player"
    const val PASSWORD = "qa-canary-password-7f3a9c"
    const val ASSERTION = "qa-canary-assertion-51d2e8"
    const val SID = "qa-canary-sid-c04b16"
}
