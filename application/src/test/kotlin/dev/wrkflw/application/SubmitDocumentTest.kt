package dev.wrkflw.application

import dev.wrkflw.application.command.SubmitDocumentCommand
import dev.wrkflw.application.command.SubmitDocumentResult
import dev.wrkflw.application.command.SubmitDocumentService
import dev.wrkflw.domain.audit.AuditEntry
import dev.wrkflw.domain.flow.FlowDefinition
import dev.wrkflw.domain.flow.FlowInstance
import dev.wrkflw.domain.flow.StateDefinition
import dev.wrkflw.domain.flow.StateType
import dev.wrkflw.domain.flow.TransitionDefinition
import dev.wrkflw.domain.flow.Trigger
import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.FlowDefinitionKey
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.identity.GroupId
import dev.wrkflw.domain.port.ActorContext
import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.domain.port.Clock
import dev.wrkflw.domain.port.FlowDefinitionRepository
import dev.wrkflw.domain.port.FlowInstanceRepository
import dev.wrkflw.domain.port.WorkflowEngine
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class SubmitDocumentTest {

    private val fixedNow = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = object : Clock { override fun now() = fixedNow }

    private val authorGroup = GroupId("authors")
    private val reviewerGroup = GroupId("reviewers")

    private val definition = FlowDefinition(
        key = FlowDefinitionKey("document-approval"),
        version = 1,
        initialState = "Submitted",
        initiatorGroupId = authorGroup,
        states = listOf(
            StateDefinition("Submitted", StateType.HUMAN_TASK, candidateGroupId = reviewerGroup),
            StateDefinition("Approved", StateType.TERMINAL, terminalOutcome = "APPROVED"),
        ),
        transitions = listOf(
            TransitionDefinition("Submitted", Trigger.APPROVE, "Approved"),
        ),
    )

    private var savedInstance: FlowInstance? = null
    private val appendedAudit = mutableListOf<AuditEntry>()
    private var startedWorkflowId: FlowInstanceId? = null

    private val fakeDefinitions = object : FlowDefinitionRepository {
        override suspend fun findByKey(key: FlowDefinitionKey) =
            definition.takeIf { it.key == key }

        override suspend fun findByKeyAndVersion(key: FlowDefinitionKey, version: Int) =
            definition.takeIf { it.key == key && it.version == version }
    }

    private val fakeInstances = object : FlowInstanceRepository {
        override suspend fun findById(id: FlowInstanceId) = savedInstance?.takeIf { it.id == id }
        override suspend fun save(instance: FlowInstance) { savedInstance = instance }
        override suspend fun update(instance: FlowInstance) { savedInstance = instance }
    }

    private val fakeAuditLog = object : AuditLog {
        override suspend fun append(entry: AuditEntry) { appendedAudit += entry }
        override suspend fun findByFlowInstanceId(flowInstanceId: FlowInstanceId) =
            appendedAudit.filter { it.flowInstanceId == flowInstanceId }
    }

    private val fakeEngine = object : WorkflowEngine {
        override suspend fun startWorkflow(flowInstanceId: FlowInstanceId, definitionKey: String) {
            startedWorkflowId = flowInstanceId
        }

        override suspend fun signalWorkflow(flowInstanceId: FlowInstanceId, signalName: String, data: Any?) {}
    }

    private val service = SubmitDocumentService(fakeDefinitions, fakeInstances, fakeEngine, fakeAuditLog, clock)

    @Test
    fun `returns DefinitionNotFound when definition does not exist`() = runTest {
        val result = service.execute(
            SubmitDocumentCommand("unknown-flow", "doc-1", ActorContext(ActorId("user1"), setOf(authorGroup)))
        )
        result shouldBe SubmitDocumentResult.DefinitionNotFound
    }

    @Test
    fun `returns Unauthorized when actor is not in initiator group`() = runTest {
        val result = service.execute(
            SubmitDocumentCommand("document-approval", "doc-1", ActorContext(ActorId("user1"), setOf(reviewerGroup)))
        )
        result shouldBe SubmitDocumentResult.Unauthorized
    }

    @Test
    fun `saves flow instance and starts workflow on success`() = runTest {
        val actor = ActorContext(ActorId("author1"), setOf(authorGroup))
        val result = service.execute(SubmitDocumentCommand("document-approval", "doc-ref-123", actor))

        result.shouldBeInstanceOf<SubmitDocumentResult.Success>()
        val instance = (result as SubmitDocumentResult.Success).instance
        instance.definitionKey.value shouldBe "document-approval"
        instance.documentRef shouldBe "doc-ref-123"
        instance.currentState shouldBe "Submitted"

        savedInstance shouldBe instance
        startedWorkflowId shouldBe instance.id
    }

    @Test
    fun `writes FLOW_STARTED audit entry on success`() = runTest {
        val actor = ActorContext(ActorId("author1"), setOf(authorGroup))
        service.execute(SubmitDocumentCommand("document-approval", "doc-ref-456", actor))

        appendedAudit.size shouldBe 1
        appendedAudit[0].type.name shouldBe "FLOW_STARTED"
        appendedAudit[0].actorId shouldBe ActorId("author1")
        appendedAudit[0].occurredAt shouldBe fixedNow
    }
}
