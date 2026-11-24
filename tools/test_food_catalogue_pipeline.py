import unittest

from tools.build_food_catalogue import (
    build_catalogue, clean_recipe_ingredient, classify_food,
    normalize_term, parse_foodon_tsv, parse_off_taxonomy
)


class FoodCataloguePipelineTests(unittest.TestCase):
    def test_off_taxonomy_reads_trilingual_labels_aliases_and_origin(self):
        source = """< en: dairy
en: milk, cow milk
ar: حليب
fr: lait
origin:en: en:ireland

< en: legume
en: chickpeas, garbanzo beans
ar: حمص
fr: pois chiches
"""
        entries = parse_off_taxonomy(source)
        milk = next(item for item in entries if item["name_en"] == "milk")
        self.assertIn("dairy", milk["parents"])
        self.assertEqual(milk["names"]["ar"], ["حليب"])
        self.assertEqual(milk["names"]["fr"], ["lait"])
        self.assertEqual(milk["country_of_origin"], ["Ireland"])

    def test_foodon_rdf_tsv_language_suffix_and_parent(self):
        root = "http://purl.obolibrary.org/obo/FOODON_00001002"
        child = "http://purl.obolibrary.org/obo/FOODON_00001014"
        source = (
            "?class\\t?parent\\t?type\\t?label\\n"
            f"<{root}>\\t\\t\\t\\n"
            f"<{root}>\\t\\t\\"label\\"\\t\\"food product\\"@en\\n"
            f"<{child}>\\t<{root}>\\t\\t\\n"
            f"<{child}>\\t\\t\\"label\\"\\t\\"yogurt food product\\"@en\\n"
            f"<{child}>\\t\\t\\"synonym (exact)\\"\\t\\"yoghurt\\"@en\\n"
            f"<{child}>\\t\\t\\"label\\"\\t\\"yaourt\\"@fr\\n"
        )
        nodes, parents = parse_foodon_tsv(source)
        self.assertEqual(nodes["FOODON_00001014"]["names"]["en"][0], "yogurt food product")
        self.assertIn("yoghurt", nodes["FOODON_00001014"]["aliases"]["en"])
        self.assertEqual(nodes["FOODON_00001014"]["names"]["fr"][0], "yaourt")
        self.assertIn("FOODON_00001002", parents["FOODON_00001014"])

    def test_arabic_normalization_and_recipe_preparation_cleanup(self):
        self.assertEqual(normalize_term("أَرُزّ"), normalize_term("ارز"))
        value = clean_recipe_ingredient(
            {"sourceTextEn":"Chicken thigh, skinless, cooked: 560 g cooked"},
            "focus:recipe"
        )
        self.assertEqual(normalize_term(value), "chicken thigh")

    def test_market_food_categories_do_not_confuse_eggplant_or_oil(self):
        self.assertEqual(classify_food(["Eggplant"])[0], "vegetables")
        self.assertEqual(classify_food(["Vegetable oil"])[0], "oils-fats")
        self.assertEqual(classify_food(["Canned chickpeas"]), ("legumes", "canned-legumes"))
        self.assertEqual(classify_food(["Mayonnaise"]), ("sauces-condiments", "mayonnaise-dressings"))

    def test_recipe_rows_map_to_existing_food_item_without_duplicate(self):
        off = parse_off_taxonomy("< en: dairy\\nen: milk, cow milk\\nar: حليب\\nfr: lait\\n")
        root = "FOODON_00001002"
        child = "FOODON_00001014"
        foodon_source = (
            "?class\\t?parent\\t?type\\t?label\\n"
            f"<http://purl.obolibrary.org/obo/{root}>\\t\\t\\t\\n"
            f"<http://purl.obolibrary.org/obo/{root}>\\t\\t\\"label\\"\\t\\"food product\\"@en\\n"
            f"<http://purl.obolibrary.org/obo/{child}>\\t<http://purl.obolibrary.org/obo/{root}>\\t\\t\\n"
            f"<http://purl.obolibrary.org/obo/{child}>\\t\\t\\"label\\"\\t\\"milk\\"@en\\n"
        )
        nodes, parents = parse_foodon_tsv(foodon_source)
        recipes = {
            "complete":True,"expectedCount":1,"translatedCount":1,
            "recipes":[{"id":"unitools:sample","ingredients":[
                {"id":"milk","name":"حليب","sourceNameEn":"Milk","quantity":250,"unit":"ml"}
            ]}]
        }
        items, categories, rows, report = build_catalogue(recipes,off,nodes,parents,[],{"ids":{},"redirects":{}})
        self.assertEqual(len(items),1)
        self.assertEqual(len(rows),1)
        self.assertEqual(rows[0]["foodItemId"],items[0]["id"])
        self.assertEqual(rows[0]["recipeId"],"unitools:sample")
        self.assertIn("ml",items[0]["acceptedUnits"])
        self.assertEqual(items[0]["names"]["ar"],"حليب")
        self.assertEqual(report["recipeRowsFullyMapped"],1)


if __name__ == "__main__":
    unittest.main()
