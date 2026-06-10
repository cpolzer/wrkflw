package dev.wrkflw.application.query

import dev.wrkflw.domain.flow.FlowInstance
import dev.wrkflw.domain.port.ActorContext
import dev.wrkflw.domain.port.FlowInstanceRepository

data class SubmitterFlowsQuery(
    val actor: ActorContext,
)

data class SubmitterFlowsResult(
    val flows: List<FlowInstance>,
)

fun interface SubmitterFlowsUseCase {
    suspend fun execute(query: SubmitterFlowsQuery): SubmitterFlowsResult
}

class SubmitterFlowsService(
    private val instances: FlowInstanceRepository,
) : SubmitterFlowsUseCase {
    override suspend fun execute(query: SubmitterFlowsQuery): SubmitterFlowsResult {
        val flows = instances.findBySubmitterId(query.actor.actorId.value)
        return SubmitterFlowsResult(flows)
    }
}
