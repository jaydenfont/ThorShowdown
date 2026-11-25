package com.example.thorshowdown.tools.qa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportRendererTest {
    @Test
    fun ReportRenderer_NoReview_IsCompleteDocumentWithVisualChecksNotReviewed() {
        val rendered = ReportRenderer.render(REPORT, review = null)

        assertTrue(rendered.document.startsWith("<!doctype html>"))
        assertTrue(rendered.document.contains("<title>"))
        assertTrue(rendered.document.trimEnd().endsWith("</html>"))
        assertTrue(rendered.document.contains("Not reviewed"))
    }

    @Test
    fun ReportRenderer_PageVariant_HasNoDocumentSkeleton() {
        val rendered = ReportRenderer.render(REPORT, review = null)

        assertFalse(rendered.page.contains("<html"))
        assertFalse(rendered.page.contains("<body"))
        assertTrue(rendered.page.contains("<title>"))
        assertTrue(rendered.page.contains(LOGIN))
    }

    @Test
    fun ReportRenderer_Summary_CountsEachStatus() {
        val page = ReportRenderer.render(REPORT, review = null).page

        assertTrue(page.contains("1 passed"))
        assertTrue(page.contains("1 failed"))
    }

    @Test
    fun ReportRenderer_WithReview_ShowsVerdictsReasonsAndSummary() {
        val page = ReportRenderer.render(REPORT, REVIEW).page

        assertTrue(page.contains("Bottom tiles overlap"))
        assertTrue(page.contains("Battle menu clipped on the right"))
        assertTrue(page.contains("verdict-fail"))
        assertTrue(page.contains("Reviewed by qa-skill"))
    }

    @Test
    fun ReportRenderer_UntrustedText_IsEscaped() {
        val page = ReportRenderer.render(REPORT, review = null).page

        assertFalse(page.contains("<script>alert(1)</script>"))
        assertTrue(page.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
    }

    @Test
    fun ReportRenderer_MissingScreenshot_SaysSo() {
        val page = ReportRenderer.render(REPORT, review = null).page

        assertTrue(page.contains("Screenshot missing"))
    }

    @Test
    fun ReportRenderer_SpecTextAndProblems_AreShown() {
        val page = ReportRenderer.render(REPORT, review = null).page

        assertTrue(page.contains("Four move tiles with PP"))
        assertTrue(page.contains("Spec checkpoint Forced was never reached"))
    }

    @Test
    fun ReportRenderer_ScreenshotsAndArtifacts_LinkByRelativePath() {
        val page = ReportRenderer.render(REPORT, review = null).page

        assertTrue(page.contains("src=\"Login/checkpoints/01-FormShown-top.png\""))
        assertTrue(page.contains("href=\"Login/logcat.txt\""))
    }

    private companion object {
        const val LOGIN = "Login"
        const val BATTLE = "BattleTurn"
        const val NO_FINDINGS_ERRORS = "E OpenGLRenderer: swap"

        val REPORT =
            RunReport(
                runId = "20261125-090000",
                startedAt = "2026-11-25T09:00:00",
                commit = "2764fac",
                device = "emulator-5554",
                scenarios =
                    listOf(
                        ScenarioReport(
                            name = LOGIN,
                            status = ScenarioStatus.Passed,
                            durationMillis = 12_400,
                            failure = null,
                            checkpoints =
                                listOf(
                                    CheckpointCapture(
                                        1,
                                        "FormShown",
                                        "checkpoints/01-FormShown-top.png",
                                        "checkpoints/01-FormShown-bottom.png",
                                    ),
                                ),
                            logs = LogFindings(emptyList(), listOf(NO_FINDINGS_ERRORS)),
                            specProblems = emptyList(),
                            spec = SpecSnapshot("Logs in.", mapOf("FormShown" to "- Bottom: the form.")),
                            artifacts = ScenarioArtifacts("top.mp4", "bottom.webm", "logcat.txt", "instrument.txt"),
                        ),
                        ScenarioReport(
                            name = BATTLE,
                            status = ScenarioStatus.Failed,
                            durationMillis = 30_100,
                            failure = "<script>alert(1)</script>",
                            checkpoints = listOf(CheckpointCapture(1, "MovesShown", "checkpoints/01-MovesShown-top.png", bottom = null)),
                            logs =
                                LogFindings(
                                    listOf(LogFailure(LogFailureKind.Crash, "E AndroidRuntime: FATAL EXCEPTION: main")),
                                    emptyList(),
                                ),
                            specProblems = listOf("Spec checkpoint Forced was never reached"),
                            spec = SpecSnapshot("A turn.", mapOf("MovesShown" to "- Bottom: Four move tiles with PP.", "Forced" to "- x")),
                            artifacts = ScenarioArtifacts(null, null, "logcat.txt", "instrument.txt"),
                        ),
                    ),
            )

        val REVIEW =
            Review(
                reviewer = "qa-skill",
                summary = "Bottom tiles overlap in BattleTurn.",
                scenarios =
                    mapOf(
                        BATTLE to
                            ScenarioReview(
                                checkpoints =
                                    mapOf(
                                        "MovesShown" to
                                            CheckpointReview(
                                                top = ScreenReview(Verdict.Pass, "Status line shown."),
                                                bottom = ScreenReview(Verdict.Fail, "Battle menu clipped on the right"),
                                            ),
                                    ),
                            ),
                    ),
            )
    }
}
