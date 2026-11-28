package com.example.thorshowdown.tools.qa

import kotlinx.serialization.Serializable

/** `report.json`: everything one `qaRun` produced. Paths are relative to the run folder. */
@Serializable
data class RunReport(
    val runId: String,
    val startedAt: String,
    val commit: String,
    val device: String,
    val scenarios: List<ScenarioReport>,
)

/** One scenario's outcome. Paths are relative to the scenario's folder. */
@Serializable
data class ScenarioReport(
    val name: String,
    val status: ScenarioStatus,
    val durationMillis: Long,
    val failure: String?,
    val checkpoints: List<CheckpointCapture>,
    val logs: LogFindings,
    val specProblems: List<String>,
    val spec: SpecSnapshot,
    val artifacts: ScenarioArtifacts,
)

/** The screenshots taken at one checkpoint; null where the capture failed. */
@Serializable
data class CheckpointCapture(
    val index: Int,
    val name: String,
    val top: String?,
    val bottom: String?,
)

/** The spec as it was when the scenario ran, so the report folder stands alone. */
@Serializable
data class SpecSnapshot(
    val summary: String,
    val checkpoints: Map<String, String>,
)

@Serializable
data class ScenarioArtifacts(
    val topVideo: String?,
    val bottomVideo: String?,
    val logcat: String,
    val instrument: String,
)
