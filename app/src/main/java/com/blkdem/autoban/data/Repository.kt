package com.blkdem.autoban.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.CallInfo
import com.blkdem.autoban.rules.Decision
import com.blkdem.autoban.rules.ListEntry
import com.blkdem.autoban.rules.ListType
import com.blkdem.autoban.rules.Rule
import com.blkdem.autoban.rules.RuleMode
import com.blkdem.autoban.rules.Rules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Доступ к БД. Потокобезопасен (SQLiteOpenHelper сериализует запись). */
class Repository(context: Context) {

    private val helper = Db(context.applicationContext)
    private val db get() = helper.writableDatabase

    private val _changes = MutableStateFlow(0L)
    /** Увеличивается при любой записи — UI перечитывает данные. */
    val changes: StateFlow<Long> = _changes

    private fun changed() { _changes.value = _changes.value + 1 }

    // ---------- events ----------

    data class EventFilter(
        val actions: Set<Action> = emptySet(),
        val simSlot: Int? = null,
        val query: String = "",
        val sinceMillis: Long? = null,
        val onlyDryRun: Boolean = false,
        val limit: Int = 500,
    )

    fun insertEvent(e: CallEvent): Long = db.insert("events", null, ContentValues().apply {
        put("ts", e.timestamp)
        put("raw_number", e.rawNumber)
        put("number", e.number)
        put("presentation", e.presentation.name)
        put("caller_name", e.callerName)
        put("sim_slot", e.simSlot)
        put("sim_label", e.simLabel)
        put("action", e.action.name)
        put("source", e.source.name)
        put("rule_id", e.ruleId)
        put("rule_name", e.ruleName)
        put("dry_run", e.dryRun)
        put("would_action", e.wouldAction.name)
        put("tg_status", e.tgStatus.name)
        put("details", e.details)
    }).also { changed() }

