#!/usr/bin/env python3
"""Generate missing vocabulary atlases through Google's Nano Banana API.

Credentials are read from a private key file or environment. Existing images are preserved.
Uses the documented Interactions API with the first board as a style reference.
"""
import argparse
import base64
import datetime
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
IMAGES = ROOT / "app/src/main/assets/images"
MANIFEST = ROOT / "docs/nano-banana-generations.json"
ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
MODEL = "gemini-nano-banana-2.1"
STYLE_INSTRUCTION = (
    "The attached image is a STYLE AND GRID REFERENCE ONLY. Match its expressive "
    "teen characters, editorial anime illustration, lighting, colours, and regular "
    "four-by-four panel layout. Create NEW content for the sixteen scenes below; "
    "do not repeat the reference scenes. The app crops each cell independently: "
    "put vertical gutters at exactly 25%, 50%, and 75% of the image width and "
    "horizontal gutters at exactly 25%, 50%, and 75% of the height. Keep every "
    "scene inside its own equal square. No title, captions, panel numbers, or "
    "outer margins. Every requested scene must be identifiable.\n\n"
)


def png_dimensions(data):
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("The API did not return a PNG; refusing to mislabel it.")
    width, height = struct.unpack(">II", data[16:24])
    if width != height or width < 1024:
        raise ValueError(f"Invalid atlas dimensions: {width}x{height}")
    return width, height


def as_png(data):
    """Normalize the provider's JPEG/WebP output without altering the illustration."""
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return data
    converted = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", "pipe:0", "-frames:v", "1",
         "-f", "image2pipe", "-c:v", "png", "pipe:1"],
        input=data, capture_output=True, check=True,
    )
    return converted.stdout


def image_data(value):
    """Find only generated image parts, without logging response image data."""
    if isinstance(value, dict):
        mime = value.get("mime_type", value.get("mimeType", ""))
        if "data" in value and (mime.startswith("image/") or value.get("type") == "image"):
            return base64.b64decode(value["data"], validate=True)
        for child in value.values():
            found = image_data(child)
            if found is not None:
                return found
    elif isinstance(value, list):
        for child in value:
            found = image_data(child)
            if found is not None:
                return found
    return None


def call_api(payload, key):
    request = urllib.request.Request(
        ENDPOINT,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json", "x-goog-api-key": key},
        method="POST",
    )
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=300) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            detail = error.read().decode(errors="replace").replace(key, "[REDACTED]")
            if error.code in (429, 500, 502, 503, 504) and attempt < 2:
                print(f"Temporary API error {error.code}; retrying ({attempt + 1}/2).", flush=True)
                time.sleep(10 * (attempt + 1))
                continue
            raise RuntimeError(f"Google API HTTP {error.code}: {detail[:1200]}") from None
        except urllib.error.URLError as error:
            # A timed-out generation may still be billed; don't blindly resubmit.
            raise RuntimeError(f"Google API connection failed: {str(error.reason).replace(key, '[REDACTED]')}") from None


def generate(number, model, size, dry_run, key_file=None, repair_prompt=None):
    output = (ROOT / ".tools/illustration-candidates" if repair_prompt else IMAGES) / f"board_{number:02}.png"
    if output.exists():
        print(f"Board {number:02}: already present; skipped.", flush=True)
        return
    prompts = json.loads((ROOT / "docs/illustration-prompts.json").read_text())
    prompt = Path(repair_prompt).read_text() if repair_prompt else STYLE_INSTRUCTION + prompts["boards"][number - 1]
    reference_path = IMAGES / (f"board_{number:02}.png" if repair_prompt else "board_01.png")
    reference = reference_path.read_bytes()
    if dry_run:
        print(json.dumps({"board": number, "model": model, "image_size": size,
                          "reference": str(reference_path.relative_to(ROOT)), "output": str(output), "prompt": prompt}, indent=2))
        return
    private_file = Path(key_file) if key_file else ROOT / ".tools/gemini-api-key"
    key = private_file.read_text().strip() if private_file.exists() else (
        os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY")
    )
    if not key:
        raise RuntimeError("Set GEMINI_API_KEY locally before generating images. Do not put it in source or chat.")
    payload = {
        "model": model,
        "store": False,
        "input": [
            {"type": "text", "text": prompt},
            {"type": "image", "mime_type": "image/png", "data": base64.b64encode(reference).decode()},
        ],
        "response_format": {"type": "image", "aspect_ratio": "1:1", "image_size": size},
    }
    print(f"Board {number:02}: generating with {model} at {size}...", flush=True)
    checkpoint = ROOT / ".tools" / f"nano-response-{'repair-' if repair_prompt else ''}board-{number:02}.json"
    if checkpoint.exists():
        print(f"Board {number:02}: recovering saved API response without another request.", flush=True)
        result = json.loads(checkpoint.read_text())
    else:
        result = call_api(payload, key)
        checkpoint.parent.mkdir(exist_ok=True)
        descriptor = os.open(checkpoint, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w") as stream:
            json.dump(result, stream)
    data = image_data(result.get("output_image")) or image_data(result.get("outputs")) or image_data(result.get("steps"))
    if data is None:
        # Never print a raw response: it can contain large binary image parts.
        raise RuntimeError(f"No image returned (status={result.get('status')}; fields={','.join(result.keys())}).")
    data = as_png(data)
    dimensions = png_dimensions(data)
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(".png.part")
    temporary.write_bytes(data)
    temporary.replace(output)
    manifest = json.loads(MANIFEST.read_text()) if MANIFEST.exists() else {"provider": "Google Gemini API", "generations": []}
    manifest["generations"].append({
        "board": number, "model": model, "image_size": size,
        "created_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "output": str(output.relative_to(ROOT)), "dimensions": dimensions,
        "sha256": hashlib.sha256(data).hexdigest(),
        "reference": str(reference_path.relative_to(ROOT)),
        "reference_sha256": hashlib.sha256(reference).hexdigest(),
        "prompt": prompt,
        "usage": result.get("usage", result.get("usage_metadata")),
    })
    MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Board {number:02}: saved {dimensions[0]}x{dimensions[1]} PNG ({len(data):,} bytes).", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("boards", type=int, nargs="*", default=list(range(2, 13)))
    parser.add_argument("--model", default=MODEL)
    parser.add_argument("--size", choices=["1K", "2K", "4K"], default="2K")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--key-file", type=Path, help="Private API key file; defaults to .tools/gemini-api-key if present")
    parser.add_argument("--repair-prompt", type=Path, help="Edit one existing atlas into a private candidate file for visual review")
    args = parser.parse_args()
    if args.repair_prompt and len(args.boards) != 1:
        parser.error("A repair must specify exactly one board.")
    for number in args.boards:
        if not 2 <= number <= 12:
            parser.error("Board numbers must be between 2 and 12; the first reference board is preserved.")
    try:
        for number in args.boards:
            generate(number, args.model, args.size, args.dry_run, args.key_file, args.repair_prompt)
    except (RuntimeError, ValueError, TimeoutError, subprocess.CalledProcessError) as error:
        print(f"Generation stopped: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
