import base64
import importlib.util
import io
from pathlib import Path
import struct
import unittest
from unittest.mock import patch
from urllib.error import HTTPError


spec = importlib.util.spec_from_file_location(
    "nano_banana", Path(__file__).resolve().parents[1] / "generate_nano_banana.py"
)
nano = importlib.util.module_from_spec(spec)
spec.loader.exec_module(nano)


class NanoBananaTests(unittest.TestCase):
    def test_extracts_image_from_generated_output_parts(self):
        raw = b"generated-image-content"
        result = [
            {"type": "text", "text": "Your image"},
            {"type": "image", "mime_type": "image/png", "data": base64.b64encode(raw).decode()},
        ]
        self.assertEqual(raw, nano.image_data(result))
        self.assertEqual(raw, nano.image_data({"steps": [{"type": "model_output", "content": result}]}))
        self.assertIsNone(nano.image_data([{"type": "text", "text": "No image"}]))

    def test_rejects_wrong_format_and_non_square_atlases(self):
        with self.assertRaises(ValueError):
            nano.png_dimensions(b"not-a-png")
        header = b"\x89PNG\r\n\x1a\n" + b"\0" * 8
        with self.assertRaises(ValueError):
            nano.png_dimensions(header + struct.pack(">II", 2048, 1024))
        self.assertEqual((2048, 2048), nano.png_dimensions(header + struct.pack(">II", 2048, 2048)))

    def test_billing_failure_stops_without_retry_and_redacts_key(self):
        key = "fake-local-key-for-redaction-test"
        failure = HTTPError(nano.ENDPOINT, 402, "Payment Required", {},
                            io.BytesIO(("Credits depleted: " + key).encode()))
        with patch.object(nano.urllib.request, "urlopen", side_effect=failure) as request:
            with patch.object(nano.time, "sleep") as sleep:
                with self.assertRaises(RuntimeError) as result:
                    nano.call_api({"model": nano.MODEL}, key)
        self.assertIn("402", str(result.exception))
        self.assertNotIn(key, str(result.exception))
        self.assertIn("[REDACTED]", str(result.exception))
        self.assertEqual(1, request.call_count)
        sleep.assert_not_called()


if __name__ == "__main__":
    unittest.main()
