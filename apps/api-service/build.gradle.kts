plugins {
    id("ktor-app")
    id("testing")
}

tasks.withType<Test> {
    systemProperty(
        "wrkflw.migrations.dir",
        rootProject.file("adapters/persistence-postgres/src/main/resources/db/migration").absolutePath,
    )
}

group = "dev.wrkflw"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":adapters:persistence-postgres"))
    implementation(project(":adapters:rest-api"))
    implementation(project(":adapters:eventing-cloudevents"))
    implementation(project(":adapters:temporal"))
    implementation(libs.temporal.sdk)
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.postgresql)
    implementation(libs.jooq)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.temporal.testing)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.database.postgresql)
}
