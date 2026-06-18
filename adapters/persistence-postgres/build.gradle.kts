buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.postgresql:postgresql:42.7.4")
        classpath("org.flywaydb:flyway-database-postgresql:10.20.1")
    }
}

plugins {
    id("kotlin-jvm")
    id("testing")
    id("org.flywaydb.flyway") version "10.20.1"
    id("nu.studer.jooq") version "9.0"
}

group = "dev.wrkflw"
version = "0.1.0-SNAPSHOT"

flyway {
    url = "jdbc:postgresql://localhost:5432/wrkflw"
    user = "wrkflw"
    password = "wrkflw"
    locations = arrayOf("filesystem:src/main/resources/db/migration")
    driver = "org.postgresql.Driver"
}

jooq {
    version.set("3.19.15")
    edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)

    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)
            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = "jdbc:postgresql://localhost:5432/wrkflw"
                    user = "wrkflw"
                    password = "wrkflw"
                }
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        includes = ".*"
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isImmutablePojos = true
                    }
                    target.apply {
                        packageName = "dev.wrkflw.persistence.generated"
                        directory = "build/generated-src/jooq/main"
                    }
                }
            }
        }
    }
}

sourceSets {
    main {
        kotlin.srcDir("build/generated-src/jooq/main")
    }
}

// Schema must exist before jOOQ can introspect it.
tasks.named("generateJooq") {
    dependsOn(tasks.named("flywayMigrate"))
}

dependencies {
    jooqGenerator(libs.postgresql)
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(libs.jooq)
    implementation(libs.jooq.kotlin)
    implementation(libs.jooq.kotlin.coroutines)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.postgresql)
}
