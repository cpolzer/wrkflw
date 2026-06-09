package dev.wrkflw.domain.port

import dev.wrkflw.domain.flow.FlowInstance
import dev.wrkflw.domain.identity.FlowInstanceId

interface FlowInstanceRepository {
    suspend fun findById(id: FlowInstanceId): FlowInstance?
    suspend fun save(instance: FlowInstance)
    suspend fun update(instance: FlowInstance)
}
