# Implementation notes

The app is a single-activity Kotlin/Compose application. `LearningViewModel` coordinates the English content bundle, local Room database, DataStore preferences/session checkpoint, temporary audio, and a `PronunciationService` interface. `AzurePronunciationService` implements that interface through the Android Speech SDK; it never receives a recording until the user submits it.

`Vocabulary` references a stable ID, source page, theme, locale, spoken form, English example, related forms, one atlas panel, and two audio paths. `Session` stores a queue of typed exercises, position, incorrect items, and separate speaking attempts/successes. It is JSON-checkpointed after each continued exercise. A Room transaction records completion using a unique session token so replaying a completed checkpoint cannot advance the schedule twice.

Comprehension reviews and speech attempts are independent. New comprehension success is due in one day; subsequent successes use 3, 7, 14, then 30 days. An incorrect answer inserts one later retry in the session and schedules the item for tomorrow. Another incorrect retry does not create an endless queue. Speech can be skipped, and provider/connection failures do not generate a score.

Audio is recorded locally as 16-kHz, mono PCM WAV with a hard limit of 15 seconds. Stop requests are safe even before capture starts. Submission uses scripted pronunciation assessment with the expected word or sentence and its locale. Accuracy ≥70 and completeness ≥70 when available count as a speaking success. These defaults are deliberately encouraging and should be calibrated with the learner's real recordings. Word-level results drive highlighting; the app makes no unsupported British-English phoneme claims. Submitted-attempt caps are enforced atomically in DataStore and reset by local calendar day. They count failures conservatively.

Keys are AES-GCM encrypted with a non-exportable Android Keystore key. No app data is included in Android backup/device transfer. There is no logging of keys, provider payloads, or recordings. Temporary audio is cleared on leaving an exercise, entering the background, or the next cold start. Azure service processing policies remain separate from local cleanup.

Dependencies are pinned. The initial SDK installation guide showed Speech SDK 1.43.0; implementation uses **1.52.0** because Microsoft's current release notes call out a required Android CRL fix in 1.48.2+ and later Android/native improvements.

Primary references:

- [Azure pronunciation assessment](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/how-to-pronunciation-assessment)
- [Azure language support](https://learn.microsoft.com/azure/cognitive-services/speech-service/language-support?tabs=tts)
- [Azure Speech release notes](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/releasenotes)
- [Android Gradle plugin 8.9](https://developer.android.com/build/releases/agp-8-9-0-release-notes)
