plugins {
    id("kotlin-jvm")
    id("testing")
}

group = "dev.wrkflw"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(libs.temporal.sdk)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.temporal.testing)
}
