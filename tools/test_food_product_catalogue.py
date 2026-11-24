import hashlib
import tempfile
import unittest
import uuid
from pathlib import Path

from tools.build_food_product_catalogue import (
    build_catalogue,
    category_for,
    normalize_label,
    normalize_recipe_unit,
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

    def test_off_taxonomy_reads_explicit_country_of_origin_only(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "ingredients.txt"
            path.write_text(
                "en: olives\n"
                "ar: زيتون\n"
                "fr: olives\n"
                "origin:en: fr: france, it: italy\n\n",
                encoding="utf-8",
            )
            records = parse_off_taxonomy(path)

        self.assertEqual(
            records[0]["sourceProperties"]["countryOfOrigin"],
            ["France", "Italy"],
        )

    def test_foodon_named_class_and_synonym(self):
        owl = (
            '<?xml version="1.0"?>'
            '<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" '
            'xmlns:owl="http://www.w3.org/2002/07/owl#" '
            'xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#" '
            'xmlns:oboInOwl="http://www.geneontology.org/formats/oboInOwl#" '
            'xmlns:xml="http://www.w3.org/XML/1998/namespace">'
            '<owl:Class rdf:about="http://purl.obolibrary.org/obo/FOODON_00001234">'
            '<rdfs:label>chickpea</rdfs:label>'
            '<rdfs:label xml:lang="ar">حمص</rdfs:label>'
            '<rdfs:label xml:lang="fr">Pois chiche</rdfs:label>'
            '<oboInOwl:hasExactSynonym>garbanzo bean</oboInOwl:hasExactSynonym>'
            '<oboInOwl:hasExactSynonym xml:lang="ar">حبوب الحمص</oboInOwl:hasExactSynonym>'
            '</owl:Class></rdf:RDF>'
        )
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "foodon.owl"
            path.write_text(owl, encoding="utf-8")
            records = parse_foodon_owl(path)

        self.assertIn("FOODON_00001234", records)
        self.assertEqual(records["FOODON_00001234"]["name"], "chickpea")
        self.assertIn("garbanzo bean", records["FOODON_00001234"]["aliases"])
        self.assertEqual(records["FOODON_00001234"]["names"]["ar"], "حمص")
        self.assertEqual(records["FOODON_00001234"]["names"]["fr"], "Pois chiche")
        self.assertIn("حبوب الحمص", records["FOODON_00001234"]["aliasesByLanguage"]["ar"])

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
        catalogue, mapping, audit, registry = build_catalogue(recipe_data, off, foodon, {})
        
        self.assertEqual(len(catalogue["products"]), 1)
        product = catalogue["products"][0]
        existing_id = "orc-ing-unitools-food-ingredient-123"
        self.assertEqual(product["recipeIngredientIds"], [existing_id])
        uuid_value = uuid.UUID(product["id"])
        self.assertEqual(uuid_value.version, 4)
        self.assertEqual(mapping["recipeIngredientToProductId"][existing_id], product["id"])
        self.assertEqual(mapping["legacyRoomIngredientIdToFoodItemId"][existing_id], product["id"])
        self.assertEqual(mapping["recipeRows"][0]["legacyRoomIngredientId"], existing_id)
        self.assertEqual(mapping["recipeRows"][0]["foodItemId"], product["id"])
        self.assertEqual(registry["idFormat"], "UUIDv4")

        rebuilt, rebuilt_mapping, _, registry = build_catalogue(recipe_data, off, foodon, registry)
        self.assertEqual(rebuilt["products"][0]["id"], product["id"])
        self.assertEqual(rebuilt_mapping["recipeIngredientToProductId"][existing_id], product["id"])
        self.assertEqual(product["categoryId"], "foodcat-legumes")
        self.assertEqual(product["subcategoryId"], "foodsub-legumes-chickpeas")
        self.assertIn("g", product["acceptedUnits"])
        self.assertIn("kg", product["acceptedUnits"])
        self.assertIn("piece", product["acceptedUnits"])
        self.assertEqual(audit["catalogueCounts"]["duplicateIds"], 0)

    def test_recipe_ingredient_matches_foodon_product_when_off_has_no_term(self):
        root = "FOODON_00001002"
        child = "FOODON_00001234"
        owl = (
            '<?xml version="1.0"?>'
            '<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" '
            'xmlns:owl="http://www.w3.org/2002/07/owl#" '
            'xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#" '
            'xmlns:oboInOwl="http://www.geneontology.org/formats/oboInOwl#">'
            f'<owl:Class rdf:about="http://purl.obolibrary.org/obo/{root}">'
            '<rdfs:label>food product</rdfs:label>'
            '</owl:Class>'
            f'<owl:Class rdf:about="http://purl.obolibrary.org/obo/{child}">'
            '<rdfs:label>chickpea</rdfs:label>'
            '<oboInOwl:hasExactSynonym>garbanzo bean</oboInOwl:hasExactSynonym>'
            f'<rdfs:subClassOf rdf:resource="http://purl.obolibrary.org/obo/{root}"/>'
            '</owl:Class></rdf:RDF>'
        )
        with tempfile.TemporaryDirectory() as directory:
            owl_path = Path(directory) / "foodon.owl"
            owl_path.write_text(owl, encoding="utf-8")
            foodon = parse_foodon_owl(owl_path)

        recipe_data = {
            "recipes": [{
                "id": "unitools:recipe-foodon-only",
                "ingredients": [{
                    "id": "garbanzo-bean",
                    "name": "حمص",
                    "sourceNameEn": "garbanzo bean",
                    "quantity": 1,
                    "unit": "cup",
                }],
            }]
        }
        catalogue, mapping, audit, registry = build_catalogue(recipe_data, [], foodon, {})
        self.assertEqual(len(catalogue["products"]), 1)
        product = catalogue["products"][0]
        self.assertEqual(product["categoryId"], "foodcat-legumes")
        self.assertIn("kg", product["acceptedUnits"])
        self.assertIn("g", product["acceptedUnits"])
        self.assertTrue(
            product["names"]["en"] == "chickpea"
            or "chickpea" in product["aliases"]["en"]
        )
        self.assertIn(
            "FOODON_00001234",
            product["sourceMappings"]["foodOnIds"],
        )
        self.assertEqual(audit["catalogueCounts"]["foodOnMatchedGroups"], 1)
        self.assertEqual(audit["catalogueCounts"]["unmatchedRecipeGroups"], 0)
        self.assertEqual(
            mapping["recipeIngredientToProductId"]["orc-ing-unitools-garbanzo-bean"],
            product["id"],
        )
        self.assertEqual(registry["idFormat"], "UUIDv4")
        self.assertEqual(mapping["recipeRows"][0]["foodItemId"], product["id"])

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
        catalogue, mapping, audit, registry = build_catalogue(recipe_data, [], {}, {})
        self.assertEqual(len(catalogue["products"]), 1)
        product = catalogue["products"][0]
        self.assertEqual(uuid.UUID(product["id"]).version, 4)
        self.assertEqual(registry["idFormat"], "UUIDv4")
        self.assertEqual(len(mapping["recipeIngredientToProductId"]), 1)
        self.assertEqual(len(mapping["recipeRows"]), 1)
        self.assertEqual(audit["catalogueCounts"]["unmatchedRecipeGroups"], 1)

    def test_focus_legacy_id_matches_android_measure_normalization(self):
        item = {"name": "حمص", "quantity": 1, "unit": "kg"}
        normalized = "حمص|g"
        expected = "orc-ing-" + hashlib.sha256(normalized.encode("utf-8")).digest()[:10].hex()
        self.assertEqual(room_ingredient_id(item, "focus:demo"), expected)
        self.assertEqual(room_ingredient_id(item, "focus:demo"), room_ingredient_id(item, "focus:demo"))

    def test_unit_normalization_matches_android_mapping(self):
        self.assertEqual(normalize_recipe_unit("kg", 1), "g")
        self.assertEqual(normalize_recipe_unit("L", 1), "L")
        self.assertEqual(normalize_recipe_unit("liter", 1), "liter")
        self.assertEqual(normalize_recipe_unit("bunches", 2), "ربطة")
        self.assertEqual(normalize_recipe_unit("pinches", 1), "رشة")
        self.assertEqual(normalize_recipe_unit("", ""), "حسب الحاجة")

    def test_unitools_legacy_id_does_not_slugify_source_id(self):
        item = {"id": "Mixed ID!?", "name": "زيت", "quantity": 1, "unit": "piece"}
        self.assertEqual(
            room_ingredient_id(item, "unitools:demo"),
            "orc-ing-unitools-mixed id!?",
        )


if __name__ == "__main__":
    unittest.main()
