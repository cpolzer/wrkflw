import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    base
    jacoco
    id("org.jetbrains.dokka") version "2.0.0"
}

group = "dev.wrkflw"
version = "0.1.0-SNAPSHOT"

tasks.register<JacocoReport>("jacocoRootReport") {
    group = "verification"
    description = "Aggregates JaCoCo coverage reports from all submodules"

    executionData.setFrom(
        fileTree(rootDir) {
            include("**/build/jacoco/*.exec")
        }
    )

    sourceDirectories.setFrom(
        subprojects.map { it.file("src/main/kotlin") }.filter { it.exists() }
    )

    classDirectories.setFrom(
        subprojects.map { proj ->
            proj.fileTree("${proj.buildDir}/classes/kotlin/main") {
                exclude("**/*\$\$serializer.class")
            }
        }
    )

    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/root/jacocoRootReport.xml"))
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/root/html"))
    }
}

tasks.register("checkAll") {
    description = "Run build and tests across all modules"
    group = "verification"
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
    subprojects.forEach { sub ->
        sub.tasks.findByName("check")?.let { dependsOn(it) }
    }
}
