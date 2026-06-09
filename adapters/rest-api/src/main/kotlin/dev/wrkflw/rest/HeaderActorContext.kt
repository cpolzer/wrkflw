package dev.wrkflw.rest

import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.GroupId
import dev.wrkflw.domain.port.ActorContext
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*

object HeaderActorContext {
    fun fromCall(call: ApplicationCall): ActorContext {
        val actorId = call.request.headers["X-Actor-Id"]
            ?: throw MissingActorHeaderException("X-Actor-Id header is required")

        val groupsHeader = call.request.headers["X-Actor-Groups"] ?: ""
        val groupIds = groupsHeader
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { GroupId(it) }
            .toSet()

        return ActorContext(ActorId(actorId), groupIds)
    }
}

class MissingActorHeaderException(message: String) : IllegalStateException(message)
