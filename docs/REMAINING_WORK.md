# Delivery status

The vocabulary application and complete artwork bundle are ready. The current installable APK is `output/Vokabellux-1.0.apk`; it supersedes the earlier preview. All 188 entries have pictures and two offline audio clips each. `scripts/validate_content.py` passes.

Google Nano Banana 2.1 generated the eleven previously missing sheets. The first sheet remains the original built-in-tool result. The broken-promise scene and family illustrations received targeted corrections after review. The great-grandmother item uses a natural scene of a very elderly woman with her older adult son to avoid ambiguous diagram arrows. Full prompts, model metadata, and final image hashes are in `docs/nano-banana-generations.json`. The generation workflow and private credential handling are documented in `docs/NANO_BANANA.md`.

Lesson images use measured panel bounds and fit the complete scene into their available space, preserving context at the edges. Every atlas row/column gutter passes the layout check, including the restored university cell. The decorative home image retains its cover crop. Version code 2 allows the APK to update the existing preview while keeping local progress when signed with the same personal debug key.

## Real-device speech validation

No physical Android phone or Azure subscription key was available. Live Azure scoring therefore remains unverified. The user enters the Azure Speech key and matching region in the app's Settings; the Gemini key used for illustrations is unrelated and is never included in the APK. See `docs/AZURE_SETUP.md`.

On the intended phone, verify clear/imperfect speech, silence, unrelated speech, network failures, recording playback, and the daily submission cap. The encouraging 70/100 default should be adjusted only based on real recordings. This is validation of the implemented integration, not remaining image work.

The toolchain is in the Git-ignored `.tools` directory. No Git commit or remote publication was requested.
