# Vokabellux

An Android vocabulary app for learning English directly through illustrated scenes, listening, sentence practice, and speaking. The course covers 188 distinct entries from **Zoe Vokabeln 2610.pdf**, printed pages 218–224, including the Feelings and Going online boxes. The learning content contains no German translations.

## Current delivery status

The complete picture set is ready: **188 vocabulary entries illustrated**, with 376 packaged audio clips. The current installable build is `output/Vokabellux-1.0.apk`. It includes the learning exercises, local progress, recording/playback, and Azure pronunciation-assessment integration. Live Azure scoring still needs validation with the learner's own Speech resource and phone.

The first illustration sheet was generated with the built-in image tool; the remaining eleven sheets and targeted corrections were created with **Google Nano Banana 2.1**. Pictures are bundled in the APK, so the phone needs no Gemini key and makes no image-generation requests. See [artwork generation details](docs/NANO_BANANA.md) and [validation](output/VALIDATION.md).

## Install

Copy `output/Vokabellux-1.0.apk` to an Android phone running Android 8 or later. Open it and allow installation from your file manager when Android asks. The personal APK is debug signed. Future APKs must use the same signing key to update the app while preserving progress.

Open **Today** for a daily mix or **Lessons** to choose a theme. Each lesson introduces five words, then practises listening, picture recognition, contextual completion, sentence order, spelling, word pronunciation, and describing a scene. **Words** is a searchable collection with pictures and audio. **Review** revisits familiar words after 1, 3, 7, 14, and 30 days. Incorrect items return in the current session and again tomorrow. Unfinished sessions can be resumed after closing the app.

Pictures and 376 spoken word/sentence clips are packaged with the app. Listening and comprehension work offline. Speaking feedback requires your own Azure Speech subscription and an internet connection.

## Set up pronunciation

For the full portal walkthrough, see [Get an Azure Speech key](docs/AZURE_SETUP.md).

1. Create a regional **Azure AI Speech** resource in your Azure account. Its **Keys and Endpoint** page provides the subscription key and region, such as `westeurope`.
2. In the app's **Settings**, enter the key and region, choose a daily speech request cap, enable submitted-recording processing, and save.
3. In a speaking exercise, allow microphone access. Record up to 15 seconds, listen back if desired, and tap **Submit for feedback**.
4. Review highlighted words, listen to the example again, and retry or continue.

The app calls Azure directly; no hosted backend or app account is needed. The speech key is encrypted using Android Keystore, never included in source/APK, and excluded from Android backups and transfers. Credentials on a personal device are a security compromise; use a separate resource and rotate the key if the device is lost. Azure may charge for requests. The local daily cap counts submission attempts, including failures, and cannot enforce an Azure-wide billing limit.

Only **Submit** sends a recording to the speech provider. Temporary recordings are removed on leaving the exercise or putting the app in the background. Only progress and scores are saved locally. Provider retention is governed by your Azure resource and service policies. No analytics are included. Automated scores are practice estimates; skipped or unavailable assessment never creates a pronunciation grade.

British English is the default. Explicitly American entries (`color`, `neighbor`, `center`, `mom`, `movie`) use American audio and assessment. Feedback uses word-level accuracy, omissions, and available fluency/completeness scores. It does not claim sound-by-sound diagnosis for British English. Short scene descriptions are guided practice, with the expected sentence shown.

## Build and verify

Requirements: JDK 17, Android SDK platform 35/build tools 35.0.0, and internet for the initial Gradle dependencies. Set `ANDROID_HOME` or add `sdk.dir=/path/to/Android/sdk` to an untracked `local.properties`. The wrapper pins Gradle 8.11.1.

```sh
./scripts/android.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 scripts/validate_content.py
# With an emulator or USB-debugging-enabled phone connected:
./scripts/android.sh :app:connectedDebugAndroidTest
```

`scripts/android.sh` automatically uses the local `.tools` toolchain if present. Otherwise it uses your installed Java/Android SDK. The APK is built at `app/build/outputs/apk/debug/app-debug.apk`.

The source vocabulary and original illustration descriptions are in `scripts/vocabulary.tsv`. Run `python3 scripts/build_content.py` after editing content. After generating or replacing an illustration, first run `python3 scripts/validate_illustration_grids.py --write-layout` (requires FFmpeg), then rebuild content; this records actual scene boundaries and rejects merged panels. Audio is generated using macOS Daniel (British) and Samantha (American) voices and FFmpeg: `python3 scripts/generate_audio.py`. Existing clips are retained; remove changed clips before regenerating them. Generated illustrations are included in the source tree, so normal builds need no image API or image-generation account. Missing illustrations can be generated through `scripts/generate_nano_banana.py` using a funded Gemini API account; see [Nano Banana workflow](docs/NANO_BANANA.md). See `docs/illustration-prompts.json` for the original prompts and `docs/nano-banana-generations.json` for exact Google prompts, model details, and final image hashes.

## Structure

- `app/src/main/java/com/vokabellux/app`: Compose screens, learning/session rules, Room persistence, preferences, encrypted credentials, WAV recording, Azure assessment.
- `app/src/main/assets`: versioned English-only content, 12 illustration atlases (16 cells each), and offline AAC speech.
- `app/src/test` and `app/src/androidTest`: learning-rule, provider-result, and Android lesson-flow tests.

Each vocabulary ID is stable across progress updates. Distinct meanings of `as` and noun/verb forms remain separate. Repeated textbook entries share one learning record. Box headings are not vocabulary entries. Irregular forms and regional alternatives are shown in discovery and the vocabulary collection.

This is a personal vocabulary course inspired by immersion learning. Live tutoring, accounts, cloud sync, unrestricted AI conversation, and Google Play release are outside this version.
