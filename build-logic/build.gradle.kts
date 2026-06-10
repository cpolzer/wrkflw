plugins {
    `kotlin-dsl`
}

group = "dev.wrkflw.buildlogic"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.0.21")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.2")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.0.0")
}
