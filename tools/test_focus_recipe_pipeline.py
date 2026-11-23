import unittest

from tools.translate_focus_recipe_corpus import (
    adapt_recipe,
    alcohol_match,
    build_output_recipe,
    build_taxonomy_translation_request,
    build_translation_request,
    collect_taxonomy_terms,
    map_taxonomy_translation,
    has_unresolved_pork,
    parse_ingredient_line,
    substitute_pork,
)


class FocusRecipeSafetyTests(unittest.TestCase):
    def recipe(self, **overrides):
        value = {
            "id": "test-id",
            "title_es": "Receta de prueba",
            "title_en": "Test recipe",
            "description_es": "",
            "description_en": "",
            "ingredients_es": ["Tomate: 2 medianos"],
            "ingredients_en": ["Tomato: 2 medium"],
            "steps_es": ["Cocina hasta que esté listo."],
            "steps_en": ["Cook until ready."],
            "categories": ["dinner"],
            "dietary": [],
        }
        value.update(overrides)
        return value

    def test_rejects_wine_in_ingredient_or_method(self):
        self.assertIsNotNone(alcohol_match(self.recipe(ingredients_es=["Vino tinto: 1 taza"])))
        self.assertIsNotNone(alcohol_match(self.recipe(steps_en=["Deglaze the pan with red wine."])))
        adapted, _, reason = adapt_recipe(self.recipe(ingredients_en=["White wine: 1/2 cup"]))
        self.assertIsNone(adapted)
        self.assertTrue(reason.startswith("alcohol"))

    def test_rejects_other_alcoholic_cooking_ingredients(self):
        self.assertIsNotNone(alcohol_match(self.recipe(ingredients_en=["Beer: 1/2 cup"])))
        self.assertIsNotNone(alcohol_match(self.recipe(ingredients_es=["Ron: 2 cucharadas"])))
        self.assertIsNotNone(alcohol_match(self.recipe(ingredients_en=["Wine: 30 ml"])))
        self.assertIsNotNone(alcohol_match(self.recipe(ingredients_es=["Salsa con alcohol: 1 cucharada"])))

    def test_pork_substitutions_cover_english_and_spanish(self):
        english, changes = substitute_pork("Pork tenderloin with bacon and pancetta")
        self.assertIn("beef tenderloin", english.lower())
        self.assertIn("smoked beef strips", english.lower())
        self.assertIn("smoked beef cubes", english.lower())
        self.assertTrue(changes)
        spanish, _ = substitute_pork("Carne molida de cerdo con tocino y manteca de cerdo")
        self.assertIn("carne molida de res", spanish.lower())
        self.assertIn("tiras de res ahumadas", spanish.lower())
        self.assertIn("aceite de oliva", spanish.lower())
        self.assertFalse(has_unresolved_pork(spanish))

    def test_adapts_common_pork_cured_products(self):
        adapted, changes, reason = adapt_recipe(self.recipe(
            ingredients_es=["Jamón serrano: 100 g", "Morcilla: 1 pieza", "Chicharrón: 100 g"],
            ingredients_en=["Pork rinds: 100 g", "Blood sausage: 1 link", "Guanciale: 50 g"],
        ))
        self.assertIsNotNone(adapted)
        self.assertIsNone(reason)
        self.assertIn("jamón de res", adapted["ingredients_es"][0].lower())
        self.assertIn("morcilla de res", adapted["ingredients_es"][1].lower())
        self.assertIn("chicharrón de res", adapted["ingredients_es"][2].lower())
        self.assertIn("crispy beef cracklings", adapted["ingredients_en"][0].lower())
        self.assertIn("beef blood sausage", adapted["ingredients_en"][1].lower())
        self.assertIn("smoked beef cheek", adapted["ingredients_en"][2].lower())
        self.assertTrue(changes)

    def test_adapts_pork_recipe_in_both_languages(self):
        adapted, changes, reason = adapt_recipe(self.recipe(
            title_es="Lomo de cerdo",
            title_en="Pork tenderloin",
            ingredients_es=["Lomo de cerdo: 500 g"],
            ingredients_en=["Pork tenderloin: 500 g"],
        ))
        self.assertIsNone(reason)
        self.assertTrue(changes)
        self.assertIn("res", adapted["title_es"].lower())
        self.assertIn("beef", adapted["title_en"].lower())

    def test_rejects_pork_reference_that_cannot_be_safely_resolved(self):
        adapted, _, reason = adapt_recipe(self.recipe(
            ingredients_es=["Gelatina porcina de origen no especificado"],
        ))
        self.assertIsNone(adapted)
        self.assertIn("pork", reason.lower())

    def test_rejects_porcinely_derived_non_gelatin_items(self):
        adapted, _, reason = adapt_recipe(self.recipe(ingredients_en=["Porcine casing: 1 length"]))
        self.assertIsNone(adapted)
        self.assertIn("pork", reason.lower())

    def test_rejects_unspecified_gelatin_but_allows_bovine_gelatin(self):
        adapted, _, reason = adapt_recipe(self.recipe(ingredients_en=["Gelatin: 1 packet"]))
        self.assertIsNone(adapted)
        self.assertIn("gelatin", reason.lower())
        adapted, _, reason = adapt_recipe(self.recipe(ingredients_en=["Bovine gelatin: 1 packet"]))
        self.assertIsNotNone(adapted)
        self.assertIsNone(reason)

    def test_preserves_beef_chorizo_and_other_non_pork_products(self):
        adapted, _, reason = adapt_recipe(self.recipe(
            ingredients_es=["Chorizo de res: 200 g", "Morcilla de res: 1 pieza"],
        ))
        self.assertIsNotNone(adapted)
        self.assertIsNone(reason)

    def test_preserves_known_non_pork_meats(self):
        adapted, changes, reason = adapt_recipe(self.recipe(
            ingredients_en=["Turkey bacon: 2 slices", "Chicken sausage: 1 link"],
        ))
        self.assertIsNone(reason)
        self.assertEqual(adapted["ingredients_en"], ["Turkey bacon: 2 slices", "Chicken sausage: 1 link"])
        self.assertEqual(changes, [])

    def test_taxonomy_labels_are_translated_separately_from_english(self):
        source = self.recipe(
            id="focus-taxonomy",
            title_es="Tostadas de aguacate",
            description_es="Desayuno rápido",
            ingredients_es=[],
            ingredients_en=[],
            steps_es=["Tuesta el pan."],
            steps_en=["Toast the bread."],
            cuisine="brazilian",
            categories=["breakfast", "quick"],
            dietary=["vegetarian", "high protein"],
            servings=2,
            prep_minutes=5,
            cook_minutes=0,
        )
        recipe_request = build_translation_request(source)
        self.assertEqual(recipe_request["sourceLanguage"], "es")
        self.assertNotIn("tags", recipe_request)

        terms = collect_taxonomy_terms([(source, [])])
        self.assertEqual(terms, ["brazilian", "breakfast", "quick", "vegetarian", "high protein"])
        taxonomy_request = build_taxonomy_translation_request(terms)
        self.assertEqual(taxonomy_request["sourceLanguage"], "en")
        self.assertEqual(taxonomy_request["tags"], terms)
        taxonomy_ar = map_taxonomy_translation(
            terms,
            {"tags": ["برازيلي", "فطور", "سريع", "نباتي", "غني بالبروتين"]},
        )
        result = build_output_recipe(
            source,
            {
                "name": "توست الأفوكادو",
                "summary": "فطور سريع",
                "ingredients": [],
                "steps": ["حمّص الخبز."],
            },
            [],
            taxonomy_ar,
        )
        self.assertEqual(result["category"], "فطور")
        self.assertEqual(result["cuisine"], "برازيلي")
        self.assertEqual(result["categoriesAr"], ["فطور", "سريع"])
        self.assertEqual(result["dietaryAr"], ["نباتي", "غني بالبروتين"])
        self.assertEqual(result["sourceRecord"]["categories"], ["breakfast", "quick"])

    def test_keeps_non_alcoholic_vinegar(self):
        self.assertIsNone(alcohol_match(self.recipe(ingredients_en=["Apple cider vinegar: 1 tbsp"])))

    def test_preserves_ingredient_quantity_information(self):
        parsed = parse_ingredient_line("Pulpa de açaí sin azúcar: 2 paquetes congelados")
        self.assertEqual(parsed["name"], "Pulpa de açaí sin azúcar")
        self.assertEqual(parsed["quantity"], 2)
        self.assertEqual(parsed["unit"], "pcs")
        self.assertEqual(parsed["quantityText"], "2 paquetes congelados")
        parsed = parse_ingredient_line("200 g de bacalao seco")
        self.assertEqual(parsed["name"], "bacalao seco")
        self.assertEqual(parsed["quantity"], 200)
        self.assertEqual(parsed["unit"], "g")
        parsed = parse_ingredient_line("2 eggs")
        self.assertEqual(parsed["name"], "eggs")
        self.assertEqual(parsed["quantity"], 2)
        self.assertEqual(parsed["unit"], "pcs")


if __name__ == "__main__":
    unittest.main()
