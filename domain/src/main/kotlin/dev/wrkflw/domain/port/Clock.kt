package dev.wrkflw.domain.port

import java.time.Instant

interface Clock {
    fun now(): Instant
}

object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}
