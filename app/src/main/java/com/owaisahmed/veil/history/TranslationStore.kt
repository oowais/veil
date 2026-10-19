package com.owaisahmed.veil.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One translated text, as shown on the History screen. */
data class HistoryItem(
    val id: Long,
    val at: Long,
    /** Package of the app the text was on, or null if unknown. */
    val app: String?,
    val engine: String,
    val source: String,
    val translation: String,
)

/**
 * Translations kept on the phone (D-049): a cache so a text seen before is shown at once, also
 * after a restart, and a history of everything translated. Both live in one SQLite file in the
 * app's private storage. Deleting a history item also drops its cached copy, so the text is really
 * gone. With saving turned off nothing is written (the pipeline keeps an in-memory cache).
 *
 * Blocking calls: use them off the main thread.
 */
class TranslationStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "translations.db", null, 1) {

    private val prefs = context.applicationContext.getSharedPreferences("history", Context.MODE_PRIVATE)

    private val _version = MutableStateFlow(0)

    /** Bumped on every change to the history, so screens know to reload. */
    val version: StateFlow<Int> = _version.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE cache (engine TEXT NOT NULL, source TEXT NOT NULL, translation TEXT NOT NULL, " +
                "used_at INTEGER NOT NULL, PRIMARY KEY (engine, source)) WITHOUT ROWID"
        )
        db.execSQL(
            "CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, app TEXT, " +
                "engine TEXT NOT NULL, source TEXT NOT NULL, translation TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX history_at ON history (at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Whether translations are saved (history + persistent cache). On by default. */
    var saving: Boolean
        get() = prefs.getBoolean("saving", true)
        set(value) = prefs.edit().putBoolean("saving", value).apply()

    /** Cached translations of [sources] by [engine]; texts never translated are missing from the map. */
    fun cached(engine: String, sources: Collection<String>): Map<String, String> {
        if (sources.isEmpty()) return emptyMap()
        val out = HashMap<String, String>()
        val db = readableDatabase
        for (chunk in sources.distinct().chunked(500)) {
            val marks = chunk.joinToString(",") { "?" }
            db.rawQuery(
                "SELECT source, translation FROM cache WHERE engine = ? AND source IN ($marks)",
                arrayOf(engine, *chunk.toTypedArray()),
            ).use { c -> while (c.moveToNext()) out[c.getString(0)] = c.getString(1) }
        }
        if (out.isNotEmpty()) {
            val now = System.currentTimeMillis()
            writableDatabase.transaction {
                out.keys.forEach { execSQL("UPDATE cache SET used_at = ? WHERE engine = ? AND source = ?", arrayOf<Any>(now, engine, it)) }
            }
        }
        return out
    }

    /** Saves a new translation to the cache and the history (when [saving] is on). */
    fun save(engine: String, source: String, translation: String, app: String?) {
        if (!saving) return
        val now = System.currentTimeMillis()
        writableDatabase.transaction {
            insertWithOnConflict("cache", null, ContentValues().apply {
                put("engine", engine); put("source", source); put("translation", translation); put("used_at", now)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            insert("history", null, ContentValues().apply {
                put("at", now); put("app", app); put("engine", engine); put("source", source); put("translation", translation)
            })
            // Keep the cache bounded: drop the least recently used beyond the limit.
            // (WITHOUT ROWID table, so rows are matched on the primary key.)
            execSQL(
                "DELETE FROM cache WHERE (engine, source) IN " +
                    "(SELECT engine, source FROM cache ORDER BY used_at DESC LIMIT -1 OFFSET $MAX_CACHE)"
            )
        }
        _version.value++
    }

    /** Newest first. */
    fun history(limit: Int = 2000): List<HistoryItem> =
        readableDatabase.rawQuery(
            "SELECT id, at, app, engine, source, translation FROM history ORDER BY at DESC, id DESC LIMIT $limit", null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(HistoryItem(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5)))
                }
            }
        }

    /** Deletes one history item and every cached translation of its text. */
    fun delete(item: HistoryItem) {
        writableDatabase.transaction {
            delete("history", "id = ?", arrayOf(item.id.toString()))
            delete("cache", "source = ?", arrayOf(item.source))
        }
        _version.value++
    }

    /** Deletes the whole history and the cache. */
    fun deleteAll() {
        writableDatabase.transaction {
            delete("history", null, null)
            delete("cache", null, null)
        }
        _version.value++
    }

    private inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    companion object {
        private const val MAX_CACHE = 5000

        @Volatile private var instance: TranslationStore? = null

        fun get(context: Context): TranslationStore =
            instance ?: synchronized(this) { instance ?: TranslationStore(context).also { instance = it } }
    }
}
