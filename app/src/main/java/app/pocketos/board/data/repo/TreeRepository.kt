package app.pocketos.board.data.repo

import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.BranchRow
import app.pocketos.board.domain.Growth
import app.pocketos.board.domain.Period
import app.pocketos.board.domain.Span
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** شمارندهٔ یک دوره: انجام‌شده تا امروز و همان مقدار در دورهٔ قبل تا «همان روز». */
data class Count(val cur: Int, val prev: Int)

data class TreeSummary(
    val branches: List<BranchRow>,
    val total: Int,
    /** هفته: انجام‌شده از (انجام‌شده + مانده با مهلت در همین هفته). */
    val week: Count,
    val weekPlanned: Int,
    val month: Count,
    val season: Count,
    val seasonName: String,
) {
    val fruits: Int get() = branches.sumOf { it.fruits }
}

/** درخت: هر متقاضی یک شاخه، هر کار انجام‌شده یک برگ، هر پروندهٔ نهایی یک میوه. */
@Singleton
class TreeRepository @Inject constructor(private val db: BoardDatabase, private val clock: Clock) {
    private val dao = db.treeDao()

    private fun ms(d: LocalDate) = d.atStartOfDay(clock.zone).toInstant().toEpochMilli()
    private fun from(s: Span) = ms(s.from)
    private fun to(s: Span) = ms(s.to.plusDays(1))

    private fun done(s: Span) = dao.observeDone(from(s), to(s))

    private fun count(p: Period): Flow<Count> =
        combine(done(p.toDate), done(p.prevToDate)) { cur, prev -> Count(cur, prev) }

    fun observe(today: LocalDate): Flow<TreeSummary> {
        val w = Growth.week(today)
        val pieces: Flow<Triple<Count, Count, Count>> =
            combine(count(w), count(Growth.month(today)), count(Growth.season(today))) { a, b, c -> Triple(a, b, c) }
        val extra = combine(
            dao.observeBranches(from(w.whole), to(w.whole)),
            dao.observeDone(0, Long.MAX_VALUE),
            dao.observeOpenDue(w.whole.from.toString(), w.whole.to.toString()),
        ) { branches, total, open -> Triple(branches, total, open) }
        return combine(pieces, extra) { (week, month, season), (branches, total, open) ->
            TreeSummary(branches, total, week, week.cur + open, month, season, Growth.seasonName(today))
        }
    }
}
