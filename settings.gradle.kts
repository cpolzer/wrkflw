rootProject.name = "wrkflw"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

include("domain")
include("application")
include("adapters:persistence-postgres")
include("adapters:temporal")
include("adapters:rest-api")
include("adapters:eventing-cloudevents")
include("apps:api-service")
include("apps:worker-service")
includeBuild("build-logic")
