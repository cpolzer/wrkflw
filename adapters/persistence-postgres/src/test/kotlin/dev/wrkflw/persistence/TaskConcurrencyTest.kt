package dev.wrkflw.persistence

import dev.wrkflw.domain.identity.ActorId
import dev.wrkflw.domain.identity.FlowInstanceId
import dev.wrkflw.domain.identity.GroupId
import dev.wrkflw.domain.identity.TaskId
import dev.wrkflw.domain.task.Task
import dev.wrkflw.domain.task.TaskStatus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@Testcontainers
class TaskConcurrencyTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("wrkflw_test")
            .withUsername("wrkflw")
            .withPassword("wrkflw")
    }

    private lateinit var repo: TaskRepositoryPostgres

    @BeforeEach
    fun setUp() {
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("filesystem:src/main/resources/db/migration")
            .load()
            .migrate()

        val ds = PGSimpleDataSource().apply {
            setURL(postgres.jdbcUrl)
            user = postgres.username
            password = postgres.password
        }
        repo = TaskRepositoryPostgres(JooqDslContextProvider(ds).create())
    }

    @AfterEach
    fun tearDown() {
        val ds = PGSimpleDataSource().apply {
            setURL(postgres.jdbcUrl)
            user = postgres.username
            password = postgres.password
        }
        ds.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("TRUNCATE task, flow_instance RESTART IDENTITY CASCADE")
            }
        }
    }

    @Test
    fun `two simultaneous claims on the same task result in exactly one effective claim (SC-004)`() {
        val flowInstanceId = FlowInstanceId(UUID.randomUUID())
        val taskId = TaskId(UUID.randomUUID())
        val now = Instant.now()

        // Seed a minimal flow_instance row so the FK constraint is satisfied
        val ds = PGSimpleDataSource().apply {
            setURL(postgres.jdbcUrl)
            user = postgres.username
            password = postgres.password
        }
        ds.connection.use { conn ->
            conn.createStatement().use { stmt ->
                // Insert a minimal flow_instance (no FK to flow_definition needed for task test)
                stmt.execute(
                    """
                    INSERT INTO flow_instance
                        (id, definition_id, definition_key, definition_version, document_ref,
                         submitter_id, current_state, status, created_at, updated_at)
                    SELECT
                        '${flowInstanceId.value}'::uuid,
                        id,
                        key,
                        version,
                        'doc-concurrency',
                        'author1',
                        'Submitted',
                        'RUNNING',
                        now(), now()
                    FROM flow_definition
                    LIMIT 1
                    """.trimIndent()
                )
            }
        }

        val task = Task(
            id = taskId,
            flowInstanceId = flowInstanceId,
            stateName = "Submitted",
            candidateGroupId = GroupId("reviewers"),
            status = TaskStatus.PENDING,
            version = 0,
            createdAt = now,
        )

        runBlocking { repo.save(task) }

        // Two concurrent claim attempts
        val results = runBlocking {
            listOf(
                async(Dispatchers.IO) {
                    val t = repo.findById(taskId)!!
                    val claimed = t.claim(ActorId("reviewer1"), now)
                    repo.updateConditional(claimed, TaskStatus.PENDING, t.version)
                },
                async(Dispatchers.IO) {
                    val t = repo.findById(taskId)!!
                    val claimed = t.claim(ActorId("reviewer2"), now)
                    repo.updateConditional(claimed, TaskStatus.PENDING, t.version)
                }
            ).awaitAll()
        }

        val successCount = results.count { it == 1 }
        val failCount = results.count { it == 0 }

        successCount shouldBe 1
        failCount shouldBe 1

        // Exactly one owner was set
        val finalTask = runBlocking { repo.findById(taskId)!! }
        finalTask.status shouldBe TaskStatus.CLAIMED
        finalTask.ownerId shouldBe (if (results[0] == 1) ActorId("reviewer1") else ActorId("reviewer2"))
    }
}
