package dev.wrkflw.domain.flow

import dev.wrkflw.domain.identity.FlowDefinitionKey
import dev.wrkflw.domain.identity.GroupId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FlowInterpreterMultiStageTest {
    private val definition =
        FlowDefinition(
            key = FlowDefinitionKey("doc-approval"),
            version = 1,
            initialState = "Submitted",
            initiatorGroupId = GroupId("authors"),
            states =
                listOf(
                    StateDefinition("Submitted", StateType.HUMAN_TASK, candidateGroupId = GroupId("reviewers")),
                    StateDefinition(
                        "FinalReview",
                        StateType.HUMAN_TASK,
                        candidateGroupId = GroupId("senior-reviewers"),
                    ),
                    StateDefinition("ReworkRequested", StateType.HUMAN_TASK, candidateGroupId = GroupId("authors")),
                    StateDefinition("Approved", StateType.TERMINAL, terminalOutcome = "APPROVED"),
                    StateDefinition("Rejected", StateType.TERMINAL, terminalOutcome = "REJECTED"),
                ),
            transitions =
                listOf(
                    TransitionDefinition("Submitted", Trigger.APPROVE, "FinalReview"),
                    TransitionDefinition("Submitted", Trigger.REJECT, "ReworkRequested"),
                    TransitionDefinition("FinalReview", Trigger.APPROVE, "Approved"),
                    TransitionDefinition("FinalReview", Trigger.REJECT, "ReworkRequested"),
                    TransitionDefinition("ReworkRequested", Trigger.SUBMIT, "Submitted"),
                ),
        )

    @Test
    fun `approve from Submitted advances to FinalReview (non-terminal)`() {
        val result = FlowInterpreterResult.advance("Submitted", Trigger.APPROVE, definition)
        result.isTerminal shouldBe false
        result.nextState shouldBe "FinalReview"
        result.terminalOutcome shouldBe null
    }

    @Test
    fun `approve from FinalReview advances to Approved terminal`() {
        val result = FlowInterpreterResult.advance("FinalReview", Trigger.APPROVE, definition)
        result.isTerminal shouldBe true
        result.nextState shouldBe null
        result.terminalOutcome shouldBe "APPROVED"
    }

    @Test
    fun `reject from Submitted goes to ReworkRequested (non-terminal)`() {
        val result = FlowInterpreterResult.advance("Submitted", Trigger.REJECT, definition)
        result.isTerminal shouldBe false
        result.nextState shouldBe "ReworkRequested"
        result.terminalOutcome shouldBe null
    }

    @Test
    fun `reject from FinalReview goes to ReworkRequested (non-terminal)`() {
        val result = FlowInterpreterResult.advance("FinalReview", Trigger.REJECT, definition)
        result.isTerminal shouldBe false
        result.nextState shouldBe "ReworkRequested"
        result.terminalOutcome shouldBe null
    }

    @Test
    fun `submit from ReworkRequested returns to Submitted (non-terminal)`() {
        val result = FlowInterpreterResult.advance("ReworkRequested", Trigger.SUBMIT, definition)
        result.isTerminal shouldBe false
        result.nextState shouldBe "Submitted"
        result.terminalOutcome shouldBe null
    }

    @Test
    fun `invalid trigger on state throws InvalidTransitionException`() {
        shouldThrow<InvalidTransitionException> {
            FlowInterpreterResult.advance("Submitted", Trigger.SUBMIT, definition)
        }
    }

    @Test
    fun `full two-stage approval path is navigable`() {
        val step1 = FlowInterpreterResult.advance("Submitted", Trigger.APPROVE, definition)
        step1.nextState shouldBe "FinalReview"

        val step2 = FlowInterpreterResult.advance(step1.nextState!!, Trigger.APPROVE, definition)
        step2.isTerminal shouldBe true
        step2.terminalOutcome shouldBe "APPROVED"
    }

    @Test
    fun `reject-rework-resubmit cycle is navigable`() {
        val reject = FlowInterpreterResult.advance("Submitted", Trigger.REJECT, definition)
        reject.nextState shouldBe "ReworkRequested"

        val resubmit = FlowInterpreterResult.advance(reject.nextState!!, Trigger.SUBMIT, definition)
        resubmit.nextState shouldBe "Submitted"
        resubmit.isTerminal shouldBe false
    }
}
