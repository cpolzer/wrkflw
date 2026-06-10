package dev.wrkflw.domain.port

import dev.wrkflw.domain.audit.AuditEntry
import dev.wrkflw.domain.identity.FlowInstanceId

interface AuditLog {
    suspend fun append(entry: AuditEntry)

    suspend fun findByFlowInstanceId(flowInstanceId: FlowInstanceId): List<AuditEntry>
}
