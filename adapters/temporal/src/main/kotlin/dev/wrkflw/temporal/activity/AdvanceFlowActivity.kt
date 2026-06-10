package dev.wrkflw.temporal.activity

import dev.wrkflw.domain.flow.FlowStatus
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.port.FlowInstanceRepository
import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod
import kotlinx.coroutines.runBlocking
import java.util.UUID

@ActivityInterface
interface AdvanceFlowActivity {
    /** Returns true if the flow has reached a terminal state after this signal. */
    @ActivityMethod
    fun advanceState(
        flowInstanceId: String,
        outcome: String,
    ): Boolean
}

class AdvanceFlowActivityImpl(
    private val instances: FlowInstanceRepository,
) : AdvanceFlowActivity {
    override fun advanceState(
        flowInstanceId: String,
        outcome: String,
    ): Boolean =
        runBlocking {
            val id = FlowInstanceId(UUID.fromString(flowInstanceId))
            val instance =
                instances.findById(id)
                    ?: error("FlowInstance not found: $flowInstanceId")
            instance.status == FlowStatus.COMPLETED
        }
}
