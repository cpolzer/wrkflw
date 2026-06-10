package dev.wrkflw

import dev.wrkflw.application.command.ClaimTaskUseCase
import dev.wrkflw.application.command.ReleaseTaskUseCase
import dev.wrkflw.application.command.SubmitDecisionUseCase
import dev.wrkflw.application.command.SubmitDocumentUseCase
import dev.wrkflw.application.query.FlowStatusUseCase
import dev.wrkflw.application.query.GroupWorkListUseCase
import dev.wrkflw.application.query.MyTasksUseCase
import dev.wrkflw.domain.port.TaskRepository
import dev.wrkflw.rest.flowRoutes
import dev.wrkflw.rest.queryRoutes
import dev.wrkflw.rest.taskRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.postgresql.ds.PGSimpleDataSource

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    val dataSource =
        PGSimpleDataSource().apply {
            serverNames = arrayOf(System.getenv("DB_HOST") ?: "localhost")
            portNumbers = intArrayOf(System.getenv("DB_PORT")?.toIntOrNull() ?: 5432)
            databaseName = System.getenv("DB_NAME") ?: "wrkflw"
            user = System.getenv("DB_USER") ?: "wrkflw"
            password = System.getenv("DB_PASSWORD") ?: "wrkflw"
        }

    val temporalHost = System.getenv("TEMPORAL_HOST") ?: "localhost"
    val temporalPort = System.getenv("TEMPORAL_PORT")?.toIntOrNull() ?: 7233
    val taskQueue = "wrkflw-task-queue"

    install(Koin) {
        modules(infraModule(dataSource, temporalHost, temporalPort, taskQueue))
    }

    install(CallLogging)

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                prettyPrint = false
            },
        )
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Internal server error")),
            )
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
        }
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respond(HttpStatusCode.MethodNotAllowed, mapOf("error" to "Method not allowed"))
        }
    }

    val submitDocument: SubmitDocumentUseCase by inject()
    val taskRepository: TaskRepository by inject()
    val claimTask: ClaimTaskUseCase by inject()
    val releaseTask: ReleaseTaskUseCase by inject()
    val submitDecision: SubmitDecisionUseCase by inject()
    val groupWorkList: GroupWorkListUseCase by inject()
    val myTasks: MyTasksUseCase by inject()
    val flowStatus: FlowStatusUseCase by inject()

    routing {
        route("/api/v1") {
            flowRoutes(submitDocument, taskRepository)
            taskRoutes(claimTask, releaseTask, submitDecision)
            queryRoutes(groupWorkList, myTasks, flowStatus)
        }
    }
}
