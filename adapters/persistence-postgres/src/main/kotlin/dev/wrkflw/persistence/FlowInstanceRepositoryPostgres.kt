package dev.wrkflw.persistence

import dev.wrkflw.domain.flow.FlowInstance
import dev.wrkflw.domain.flow.FlowStatus
import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.FlowDefinitionKey
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.port.FlowInstanceRepository
import dev.wrkflw.persistence.generated.tables.references.FLOW_DEFINITION
import dev.wrkflw.persistence.generated.tables.references.FLOW_INSTANCE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import java.time.ZoneOffset

class FlowInstanceRepositoryPostgres(
    private val dsl: DSLContext,
) : FlowInstanceRepository {
    override suspend fun findById(id: FlowInstanceId): FlowInstance? =
        withContext(Dispatchers.IO) {
            dsl
                .selectFrom(FLOW_INSTANCE)
                .where(FLOW_INSTANCE.ID.eq(id.value))
                .fetchOne()
                ?.let { r ->
                    FlowInstance(
                        id = FlowInstanceId(r.id!!),
                        definitionKey = FlowDefinitionKey(r.definitionKey!!),
                        definitionVersion = r.definitionVersion!!,
                        documentRef = r.documentRef!!,
                        submitterId = ActorId(r.submitterId!!),
                        currentState = r.currentState!!,
                        status = FlowStatus.valueOf(r.status!!),
                        terminalOutcome = r.terminalOutcome,
                        createdAt = r.createdAt!!.toInstant(),
                        updatedAt = r.updatedAt!!.toInstant(),
                    )
                }
        }

    override suspend fun findBySubmitterId(submitterId: String): List<FlowInstance> =
        withContext(Dispatchers.IO) {
            dsl
                .selectFrom(FLOW_INSTANCE)
                .where(FLOW_INSTANCE.SUBMITTER_ID.eq(submitterId))
                .orderBy(FLOW_INSTANCE.UPDATED_AT.desc())
                .fetch()
                .map { r ->
                    FlowInstance(
                        id = FlowInstanceId(r.id!!),
                        definitionKey = FlowDefinitionKey(r.definitionKey!!),
                        definitionVersion = r.definitionVersion!!,
                        documentRef = r.documentRef!!,
                        submitterId = ActorId(r.submitterId!!),
                        currentState = r.currentState!!,
                        status = FlowStatus.valueOf(r.status!!),
                        terminalOutcome = r.terminalOutcome,
                        createdAt = r.createdAt!!.toInstant(),
                        updatedAt = r.updatedAt!!.toInstant(),
                    )
                }
        }

    override suspend fun save(instance: FlowInstance) {
        withContext(Dispatchers.IO) {
            val definitionId =
                dsl
                    .select(FLOW_DEFINITION.ID)
                    .from(FLOW_DEFINITION)
                    .where(FLOW_DEFINITION.KEY.eq(instance.definitionKey.value))
                    .and(FLOW_DEFINITION.VERSION.eq(instance.definitionVersion))
                    .fetchOneInto(java.util.UUID::class.java)
                    ?: error("FlowDefinition not found: ${instance.definitionKey.value} v${instance.definitionVersion}")

            dsl
                .insertInto(FLOW_INSTANCE)
                .set(FLOW_INSTANCE.ID, instance.id.value)
                .set(FLOW_INSTANCE.DEFINITION_ID, definitionId)
                .set(FLOW_INSTANCE.DEFINITION_KEY, instance.definitionKey.value)
                .set(FLOW_INSTANCE.DEFINITION_VERSION, instance.definitionVersion)
                .set(FLOW_INSTANCE.DOCUMENT_REF, instance.documentRef)
                .set(FLOW_INSTANCE.SUBMITTER_ID, instance.submitterId.value)
                .set(FLOW_INSTANCE.CURRENT_STATE, instance.currentState)
                .set(FLOW_INSTANCE.STATUS, instance.status.name)
                .set(FLOW_INSTANCE.TERMINAL_OUTCOME, instance.terminalOutcome)
                .set(FLOW_INSTANCE.CREATED_AT, instance.createdAt.atOffset(ZoneOffset.UTC))
                .set(FLOW_INSTANCE.UPDATED_AT, instance.updatedAt.atOffset(ZoneOffset.UTC))
                .execute()
        }
    }

    override suspend fun update(instance: FlowInstance) {
        withContext(Dispatchers.IO) {
            dsl
                .update(FLOW_INSTANCE)
                .set(FLOW_INSTANCE.CURRENT_STATE, instance.currentState)
                .set(FLOW_INSTANCE.STATUS, instance.status.name)
                .set(FLOW_INSTANCE.TERMINAL_OUTCOME, instance.terminalOutcome)
                .set(FLOW_INSTANCE.UPDATED_AT, instance.updatedAt.atOffset(ZoneOffset.UTC))
                .where(FLOW_INSTANCE.ID.eq(instance.id.value))
                .execute()
        }
    }
}
