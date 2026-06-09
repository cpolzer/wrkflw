package dev.wrkflw.domain.port

import dev.wrkflw.domain.flow.FlowDefinition
import dev.wrkflw.domain.identity.FlowDefinitionKey

interface FlowDefinitionRepository {
    suspend fun findByKey(key: FlowDefinitionKey): FlowDefinition?
    suspend fun findByKeyAndVersion(key: FlowDefinitionKey, version: Int): FlowDefinition?
}
