plugins {
    base
}

group = "dev.wrkflw"
version = "0.1.0-SNAPSHOT"

tasks.register("checkAll") {
    description = "Run build and tests across all modules"
    group = "verification"
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
    subprojects.forEach { dependsOn(it.tasks.named("check")) }
}
