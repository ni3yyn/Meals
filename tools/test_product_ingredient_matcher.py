import json
import tempfile
import unittest
from pathlib import Path
from tools.product_ingredient_matcher import normalize, similarity, load_records


class ProductIngredientMatcherTests(unittest.TestCase):
    def test_arabic_and_latin_normalization(self):
        self.assertEqual(normalize("زيتُ الزيتون"), normalize("زيت الزيتون"))
        self.assertEqual(normalize("Épices"), "epices")

    def test_exact_match_scores_one(self):
        score, method = similarity("ملح", ["ملح"])
        self.assertEqual(score, 1.0)
        self.assertEqual(method, "exact_normalized")

    def test_brand_noise_does_not_erase_ingredient_phrase(self):
        score, method = similarity("مادور ملح طعام 1 كغ", ["ملح طعام", "سكر"])
        self.assertGreaterEqual(score, 0.90)
        self.assertEqual(method, "ingredient_phrase")

    def test_json_nested_records(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "data.json"
            path.write_text(json.dumps({"catalogue": {"foods": [{"id": "salt"}]}}), encoding="utf-8")
            self.assertEqual(load_records(path, ("foods", "items"))[0]["id"], "salt")


if __name__ == "__main__":
    unittest.main()
