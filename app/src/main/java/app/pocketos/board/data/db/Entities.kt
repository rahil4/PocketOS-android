package app.pocketos.board.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * ساختار داده (هم‌خوان با وب‌اپ). زمان‌ها: میلی‌ثانیهٔ epoch. روزها: متن ISO (YYYY-MM-DD).
 * حذف همیشه نرم است (deletedAt)؛ هر کوئری تازه باید deletedAt IS NULL را بسنجد.
 * searchText: متن نرمال‌شده برای جست‌وجو؛ هر مسیر نوشتن باید آن را به‌روز کند.
 */

@Entity(tableName = "customers", indices = [Index("deletedAt"), Index("archivedAt")])
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val pinnedAt: Long? = null,
    val archivedAt: Long? = null,
    val searchText: String = "",
    val createdAt: Long,
    val lastActivity: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "cases", indices = [Index("customerId"), Index("deletedAt")])
data class CaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val title: String,
    /** new | doing | waiting | final | archive */
    val status: String = "new",
    val dueDay: String? = null,
    val searchText: String = "",
    val createdAt: Long,
    val lastActivity: Long,
    val deletedAt: Long? = null,
)

/** یادداشت، عکس، صدا، فایل، موقعیت، رویداد. بدون مشتری و پرونده = صندوق ورودی. */
@Entity(
    tableName = "items",
    indices = [Index("customerId"), Index("caseId"), Index("deletedAt"), Index("clientId", unique = true)],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** text | photo | audio | file | place | event */
    val kind: String,
    val customerId: Long? = null,
    val caseId: Long? = null,
    val text: String = "",
    val fileName: String? = null,
    val filePath: String? = null,
    val mime: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val pinnedAt: Long? = null,
    val searchText: String = "",
    val createdAt: Long,
    val deletedAt: Long? = null,
    /** برای جلوگیری از ثبت تکراری (مثلاً «کار انجام شد»). */
    val clientId: String? = null,
)

@Entity(tableName = "tasks", indices = [Index("customerId"), Index("caseId"), Index("deletedAt"), Index("dueDay")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long? = null,
    val caseId: Long? = null,
    val title: String,
    val dueDay: String? = null,
    /** daily | weekly | monthly | every:N */
    val repeat: String? = null,
    /** روز برنامهٔ اصلیِ کار تکراری وقتی همین نوبت جابه‌جا شده. */
    val anchor: String? = null,
    val spawnedBy: Long? = null,
    val doneAt: Long? = null,
    val searchText: String = "",
    val createdAt: Long,
    val deletedAt: Long? = null,
)

/** جفت مشتری‌هایی که کاربر گفته «جدا هستند» (a < b). */
@Entity(tableName = "similar_no", primaryKeys = ["a", "b"])
data class SimilarNoEntity(val a: Long, val b: Long)

/** تنظیمات ساده: کلید ← مقدار. */
@Entity(tableName = "prefs")
data class PrefEntity(@PrimaryKey val key: String, val value: String)
