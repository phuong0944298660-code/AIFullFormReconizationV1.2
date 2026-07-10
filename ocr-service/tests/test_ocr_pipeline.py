import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.ocr_pipeline import (
    extract_lines_from_ocr_response,
    extract_text_from_rec_response,
    recognize_crop,
    recognize_crops,
    recognize_page,
)


class FakeModelClient:
    def recognize(self, image_bytes):
        return "AGUIAR", 0.91


class FakePageModelClient:
    def recognize(self, image_bytes):
        return [
            {
                "rec_texts": ["Length of residence", "22"],
                "rec_scores": [0.97, 0.61],
                "rec_polys": [
                    [[20, 100], [220, 100], [220, 124], [20, 124]],
                    [[450, 100], [485, 100], [485, 124], [450, 124]],
                ],
            }
        ]


class OcrPipelineTest(unittest.TestCase):
    def test_extracts_text_and_confidence_from_nested_paddle_response(self):
        text, confidence = extract_text_from_rec_response(
            [{"res": {"rec_text": "AGUIAR", "rec_score": 0.91}}]
        )

        self.assertEqual(text, "AGUIAR")
        self.assertEqual(confidence, 0.91)

    def test_recognize_crop_uses_injected_model_client(self):
        result = recognize_crop(b"fake-image", model_client=FakeModelClient())

        self.assertEqual(result["text"], "AGUIAR")
        self.assertEqual(result["confidence"], 0.91)
        self.assertEqual(result["status"], "available")

    def test_recognize_crops_returns_one_result_per_crop(self):
        result = recognize_crops([b"first", b"second"], model_client=FakeModelClient())

        self.assertEqual(len(result["results"]), 2)
        self.assertEqual(result["results"][0]["text"], "AGUIAR")
        self.assertEqual(result["results"][1]["confidence"], 0.91)

    def test_extracts_page_text_lines_with_absolute_bboxes(self):
        lines = extract_lines_from_ocr_response(FakePageModelClient().recognize(b"page"))

        self.assertEqual(
            lines,
            [
                {
                    "text": "Length of residence",
                    "confidence": 0.97,
                    "bbox": [20, 100, 220, 124],
                },
                {
                    "text": "22",
                    "confidence": 0.61,
                    "bbox": [450, 100, 485, 124],
                },
            ],
        )

    def test_recognize_page_uses_injected_detection_and_recognition_client(self):
        result = recognize_page(b"page", model_client=FakePageModelClient())

        self.assertEqual(result["status"], "available")
        self.assertEqual(result["lines"][0]["text"], "Length of residence")
        self.assertEqual(result["lines"][0]["bbox"], [20, 100, 220, 124])


if __name__ == "__main__":
    unittest.main()
