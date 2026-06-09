package dev.wrkflw.persistence

import dev.wrkflw.domain.audit.AuditEntry
import dev.wrkflw.domain.audit.AuditEventType
import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.port.AuditLog
import dev.wrkflw.persistence.generated.tables.references.AUDIT_ENTRY
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.jooq.DSLContext
import org.jooq.JSONB
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AuditLogPostgres(private val dsl: DSLContext) : AuditLog {

    override suspend fun append(entry: AuditEntry) {
        dsl.insertInto(AUDIT_ENTRY)
            .set(AUDIT_ENTRY.FLOW_INSTANCE_ID, entry.flowInstanceId.value)
            .set(AUDIT_ENTRY.TASK_ID, entry.taskId?.value)
            .set(AUDIT_ENTRY.TYPE, entry.type.name)
            .set(AUDIT_ENTRY.ACTOR_ID, entry.actorId?.value)
            .set(AUDIT_ENTRY.PAYLOAD, JSONB.jsonb(Json.encodeToString(entry.payload)))
            .set(AUDIT_ENTRY.OCCURRED_AT, entry.occurredAt.atOffset(ZoneOffset.UTC))
            .execute()
    }

    override suspend fun findByFlowInstanceId(flowInstanceId: FlowInstanceId): List<AuditEntry> =
        dsl.selectFrom(AUDIT_ENTRY)
            .where(AUDIT_ENTRY.FLOW_INSTANCE_ID.eq(flowInstanceId.value))
            .orderBy(AUDIT_ENTRY.ID.asc())
            .fetch()
            .map { r ->
                AuditEntry(
                    id = r.id!!,
                    flowInstanceId = FlowInstanceId(r.flowInstanceId!!),
                    taskId = r.taskId?.let { TaskId(it) },
                    type = AuditEventType.valueOf(r.type!!),
                    actorId = r.actorId?.let { ActorId(it) },
                    payload = Json.parseToJsonElement(r.payload?.data() ?: "{}").jsonObject,
                    occurredAt = r.occurredAt!!.toInstant(),
                )
            }

    private fun OffsetDateTime.toInstant() = this.toInstant()
}
