plugins {
    id("kotlin-jvm")
    id("testing")
    application
}

group = "dev.wrkflw"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":adapters:persistence-postgres"))
    implementation(project(":adapters:temporal"))
    implementation(libs.temporal.sdk)
    implementation(libs.koin.core)
    implementation(libs.postgresql)
    implementation(libs.jooq)
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
}

application {
    mainClass.set("dev.wrkflw.worker.ApplicationKt")
}
