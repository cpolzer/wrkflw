plugins {
    id("kotlin-jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

dependencies {
    implementation("io.ktor:ktor-server-core:3.0.1")
    implementation("io.ktor:ktor-server-netty:3.0.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-server-call-logging:3.0.1")
    implementation("io.ktor:ktor-server-status-pages:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.12")
}

application {
    mainClass.set("dev.wrkflw.ApplicationKt")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
