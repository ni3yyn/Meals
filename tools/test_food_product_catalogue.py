import tempfile
import unittest
from pathlib import Path

from tools.build_food_product_catalogue import (
    build_catalogue,
    category_for,
    normalize_label,
    parse_foodon_owl,
    parse_off_taxonomy,
    room_ingredient_id,
    _clean_english_ingredient,
    ingredient_name_variants,
)


class FoodCatalogueParserTests(unittest.TestCase):
    def test_off_taxonomy_multilingual_names_aliases_and_parents(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "ingredients.txt"
            path.write_text(
                "< en: legumes\n"
                "en: chickpeas, chickpea\n"
                "ar: حمص\n"
                "fr: pois chiches, pois chiche\n"
                "usda_fdc_code:en: 173756\n\n",
                encoding="utf-8",
            )
            records = parse_off_taxonomy(path)

        self.assertEqual(len(records), 1)
        self.assertEqual(records[0]["names"]["en"], "chickpeas")
        self.assertEqual(records[0]["names"]["ar"], "حمص")
        self.assertEqual(records[0]["aliases"]["en"], ["chickpea"])
        self.assertEqual(records[0]["parents"], ["legumes"])
        self.assertEqual(records[0]["sourceProperties"]["usdaFdcCode"], "173756")

    def test_foodon_named_class_and_synonym(self):
        owl = (
            '<?xml version="1.0"?>'
            '<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" '
            'xmlns:owl="http://www.w3.org/2002/07/owl#" '
            'xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#" '
            'xmlns:oboInOwl="http://www.geneontology.org/formats/oboInOwl#">'
            '<owl:Class rdf:about="http://purl.obolibrary.org/obo/FOODON_00001234">'
            '<rdfs:label>chickpea</rdfs:label>'
            '<oboInOwl:hasExactSynonym>garbanzo bean</oboInOwl:hasExactSynonym>'
            '</owl:Class></rdf:RDF>'
        )
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "foodon.owl"
            path.write_text(owl, encoding="utf-8")
            records = parse_foodon_owl(path)

        self.assertIn("FOODON_00001234", records)
        self.assertEqual(records["FOODON_00001234"]["name"], "chickpea")
        self.assertIn("garbanzo bean", records["FOODON_00001234"]["aliases"])

    def test_category_keeps_canned_legumes_distinct(self):
        self.assertEqual(
            category_for(["canned chickpeas", "chickpeas", "canned legumes"]),
            ("canned_preserved", "canned_legumes"),
        )

    def test_normalization_handles_arabic_diacritics_and_punctuation(self):
        self.assertEqual(normalize_label("  الطَّمَاطِم! "), "الطماطم")

    def test_english_source_name_removes_embedded_quantity(self):
        self.assertEqual(
            _clean_english_ingredient("Canned crushed tomatoes: 1 can (400 g)"),
            "Canned crushed tomatoes",
        )
        self.assertEqual(_clean_english_ingredient("2 limes"), "limes")
        self.assertEqual(
            _clean_english_ingredient("Chicken thigh, skinless, cooked: 600 g cooked"),
            "Chicken thigh, skinless, cooked",
        )

    def test_safe_base_variant_strips_preparation_but_not_canning(self):
        self.assertEqual(
            ingredient_name_variants("Chicken thigh, skinless, cooked"),
            ["Chicken thigh, skinless, cooked", "Chicken thigh"],
        )
        self.assertEqual(
            ingredient_name_variants("Canned crushed tomatoes"),
            ["Canned crushed tomatoes"],
        )
        self.assertEqual(
            ingredient_name_variants("Tuna, canned in water"),
            ["Tuna, canned in water"],
        )


class FoodCatalogueBuildTests(unittest.TestCase):
    def test_recipe_ingredient_uses_existing_room_id_and_map(self):
        recipe_data = {
            "recipes": [{
                "id": "unitools:recipe-one",
                "ingredients": [{
                    "id": "food-ingredient-123",
                    "name": "حمص",
                    "sourceNameEn": "chickpeas",
                    "quantity": 200,
                    "unit": "g",
                }],
            }]
        }
        off = [{
            "name": "chickpeas",
            "names": {"ar": "حمص", "en": "chickpeas", "fr": "pois chiches"},
            "aliases": {"ar": [], "en": ["chickpea"], "fr": []},
            "allNames": {"ar": ["حمص"], "en": ["chickpeas", "chickpea"], "fr": ["pois chiches"]},
            "parents": ["legumes"],
            "sourceProperties": {},
        }]
        foodon = {}
        catalogue, mapping, audit = build_catalogue(recipe_data, off, foodon)

        self.assertEqual(len(catalogue["products"]), 1)
        product = catalogue["products"][0]
        existing_id = "orc-ing-unitools-food-ingredient-123"
        self.assertEqual(product["id"], existing_id)
        self.assertEqual(product["recipeIngredientIds"], [existing_id])
        self.assertEqual(mapping["recipeIngredientToProductId"][existing_id], existing_id)
        self.assertEqual(product["categoryId"], "foodcat-legumes")
        self.assertEqual(product["subcategoryId"], "foodsub-legumes-chickpeas")
        self.assertIn("g", product["acceptedUnits"])
        self.assertEqual(audit["catalogueCounts"]["duplicateIds"], 0)

    def test_recipe_only_product_is_not_lost(self):
        recipe_data = {
            "recipes": [{
                "id": "focus:recipe-one",
                "ingredients": [{
                    "name": "مكوّن تجريبي",
                    "sourceTextEn": "2 cups experimental food",
                    "quantity": 2,
                    "unit": "cup",
                }],
            }]
        }
        catalogue, mapping, audit = build_catalogue(recipe_data, [], {})
        self.assertEqual(len(catalogue["products"]), 1)
        product = catalogue["products"][0]
        self.assertTrue(product["id"].startswith("orc-ing-"))
        self.assertEqual(len(mapping["recipeIngredientToProductId"]), 1)
        self.assertEqual(audit["catalogueCounts"]["unmatchedRecipeGroups"], 1)

    def test_focus_legacy_id_is_reproducible(self):
        item = {"name": "حمص", "quantity": 1, "unit": "kg"}
        self.assertEqual(room_ingredient_id(item, "focus:demo"), room_ingredient_id(item, "focus:demo"))
        self.assertEqual(len(room_ingredient_id(item, "focus:demo")), len("orc-ing-") + 20)


if __name__ == "__main__":
    unittest.main()
