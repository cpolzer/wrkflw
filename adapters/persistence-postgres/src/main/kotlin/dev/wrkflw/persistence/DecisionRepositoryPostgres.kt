package dev.wrkflw.persistence

import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.port.DecisionRepository
import dev.wrkflw.domain.task.Decision
import dev.wrkflw.domain.task.DecisionOutcome
import dev.wrkflw.persistence.generated.tables.references.DECISION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import java.time.ZoneOffset

class DecisionRepositoryPostgres(private val dsl: DSLContext) : DecisionRepository {

    override suspend fun save(decision: Decision) {
        withContext(Dispatchers.IO) {
            dsl.insertInto(DECISION)
                .set(DECISION.TASK_ID, decision.taskId.value)
                .set(DECISION.OUTCOME, decision.outcome.name)
                .set(DECISION.ACTOR_ID, decision.actorId.value)
                .set(DECISION.COMMENT, decision.comment)
                .set(DECISION.DECIDED_AT, decision.decidedAt.atOffset(ZoneOffset.UTC))
                .execute()
        }
    }

    override suspend fun findByTaskId(taskId: TaskId): Decision? =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(DECISION)
                .where(DECISION.TASK_ID.eq(taskId.value))
                .fetchOne()
                ?.let { r ->
                    Decision(
                        taskId = TaskId(r.taskId!!),
                        outcome = DecisionOutcome.valueOf(r.outcome!!),
                        actorId = ActorId(r.actorId!!),
                        comment = r.comment,
                        decidedAt = r.decidedAt!!.toInstant(),
                    )
                }
        }
}
