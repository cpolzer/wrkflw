package dev.wrkflw.arch

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class BoundaryTest {

    private val coreClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("dev.wrkflw.domain", "dev.wrkflw.application")

    @Test
    fun `domain and application must not depend on framework types`() {
        noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                "io.ktor..",
                "io.temporal..",
                "org.jooq..",
                "java.sql..",
                "javax.sql..",
                "org.koin..",
                "org.postgresql..",
            )
            .check(coreClasses)
    }
}
