plugins {
    id("kotlin-jvm")
    id("testing")
}

group = "dev.wrkflw"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(libs.cloudevents.core)
    implementation(libs.cloudevents.api)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotest.assertions.core)
}
