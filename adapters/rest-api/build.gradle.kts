plugins {
    id("kotlin-jvm")
    id("testing")
}

group = "dev.wrkflw"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.serialization.kotlinx.json)
}
