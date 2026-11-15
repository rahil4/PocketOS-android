package app.pocketos.board.data.repo

/** مقصد ثبت: هر دو null = صندوق ورودی؛ فقط customerId = «کلیِ مشتری»؛ caseId = پرونده (مشتری از روی پرونده پر می‌شود). */
data class Target(val customerId: Long? = null, val caseId: Long? = null) {
    val isInbox: Boolean get() = customerId == null && caseId == null

    companion object {
        val Inbox = Target()
    }
}
