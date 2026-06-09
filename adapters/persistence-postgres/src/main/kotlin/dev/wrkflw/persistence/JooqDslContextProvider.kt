package dev.wrkflw.persistence

import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.postgresql.ds.PGSimpleDataSource

class JooqDslContextProvider(private val dataSource: PGSimpleDataSource) {
    fun create(): DSLContext = DSL.using(dataSource, SQLDialect.POSTGRES)
}
