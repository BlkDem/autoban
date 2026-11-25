package com.blkdem.autoban.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.blkdem.autoban.rules.Rules

class Db(context: Context) : SQLiteOpenHelper(context, "autoban.db", null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE events(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                raw_number TEXT, number TEXT, presentation TEXT NOT NULL,
                caller_name TEXT, sim_slot INTEGER, sim_label TEXT,
                action TEXT NOT NULL, source TEXT NOT NULL,
                rule_id INTEGER, rule_name TEXT, dry_run TEXT, would_action TEXT NOT NULL,
                tg_status TEXT NOT NULL DEFAULT 'NONE', details TEXT, lookup TEXT)"""
        )
        db.execSQL("CREATE INDEX events_ts ON events(ts)")
        db.execSQL("CREATE INDEX events_number ON events(number, ts)")
        db.execSQL(
            """CREATE TABLE rules(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL, enabled INTEGER NOT NULL, priority INTEGER NOT NULL,
                mode TEXT NOT NULL, action TEXT NOT NULL, conditions TEXT NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE lists(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL, pattern TEXT NOT NULL, note TEXT,
                created_at INTEGER NOT NULL, rule_name TEXT,
                UNIQUE(type, pattern))"""
        )
        createLookups(db)
        for (r in Rules.defaults) insertRule(db, r)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) canonicalizeLists(db)
        if (oldVersion < 3) {
            // v3: сведения о номере в журнале, кэш поиска, правило «Плохие отзывы» (dry-run).
            db.execSQL("ALTER TABLE events ADD COLUMN lookup TEXT")
            createLookups(db)
            insertRule(db, Rules.reputationDefault)
        }
    }

    private fun createLookups(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE lookups(number TEXT PRIMARY KEY, json TEXT NOT NULL, fetched_at INTEGER NOT NULL)")
    }

    private fun insertRule(db: SQLiteDatabase, r: com.blkdem.autoban.rules.Rule) {
        db.insert("rules", null, ContentValues().apply {
            put("name", r.name)
            put("enabled", if (r.enabled) 1 else 0)
            put("priority", r.priority)
            put("mode", r.mode.name)
            put("action", r.action.name)
            put("conditions", Rules.conditionsToJson(r.conditions))
        })
    }

    /** v2: записи списков в едином виде; появившиеся дубли удаляются (остаётся более ранняя). */
    private fun canonicalizeLists(db: SQLiteDatabase) {
        val rows = db.rawQuery("SELECT id, type, pattern FROM lists ORDER BY created_at, id", null).use { c ->
            generateSequence { if (c.moveToNext()) Triple(c.getLong(0), c.getString(1), c.getString(2)) else null }.toList()
        }
        val seen = HashSet<Pair<String, String>>()
        for ((id, type, pattern) in rows) {
            val canon = com.blkdem.autoban.rules.ListImport.canonical(pattern) ?: pattern.trim()
            if (!seen.add(type to canon)) {
                db.delete("lists", "id = ?", arrayOf(id.toString()))
            } else if (canon != pattern) {
                db.execSQL("UPDATE lists SET pattern = ? WHERE id = ?", arrayOf<Any>(canon, id))
            }
        }
    }

    companion object { const val VERSION = 3 }
}