    fun events(f: EventFilter = EventFilter()): List<CallEvent> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (f.actions.isNotEmpty()) where += "action IN (${f.actions.joinToString(",") { "'${it.name}'" }})"
        f.simSlot?.let { where += "sim_slot = ?"; args += it.toString() }
        f.sinceMillis?.let { where += "ts >= ?"; args += it.toString() }
        if (f.onlyDryRun) where += "dry_run IS NOT NULL"
        if (f.query.isNotBlank()) {
            where += "(number LIKE ? OR raw_number LIKE ? OR caller_name LIKE ? OR rule_name LIKE ?)"
            val q = "%${f.query.trim().filter { it != ' ' && it != '-' }}%"
            val qn = "%${f.query.trim()}%"
            args += listOf(q, q, qn, qn)
        }
        val sql = "SELECT * FROM events" +
            (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) +
            " ORDER BY ts DESC LIMIT ${f.limit}"
        return db.rawQuery(sql, args.toTypedArray()).use { c -> generateSequence { if (c.moveToNext()) c.toEvent() else null }.toList() }
    }

    fun event(id: Long): CallEvent? =
        db.rawQuery("SELECT * FROM events WHERE id = ?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.toEvent() else null }

    fun countCallsFrom(number: String, sinceMillis: Long): Int =
        db.rawQuery("SELECT COUNT(*) FROM events WHERE number = ? AND ts >= ?", arrayOf(number, sinceMillis.toString()))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun setTgStatus(ids: Collection<Long>, status: CallEvent.TgStatus) {
        if (ids.isEmpty()) return
        db.execSQL("UPDATE events SET tg_status = ? WHERE id IN (${ids.joinToString(",")})", arrayOf(status.name))
        changed()
    }

    /** Вернуть неотправленные в очередь. */
    fun retryFailedTg(): Int {
        val n = db.compileStatement("UPDATE events SET tg_status = 'PENDING' WHERE tg_status = 'FAILED'").executeUpdateDelete()
        if (n > 0) changed()
        return n
    }

    fun eventsWithTgStatus(status: CallEvent.TgStatus, limit: Int = 50): List<CallEvent> =
        db.rawQuery("SELECT * FROM events WHERE tg_status = ? ORDER BY ts LIMIT $limit", arrayOf(status.name))
            .use { c -> generateSequence { if (c.moveToNext()) c.toEvent() else null }.toList() }

    fun deleteEventsBefore(ts: Long): Int = db.delete("events", "ts < ?", arrayOf(ts.toString())).also { if (it > 0) changed() }

    fun clearEvents() { db.delete("events", null, null); changed() }

    private fun Cursor.str(n: String): String? = getColumnIndexOrThrow(n).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.long(n: String): Long? = getColumnIndexOrThrow(n).let { if (isNull(it)) null else getLong(it) }

    private fun Cursor.toEvent() = CallEvent(
        id = long("id")!!,
        timestamp = long("ts")!!,
        rawNumber = str("raw_number"),
        number = str("number"),
        presentation = CallInfo.Presentation.valueOf(str("presentation")!!),
        callerName = str("caller_name"),
        simSlot = long("sim_slot")?.toInt(),
        simLabel = str("sim_label"),
        action = Action.valueOf(str("action")!!),
        source = Decision.Source.valueOf(str("source")!!),
        ruleId = long("rule_id"),
        ruleName = str("rule_name"),
        dryRun = str("dry_run"),
        wouldAction = Action.valueOf(str("would_action")!!),
        tgStatus = CallEvent.TgStatus.valueOf(str("tg_status")!!),
        details = str("details"),
    )

    // ---------- rules ----------

    fun rules(): List<Rule> = db.rawQuery("SELECT * FROM rules ORDER BY priority, id", null).use { c ->
        generateSequence { if (c.moveToNext()) c.toRule() else null }.toList()
    }

    fun rule(id: Long): Rule? = rules().firstOrNull { it.id == id }

    fun saveRule(r: Rule): Long {
        val v = ContentValues().apply {
            put("name", r.name)
            put("enabled", if (r.enabled) 1 else 0)
            put("priority", r.priority)
            put("mode", r.mode.name)
            put("action", r.action.name)
            put("conditions", Rules.conditionsToJson(r.conditions))
        }
        val id = if (r.id == 0L) db.insert("rules", null, v) else { db.update("rules", v, "id = ?", arrayOf(r.id.toString())); r.id }
        changed()
        return id
    }

    fun deleteRule(id: Long) { db.delete("rules", "id = ?", arrayOf(id.toString())); changed() }

    /** Заменить все правила (импорт). */
    fun replaceRules(list: List<Rule>) {
        db.beginTransaction()
        try {
            db.delete("rules", null, null)
            list.forEach { saveRule(it.copy(id = 0)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    private fun Cursor.toRule() = Rule(
        id = long("id")!!,
        name = str("name")!!,
        enabled = long("enabled") == 1L,
        priority = long("priority")!!.toInt(),
        mode = RuleMode.valueOf(str("mode")!!),
        action = Action.valueOf(str("action")!!),
        conditions = runCatching { Rules.conditionsFromJson(str("conditions")!!) }.getOrDefault(emptyList()),
    )

    // ---------- lists ----------

    fun list(type: ListType): List<ListEntry> =
        db.rawQuery("SELECT * FROM lists WHERE type = ? ORDER BY created_at DESC", arrayOf(type.name)).use { c ->
            generateSequence {
                if (!c.moveToNext()) null else ListEntry(
                    id = c.long("id")!!,
                    type = ListType.valueOf(c.str("type")!!),
                    pattern = c.str("pattern")!!,
                    note = c.str("note"),
                    createdAt = c.long("created_at")!!,
                    ruleName = c.str("rule_name"),
                )
            }.toList()
        }

    /** Добавить запись; если такая уже есть — ничего не делать. */
    fun addToList(type: ListType, pattern: String, note: String? = null, ruleName: String? = null): Boolean {
        val id = db.insertWithOnConflict("lists", null, ContentValues().apply {
            put("type", type.name)
            put("pattern", pattern.trim())
            put("note", note)
            put("created_at", System.currentTimeMillis())
            put("rule_name", ruleName)
        }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
        if (id != -1L) changed()
        return id != -1L
    }

    fun removeFromList(id: Long) { db.delete("lists", "id = ?", arrayOf(id.toString())); changed() }

    /** Удалить номер из списка: точные записи, совпадающие с номером. */
    fun removeNumberFromList(type: ListType, number: String) {
        db.delete("lists", "type = ? AND pattern = ?", arrayOf(type.name, number)); changed()
    }
}
