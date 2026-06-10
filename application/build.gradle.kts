plugins {
    id("kotlin-jvm")
    id("testing")
}

group = "dev.wrkflw"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(project(":domain"))
    testImplementation(libs.archunit.junit5)
}
