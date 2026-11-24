import json
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path

from tools.build_food_catalogue import (
    build,
    clean_recipe_english,
    normalize,
    parse_foodon_tsv,
    parse_off_taxonomy,
)


class FoodCatalogueBuilderTests(unittest.TestCase):
    def test_normalize_is_case_accent_and_arabic_robust(self):
        self.assertEqual(normalize("Tomàto Paste"), "tomato paste")
        self.assertEqual(normalize("أَلطَّماطِم"), normalize("الطماطم"))

    def test_focus_english_source_text_removes_quantity_tail(self):
        self.assertEqual(
            clean_recipe_english("Chicken thigh, skinless, cooked: 560 g cooked (about 750 g raw)"),
            "Chicken thigh, skinless, cooked",
        )

    def test_off_parser_reads_parent_and_three_languages(self):
        text = """< en: sauce
en: mayonnaise, mayo
ar: مايونيز
fr: mayonnaise, sauce mayonnaise

< en: vegetable
en: tomato
ar: طماطم
fr: tomate
"""
        nodes = parse_off_taxonomy(text)
        self.assertEqual(len(nodes), 2)
        self.assertEqual(nodes[0]["parents"], ["sauce"])
        self.assertEqual(nodes[0]["languages"]["ar"][0], "مايونيز")
        self.assertEqual(nodes[0]["english"], "mayonnaise")

    def test_foodon_parser_reads_only_explicit_english_labels_and_synonyms(self):
        text = """?class\t?parent\t?type\t?label
<http://purl.obolibrary.org/obo/FOODON_00000001>\t\t"label"\t"tomato"@en
<http://purl.obolibrary.org/obo/FOODON_00000001>\t\t"synonym"\t"love apple"@en
<http://purl.obolibrary.org/obo/FOODON_00000002>\t\t"label"\t"tomate"@fr
"""
        parents, names = parse_foodon_tsv(text)
        self.assertIn("http://purl.obolibrary.org/obo/FOODON_00000001", names["tomato"])
        self.assertIn("http://purl.obolibrary.org/obo/FOODON_00000001", names["love apple"])
        self.assertNotIn("tomate", names)
        self.assertEqual(len(parents), 2)

    def test_build_matches_recipe_aliases_creates_missing_and_keeps_uuid_stable(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            off = root / "off.txt"
            foodon = root / "foodon.tsv"
            recipes = root / "recipes.json"
            out = root / "out"
            categories = root / "categories.json"
            off.write_text(
                """< en: vegetable
en: tomato
ar: طماطم
fr: tomate

< en: pulse
en: chickpea, garbanzo bean
ar: حمص
fr: pois chiche
""",
                encoding="utf-8",
            )
            foodon.write_text(
                """?class\t?parent\t?type\t?label
<http://purl.obolibrary.org/obo/FOODON_00000001>\t\t"label"\t"tomato"@en
""",
                encoding="utf-8",
            )
            categories.write_text(json.dumps({
                "categories": [
                    {"id": "vegetables", "names": {"ar": "الخضروات", "en": "Vegetables", "fr": "Légumes"}},
                    {"id": "legumes", "names": {"ar": "البقوليات", "en": "Legumes", "fr": "Légumineuses"}},
                    {"id": "other_foods", "names": {"ar": "أخرى", "en": "Other", "fr": "Autres"}},
                ]
            }, ensure_ascii=False), encoding="utf-8")
            recipes.write_text(json.dumps({
                "complete": True,
                "expectedCount": 1,
                "translatedCount": 1,
                "generatedAt": "test",
                "recipes": [{
                    "id": "unitools:test",
                    "ingredients": [
                        {"id": "tomato", "name": "طماطم", "sourceNameEn": "Tomato", "unit": "g"},
                        {"id": "new", "name": "مكون جديد", "sourceNameEn": "New food", "unit": "piece"},
                    ],
                }],
            }, ensure_ascii=False), encoding="utf-8")
            args = Namespace(
                recipes=recipes, off_taxonomy=off, foodon_tsv=foodon,
                categories=categories, output_dir=out,
            )
            first = build(args)
            catalogue_1 = json.loads((out / "catalogue.json").read_text(encoding="utf-8"))
            map_doc = json.loads((out / "recipe_ingredient_map.json").read_text(encoding="utf-8"))
            self.assertEqual(first["recipeIngredientOccurrences"], 2)
            self.assertEqual(len(map_doc["items"]), 2)
            self.assertEqual(map_doc["items"][0]["matchedBy"], "off_english")
            self.assertEqual(len({item["id"] for item in catalogue_1["items"]}), len(catalogue_1["items"]))
            tomato = next(item for item in catalogue_1["items"] if item["names"]["en"] == "tomato")
            self.assertEqual(tomato["names"]["ar"], "طماطم")
            self.assertEqual(tomato["baseUnit"], "g")
            self.assertEqual(len(tomato["sourceRefs"]["foodOn"]), 1)
            ids_before = {item["names"]["en"]: item["id"] for item in catalogue_1["items"]}
            build(args)
            catalogue_2 = json.loads((out / "catalogue.json").read_text(encoding="utf-8"))
            ids_after = {item["names"]["en"]: item["id"] for item in catalogue_2["items"]}
            self.assertEqual(ids_before, ids_after)

    def test_incomplete_recipe_catalogue_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            off, foodon, recipes, categories = root / "off.txt", root / "foodon.tsv", root / "recipes.json", root / "categories.json"
            off.write_text("en: tomato\n", encoding="utf-8")
            foodon.write_text("?class\t?parent\t?type\t?label\n", encoding="utf-8")
            categories.write_text('{"categories":[{"id":"other_foods","names":{"ar":"أخرى","en":"Other","fr":"Autres"}}]}', encoding="utf-8")
            recipes.write_text('{"complete": false, "expectedCount": 1, "translatedCount": 0, "recipes": []}', encoding="utf-8")
            with self.assertRaises(ValueError):
                build(Namespace(
                    recipes=recipes, off_taxonomy=off, foodon_tsv=foodon,
                    categories=categories, output_dir=root / "out",
                ))


if __name__ == "__main__":
    unittest.main()
