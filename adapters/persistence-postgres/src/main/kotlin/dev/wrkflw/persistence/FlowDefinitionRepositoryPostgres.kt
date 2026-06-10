package dev.wrkflw.persistence

import dev.wrkflw.domain.flow.FlowDefinition
import dev.wrkflw.domain.flow.StateDefinition
import dev.wrkflw.domain.flow.StateType
import dev.wrkflw.domain.flow.TransitionDefinition
import dev.wrkflw.domain.flow.Trigger
import dev.wrkflw.domain.identity.FlowDefinitionKey
import dev.wrkflw.domain.identity.GroupId
import dev.wrkflw.domain.port.FlowDefinitionRepository
import dev.wrkflw.persistence.generated.tables.records.FlowDefinitionRecord
import dev.wrkflw.persistence.generated.tables.references.FLOW_DEFINITION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jooq.DSLContext

class FlowDefinitionRepositoryPostgres(
    private val dsl: DSLContext,
) : FlowDefinitionRepository {
    override suspend fun findByKey(key: FlowDefinitionKey): FlowDefinition? =
        withContext(Dispatchers.IO) {
            dsl
                .selectFrom(FLOW_DEFINITION)
                .where(FLOW_DEFINITION.KEY.eq(key.value))
                .orderBy(FLOW_DEFINITION.VERSION.desc())
                .limit(1)
                .fetchOne()
                ?.toDomain()
        }

    override suspend fun findByKeyAndVersion(
        key: FlowDefinitionKey,
        version: Int,
    ): FlowDefinition? =
        withContext(Dispatchers.IO) {
            dsl
                .selectFrom(FLOW_DEFINITION)
                .where(FLOW_DEFINITION.KEY.eq(key.value))
                .and(FLOW_DEFINITION.VERSION.eq(version))
                .fetchOne()
                ?.toDomain()
        }

    private fun FlowDefinitionRecord.toDomain(): FlowDefinition {
        val states =
            Json.decodeFromString<List<StateJson>>(states!!.data()).map { s ->
                StateDefinition(
                    name = s.name,
                    type = StateType.valueOf(s.type),
                    candidateGroupId = s.candidateGroupId?.let { GroupId(it) },
                    terminalOutcome = s.terminalOutcome,
                )
            }
        val transitions =
            Json.decodeFromString<List<TransitionJson>>(transitions!!.data()).map { t ->
                TransitionDefinition(
                    from = t.from,
                    trigger = Trigger.valueOf(t.trigger),
                    to = t.to,
                    guard = t.guard,
                )
            }
        return FlowDefinition(
            key = FlowDefinitionKey(key!!),
            version = version!!,
            initialState = initialState!!,
            initiatorGroupId = GroupId(initiatorGroupId!!),
            states = states,
            transitions = transitions,
        )
    }

    @Serializable
    private data class StateJson(
        val name: String,
        val type: String,
        val candidateGroupId: String? = null,
        val terminalOutcome: String? = null,
    )

    @Serializable
    private data class TransitionJson(
        val from: String,
        val trigger: String,
        val to: String,
        val guard: String? = null,
    )
}
