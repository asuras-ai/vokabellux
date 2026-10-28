# Validation — Vokabellux 1.0

Verified on 2026-10-28.

- **188/188 vocabulary entries have illustrations**, mapped across twelve atlases. Every atlas was visually reviewed for panel order and vocabulary meaning. Every row/column gutter passes the atlas-layout check, and the earlier merged-panel defect is detected by the same checker. Measured scene bounds are embedded in the course bundle. Eleven atlases and targeted corrections were produced with Google Nano Banana 2.1; the original first atlas was preserved as the style reference.
- **376 offline audio clips** are included, with word and example-sentence audio for every entry.
- `scripts/validate_content.py` passes: IDs, themes, all seven source pages, asset presence, PNG formats/dimensions, and panel indices.
- **Nine JVM tests pass**: spaced review, mistakes/retries, answer matching, separate speaking progress, session checkpoint recovery, and speech-result parsing.
- **Seven instrumented Android tests pass** on an Android 15 ARM64 emulator: complete offline lesson, leave/resume, bounded WAV recording, stop-before-start, encrypted credential storage/removal, and native Speech SDK configuration, and valid packaged image bounds for all 188 words.
- **Three Python workflow tests pass**: image-response extraction (including the live API's steps structure), atlas format validation, and non-retrying/redacted billing errors.
- Android build and lint pass. Remaining lint warnings concern newer optional dependency versions and Kotlin convenience APIs.
- The new APK is version code 2 and uses the same personal debug signing key as the preview. Its APK Signature Scheme v2 signature verifies.
- Every packaged illustration hash is checked against `docs/nano-banana-generations.json`; the APK includes all 188 content records and all 376 audio clips.
- The Gemini API key is kept in a private, Git-ignored local file and is absent from the APK. The phone does not call an image-generation API.
- Lesson illustrations show the complete scene; decorative home artwork can use a cover crop. Final screens were checked on the emulator.

The deliverable is `output/Vokabellux-1.0.apk`, with a SHA-256 checksum beside it. It supersedes the earlier incomplete preview, which is retained only as a historical artifact.

Live Azure pronunciation scoring has not been tested because no Azure Speech key or physical Android phone was available. The implemented service, recording flow, result parsing, and native SDK are checked; scoring quality and accent tolerance require a trial with the learner's own Speech resource on the intended phone. See `docs/AZURE_SETUP.md`.
