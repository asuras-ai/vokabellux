package com.vokabellux.app

import android.content.Context
import androidx.room.*
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "progress")
data class Progress(
    @PrimaryKey val id: String, val stage: Int = -1, val dueAt: Long = 0,
    val reviews: Int = 0, val lastSeen: Long = 0, val speechScore: Double? = null,
    val speechAttempts: Int = 0, val speechMastered: Boolean = false
)
@Entity(tableName = "daily")
data class Daily(@PrimaryKey val day: String, val answers: Int = 0, val sessions: Int = 0)
@Entity(tableName = "completed_sessions")
data class CompletedSession(@PrimaryKey val token: String)
@Dao
interface LearningDao {
    @Query("SELECT * FROM progress") fun observeProgress(): Flow<List<Progress>>
    @Query("SELECT * FROM daily") fun observeDaily(): Flow<List<Daily>>
    @Query("SELECT * FROM progress WHERE id = :id") suspend fun get(id: String): Progress?
    @Upsert suspend fun put(progress: Progress)
    @Query("SELECT * FROM daily WHERE day = :day") suspend fun daily(day: String): Daily?
    @Upsert suspend fun putDaily(daily: Daily)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun markComplete(session: CompletedSession): Long
}
@Database(entities = [Progress::class, Daily::class, CompletedSession::class], version = 1, exportSchema = false)
abstract class LearningDatabase : RoomDatabase() { abstract fun dao(): LearningDao }
private val Context.store by preferencesDataStore("preferences")
data class Preferences(val goal: Int = 20, val cap: Int = 50, val usage: Int = 0, val speechConsent: Boolean = false, val session: String = "")
class SettingsStore(private val context: Context) {
    val flow = context.store.data.map { p -> Preferences(
        p[intPreferencesKey("goal")] ?: 20, p[intPreferencesKey("cap")] ?: 50,
        if (p[stringPreferencesKey("usage_day")] == LearningRules.dayKey()) p[intPreferencesKey("usage")] ?: 0 else 0,
        p[booleanPreferencesKey("consent")] ?: false, p[stringPreferencesKey("session")] ?: "") }
    suspend fun settings(goal: Int, cap: Int, consent: Boolean) = context.store.edit {
        it[intPreferencesKey("goal")] = goal; it[intPreferencesKey("cap")] = cap; it[booleanPreferencesKey("consent")] = consent
    }
    suspend fun saveSession(session: Session?) = context.store.edit { it[stringPreferencesKey("session")] = session?.let { s -> SessionJson.encode(s) } ?: "" }
    suspend fun reserveAssessment(): Boolean {
        var allowed = false
        context.store.edit {
            val day = LearningRules.dayKey()
            val used = if (it[stringPreferencesKey("usage_day")] == day) it[intPreferencesKey("usage")] ?: 0 else 0
            if (used < (it[intPreferencesKey("cap")] ?: 50)) {
                it[stringPreferencesKey("usage_day")] = day; it[intPreferencesKey("usage")] = used + 1
                allowed = true
            }
        }
        return allowed
    }
}
object SessionJson {
    fun encode(s: Session) = JSONObject().apply {
        put("title",s.title); put("index",s.index); put("failed",JSONArray(s.failed.toList())); put("answered",s.answered); put("correct",s.correct)
        put("speechPassed",JSONArray(s.speechPassed.toList())); put("speechTried",JSONArray(s.speechTried.toList())); put("seed",s.seed); put("token",s.token)
        put("steps",JSONArray(s.steps.map { JSONObject().put("id",it.itemId).put("kind",it.kind.name).put("retry",it.retry) }))
    }.toString()
    fun decode(raw: String): Session? = runCatching {
        val o=JSONObject(raw)
        fun strings(key:String) = o.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        val a=o.getJSONArray("steps")
        Session(o.getString("title"),(0 until a.length()).map { a.getJSONObject(it).let { s -> Step(s.getString("id"), Exercise.valueOf(s.getString("kind")),s.optBoolean("retry")) } },
            o.getInt("index"),strings("failed"),o.getInt("answered"),o.getInt("correct"),strings("speechPassed"),strings("speechTried"),o.getInt("seed"),o.getString("token"))
    }.getOrNull()
}
fun loadCourse(context: Context): Course {
    val root=JSONObject(context.assets.open("content/vocabulary.json").bufferedReader().use { it.readText() })
    val themes=root.getJSONArray("themes"); val items=root.getJSONArray("items")
    return Course((0 until themes.length()).map { themes.getJSONObject(it).let { t -> Theme(t.getString("id"),t.getString("title"),t.getString("headline"),t.getString("description")) } },
        (0 until items.length()).map { items.getJSONObject(it).let { v ->
            val forms=v.getJSONArray("forms")
            val bounds=v.optJSONArray("bounds")
            Vocabulary(v.getString("id"),v.getString("term"),v.getString("spoken"),v.getString("example"),v.getInt("page"),v.getString("theme"),v.getString("locale"),v.getString("image"),v.getInt("panel"),v.getString("audio"),v.getString("exampleAudio"),(0 until forms.length()).map { j -> forms.getString(j) },(0 until (bounds?.length() ?: 0)).map { j -> bounds!!.getInt(j) })
        } })
}
