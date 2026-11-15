package app.pocketos.board.data

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** ساعت دستی تا زمان‌ها قابل کنترل باشند. */
class MutableClock(var now: Long) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)
    override fun millis(): Long = now

    companion object {
        fun at(date: LocalDate, hour: Int = 10) = MutableClock(date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
    }
}
