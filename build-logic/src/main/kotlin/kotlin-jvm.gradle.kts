import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.dokka")
}

kotlin {
    jvmToolchain(21)
}

ktlint {
    version.set("1.4.1")
    filter {
        exclude { element -> element.file.path.contains("/build/") }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

// KotlinAdapter fails to resolve KotlinProjectExtension across the composite-build
// classloader boundary (build-logic included build). Configure source roots explicitly.
tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        register("main") {
            sourceRoots.from(file("src/main/kotlin"))
        }
    }
}
