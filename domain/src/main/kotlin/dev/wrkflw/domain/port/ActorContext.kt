package dev.wrkflw.domain.port

import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.GroupId

data class ActorContext(
    val actorId: ActorId,
    val groupIds: Set<GroupId>,
) {
    fun isInGroup(groupId: GroupId): Boolean = groupIds.contains(groupId)
}
