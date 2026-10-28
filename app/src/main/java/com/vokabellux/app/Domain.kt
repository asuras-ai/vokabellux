package com.vokabellux.app

import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId

data class Vocabulary(
    val id: String, val term: String, val spoken: String, val example: String,
    val page: Int, val theme: String, val locale: String, val image: String,
    val panel: Int, val audio: String, val exampleAudio: String, val forms: List<String>,
    val bounds: List<Int> = emptyList()
)
data class Theme(val id: String, val title: String, val headline: String, val description: String)
data class Course(val themes: List<Theme>, val items: List<Vocabulary>)

data class ReviewSchedule(val stage: Int, val dueAt: Long)
object LearningRules {
    const val DAY = 86_400_000L
    val intervals = listOf(1, 3, 7, 14, 30)
    fun schedule(stage: Int, correct: Boolean, now: Long): ReviewSchedule {
        val next = if (correct) (stage + 1).coerceAtMost(intervals.lastIndex) else 0
        return ReviewSchedule(next, now + intervals[if (correct) next else 0] * DAY)
    }
    fun normalize(value: String): String = Normalizer.normalize(value.lowercase().replace('’', '\''), Normalizer.Form.NFKC)
        .replace(Regex("[^\\p{L}\\p{N}' ]"), " ").replace(Regex("\\s+"), " ").trim()
    fun matches(answer: String, expected: String, alternatives: List<String> = emptyList()): Boolean =
        (listOf(expected) + alternatives).any { normalize(answer) == normalize(it) }
    fun dayKey(now: Long = System.currentTimeMillis()): String =
        java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    fun gap(item: Vocabulary): String? {
        val target = item.spoken.trimEnd('.', '!', '?')
        val regex = Regex("(?i)(?<![a-z])" + Regex.escape(target) + "(?![a-z])")
        return if (regex.containsMatchIn(item.example)) item.example.replace(regex, "______") else null
    }
}
enum class Exercise { DISCOVER, LISTEN, PICTURE, CONTEXT, ORDER, SPELL, SPEAK, DIALOGUE }
data class Step(val itemId: String, val kind: Exercise, val retry: Boolean = false)
data class Session(
    val title: String, val steps: List<Step>, val index: Int = 0,
    val failed: Set<String> = emptySet(), val answered: Int = 0, val correct: Int = 0,
    val speechPassed: Set<String> = emptySet(), val speechTried: Set<String> = emptySet(),
    val seed: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
    val token: String = java.util.UUID.randomUUID().toString()
) {
    val done get() = index >= steps.size
    val current get() = steps.getOrNull(index)
    fun advance(success: Boolean?, speech: Boolean = false): Session {
        val step = current ?: return this
        val scored = success != null && step.kind != Exercise.DISCOVER
        val nextSteps = steps.toMutableList()
        if (success == false && !speech && !step.retry) {
            nextSteps.add((index + 4).coerceAtMost(nextSteps.size), step.copy(retry = true))
        }
        return copy(steps = nextSteps, index = index + 1,
            failed = if (success == false && !speech) failed + step.itemId else failed,
            answered = answered + if (scored && !speech) 1 else 0,
            correct = correct + if (success == true && !speech) 1 else 0,
            speechPassed = if (speech && success == true) speechPassed + step.itemId else speechPassed,
            speechTried = if (speech && scored) speechTried + step.itemId else speechTried)
    }
}
object SessionFactory {
    fun create(title: String, items: List<Vocabulary>, review: Boolean): Session {
        val kinds = if (review) listOf(Exercise.LISTEN, Exercise.CONTEXT, Exercise.SPELL, Exercise.SPEAK)
        else listOf(Exercise.DISCOVER, Exercise.LISTEN, Exercise.PICTURE, Exercise.CONTEXT, Exercise.ORDER, Exercise.SPELL, Exercise.SPEAK, Exercise.DIALOGUE)
        return Session(title, kinds.flatMap { kind -> items.map { Step(it.id, kind) } })
    }
}
data class WordFeedback(val word: String, val accuracy: Double, val error: String)
data class SpeechFeedback(val accuracy: Double, val fluency: Double?, val completeness: Double?, val words: List<WordFeedback>, val recognized: String) {
    val passed get() = accuracy >= 70 && (completeness == null || completeness >= 70)
    val message get() = when {
        passed -> "Nicely spoken. Keep going!"
        words.any { it.error == "Omission" } -> "A word was missing. Listen once, then try the whole phrase."
        else -> "Listen once more and try the highlighted words."
    }
}
