import unittest

from tools.translate_unitools_recipe_corpus import (
    adapt_recipe,
    alcohol_match,
    recipe_english_text,
)


def recipe(name="Test dish", ingredients=None, steps=None):
    return {
        "slug": "test-dish",
        "name": {"en": name, "ru": name},
        "summary": {"en": "A simple dish", "ru": "Блюдо"},
        "ingredients": ingredients or [],
        "steps": steps or [{"text": {"en": "Cook until tender", "ru": "Готовьте"}, "minutes": 10}],
    }


class UnitoolsSafetyTests(unittest.TestCase):
    def test_rejects_wine_in_ingredients(self):
        item = recipe(ingredients=[{"id": "wine", "name": {"en": "White wine"}}])
        self.assertIsNotNone(alcohol_match(item))
        self.assertIsNone(adapt_recipe(item)[0])

    def test_rejects_alcohol_in_method(self):
        item = recipe(steps=[{"text": {"en": "Deglaze the pan with red wine"}, "minutes": 3}])
        self.assertIsNone(adapt_recipe(item)[0])

    def test_excludes_unverified_gelatin(self):
        item = recipe(ingredients=[{"id": "gelatin", "name": {"en": "Gelatin"}}])
        adapted, _, reason = adapt_recipe(item)
        self.assertIsNone(adapted)
        self.assertIn("gelatin", reason)

    def test_adapts_common_pork_cuts(self):
        item = recipe(
            ingredients=[
                {"id": "pork", "name": {"en": "Pork chops"}},
                {"id": "bacon", "name": {"en": "Bacon"}},
                {"id": "lard", "name": {"en": "Lard"}},
            ]
        )
        adapted, changes, reason = adapt_recipe(item)
        self.assertIsNone(reason)
        self.assertEqual(adapted["ingredients"][0]["name"]["en"], "lamb chops")
        self.assertEqual(adapted["ingredients"][1]["name"]["en"], "beef bacon")
        self.assertEqual(adapted["ingredients"][2]["name"]["en"], "olive oil")
        self.assertTrue(changes)

    def test_preserves_explicit_non_pork_meats(self):
        item = recipe(
            ingredients=[
                {"id": "turkey", "name": {"en": "Turkey bacon"}},
                {"id": "beef-sausage", "name": {"en": "Beef sausage"}},
            ]
        )
        adapted, _, reason = adapt_recipe(item)
        self.assertIsNone(reason)
        self.assertEqual(adapted["ingredients"][0]["name"]["en"], "Turkey bacon")
        self.assertEqual(adapted["ingredients"][1]["name"]["en"], "Beef sausage")

    def test_pork_sausage_is_not_double_replaced(self):
        item = recipe(ingredients=[{"id": "sausage", "name": {"en": "Pork sausage"}}])
        adapted, _, reason = adapt_recipe(item)
        self.assertIsNone(reason)
        self.assertEqual(adapted["ingredients"][0]["name"]["en"], "beef sausage")

    def test_replaces_unspecified_sausage(self):
        item = recipe(ingredients=[{"id": "sausage", "name": {"en": "Sausage"}}])
        adapted, _, reason = adapt_recipe(item)
        self.assertIsNone(reason)
        self.assertEqual(adapted["ingredients"][0]["name"]["en"], "beef sausage")

    def test_removes_legacy_pork_references(self):
        item = recipe(
            name="Pork stew",
            ingredients=[{"id": "meat", "name": {"en": "Pork shoulder"}}],
        )
        adapted, _, reason = adapt_recipe(item)
        self.assertIsNone(reason)
        self.assertNotIn("pork", " ".join(recipe_english_text(adapted)).lower())

    def test_keeps_pork_free_statement(self):
        item = recipe(
            summary={"en": "A pork-free vegetable meal", "ru": ""},
            ingredients=[{"id": "oil", "name": {"en": "Olive oil"}}],
        )
        adapted, _, reason = adapt_recipe(item)
        self.assertIsNone(reason)
        self.assertIn("pork-free", adapted["summary"]["en"])

    def test_does_not_change_source_original_object(self):
        item = recipe(ingredients=[{"id": "pork", "name": {"en": "Pork ribs"}}])
        adapted, _, reason = adapt_recipe(item)
        self.assertIsNone(reason)
        self.assertEqual(item["ingredients"][0]["name"]["en"], "Pork ribs")
        self.assertEqual(adapted["ingredients"][0]["name"]["en"], "beef ribs")


if __name__ == "__main__":
    unittest.main()
