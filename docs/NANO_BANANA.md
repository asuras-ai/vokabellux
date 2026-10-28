# Vocabulary illustrations with Nano Banana

The image-generation workflow now supports **Google Nano Banana 2.1** through
the Gemini Interactions API. It uses the existing first illustration board as a
style/grid reference and generates missing boards at 2048×2048 pixels. The app's
existing content mapping remains unchanged.

## Current status

All eleven missing illustration boards were generated successfully with the new local key on 2026-10-28. Two boards received targeted corrections after visual review. All twelve boards are included under `app/src/main/assets/images`, covering all 188 vocabulary entries. The first board remains the original built-in-tool illustration and supplies the style reference.

An earlier key returned HTTP 402 (depleted prepayment credits). If this happens again, use [Google AI Studio projects](https://ai.studio/projects) to select the key's project and open **Billing → Buy credits**. See Google's [billing guide](https://ai.google.dev/gemini-api/docs/billing#prepay).

## Resume generation

The script first uses the private, Git-ignored `.tools/gemini-api-key` file if present, then falls back to `GEMINI_API_KEY` or `GOOGLE_API_KEY` in the environment. `--key-file` selects another local file. The configured private file has mode 0600. Never put the key into source, chat, or the Android application.

The normal build uses the saved images and needs no API key. To resume a partially missing image set, first generate and inspect one sheet:

```sh
python3 scripts/generate_nano_banana.py 2
```

After confirming its scene order and visual quality, generate the remainder:

```sh
python3 scripts/generate_nano_banana.py 3 4 5 6 7 8 9 10 11 12
python3 scripts/validate_illustration_grids.py --write-layout
python3 scripts/build_content.py
python3 scripts/validate_content.py
./scripts/android.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Existing images are skipped. Each successful image is saved directly under
`app/src/main/assets/images/board_NN.png`. Its exact prompt, model, reference
hash, output hash, dimensions, and returned usage metadata are recorded in
`docs/nano-banana-generations.json`. The original prompts are retained in
`docs/illustration-prompts.json`. Provider request storage is disabled. Successful responses are checkpointed under `.tools` before extraction, so response-handling retries reuse the result rather than making another paid request. The live API returns images inside `steps`; provider JPEG output is normalized to PNG with FFmpeg without resizing the scene.

To preview a request without making a paid call:

```sh
python3 scripts/generate_nano_banana.py 2 --dry-run
```

The script stops on billing or authentication errors. Temporary HTTP 429/5xx
errors receive at most two retries. Connection timeouts are not automatically
resubmitted because a timed-out generation might still have been processed.

The workflow's syntax, request construction, image extraction, and billing-error
handling can be checked without an API key:

```sh
python3 -m unittest discover -s scripts/tests -v
```

Live generation succeeded. Every board was visually reviewed for scene order and vocabulary coverage. `scripts/validate_illustration_grids.py` checks every row/column gutter and records the measured panel bounds in `scripts/illustration-layout.json`; content compilation embeds those bounds so slightly uneven generated grids are displayed correctly. A previously rejected merged-panel image is confirmed to fail that check. FFmpeg is required only for decoding during this optional artwork validation step. `--repair-prompt <file>` produces an edited candidate under `.tools/illustration-candidates` for review before any app asset is replaced. Accepted corrections and their prompts are recorded in the generation manifest; the repair prompt revisions for board 05 are retained for traceability.

Official API reference:
[Nano Banana image generation](https://ai.google.dev/gemini-api/docs/image-generation).
