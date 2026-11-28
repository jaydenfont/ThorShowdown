package com.example.thorshowdown.tools.qa

import kotlinx.serialization.Serializable

/** An agent's judgement of one screenshot (or of a scenario's logs). */
@Serializable
enum class Verdict { Pass, Fail, NeedsHuman }

@Serializable
data class ScreenReview(
    val verdict: Verdict,
    val reason: String,
)

@Serializable
data class CheckpointReview(
    val top: ScreenReview? = null,
    val bottom: ScreenReview? = null,
)

@Serializable
data class ScenarioReview(
    val checkpoints: Map<String, CheckpointReview> = emptyMap(),
    val logs: ScreenReview? = null,
    val notes: String? = null,
)

/**
 * `review.json`, written by the QA skill next to `report.json`. Keys are scenario and checkpoint
 * names; anything missing shows as "Not reviewed".
 */
@Serializable
data class Review(
    val reviewer: String,
    val summary: String,
    val scenarios: Map<String, ScenarioReview> = emptyMap(),
)
