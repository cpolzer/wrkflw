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

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    // Use proxy socket when available (needed for Docker 29.x+ which dropped API < 1.40).
    // Falls back to Testcontainers default Docker detection when proxy is not running (e.g. CI).
    val proxySocket = "/tmp/docker-proxy.sock"
    if (java.io.File(proxySocket).exists()) {
        environment("DOCKER_HOST", "unix://$proxySocket")
    }
}
