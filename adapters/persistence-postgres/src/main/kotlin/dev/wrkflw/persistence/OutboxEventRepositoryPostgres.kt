package dev.wrkflw.persistence

import dev.wrkflw.domain.event.OutboxEvent
import dev.wrkflw.domain.event.OutboxStatus
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.port.OutboxEventRepository
import dev.wrkflw.persistence.generated.tables.references.OUTBOX_EVENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.JSONB
import java.time.ZoneOffset
import java.util.UUID

class OutboxEventRepositoryPostgres(
    private val dsl: DSLContext,
) : OutboxEventRepository {
    override suspend fun save(event: OutboxEvent) {
        withContext(Dispatchers.IO) {
            dsl
                .insertInto(OUTBOX_EVENT)
                .set(OUTBOX_EVENT.ID, event.id)
                .set(OUTBOX_EVENT.FLOW_INSTANCE_ID, event.flowInstanceId.value)
                .set(OUTBOX_EVENT.TYPE, event.type)
                .set(OUTBOX_EVENT.DATA, JSONB.jsonb(event.data))
                .set(OUTBOX_EVENT.OCCURRED_AT, event.occurredAt.atOffset(ZoneOffset.UTC))
                .set(OUTBOX_EVENT.STATUS, event.status.name)
                .execute()
        }
    }

    override suspend fun findPending(limit: Int): List<OutboxEvent> =
        withContext(Dispatchers.IO) {
            dsl
                .selectFrom(OUTBOX_EVENT)
                .where(OUTBOX_EVENT.STATUS.eq(OutboxStatus.PENDING.name))
                .orderBy(OUTBOX_EVENT.OCCURRED_AT.asc())
                .limit(limit)
                .fetch()
                .map { r ->
                    OutboxEvent(
                        id = r.id!!,
                        flowInstanceId = FlowInstanceId(r.flowInstanceId!!),
                        type = r.type!!,
                        data = r.data!!.data(),
                        occurredAt = r.occurredAt!!.toInstant(),
                        status = OutboxStatus.valueOf(r.status!!),
                    )
                }
        }

    override suspend fun markDispatched(id: UUID): Int =
        withContext(Dispatchers.IO) {
            dsl
                .update(OUTBOX_EVENT)
                .set(OUTBOX_EVENT.STATUS, OutboxStatus.DISPATCHED.name)
                .set(
                    OUTBOX_EVENT.DISPATCHED_AT,
                    java.time.Instant
                        .now()
                        .atOffset(ZoneOffset.UTC),
                ).where(OUTBOX_EVENT.ID.eq(id))
                .and(OUTBOX_EVENT.STATUS.eq(OutboxStatus.PENDING.name))
                .execute()
        }
}
