import java.io.File

plugins {
    id("kotlin-jvm")
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

// Proxy socket path for Docker 29.x+ compat (see scripts/docker-api-proxy.py).
// Evaluated at configuration time outside the task lambda to avoid receiver ambiguity.
val proxySocket = "/tmp/docker-proxy.sock"
val proxySocketExists = File(proxySocket).exists()

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    if (proxySocketExists) {
        environment("DOCKER_HOST", "unix://$proxySocket")
    }
}
