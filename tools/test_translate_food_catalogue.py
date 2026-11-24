import unittest

from tools.translate_food_catalogue import build_translation_tasks


class FoodCatalogueTranslationTaskTests(unittest.TestCase):
    def test_missing_arabic_and_french_use_english_source(self):
        catalogue = {
            "products": [{
                "id": "11111111-1111-4111-8111-111111111111",
                "names": {"ar": None, "en": "chickpeas", "fr": ""},
            }]
        }
        tasks, without_source = build_translation_tasks(
            catalogue, {"translations": {}}, ("ar", "fr")
        )

        self.assertEqual(without_source, [])
        self.assertEqual(
            {
                (item["sourceLanguage"], item["targetLanguage"], item["sourceText"])
                for item in tasks
            },
            {("en", "ar", "chickpeas"), ("en", "fr", "chickpeas")},
        )

    def test_missing_english_prefers_arabic_source(self):
        catalogue = {
            "products": [{
                "id": "22222222-2222-4222-8222-222222222222",
                "names": {"ar": "حمص", "en": None, "fr": "Pois chiches"},
            }]
        }
        tasks, without_source = build_translation_tasks(
            catalogue, {"translations": {}}, ("en",)
        )

        self.assertEqual(without_source, [])
        self.assertEqual(len(tasks), 1)
        self.assertEqual(tasks[0]["sourceLanguage"], "ar")
        self.assertEqual(tasks[0]["sourceText"], "حمص")

    def test_saved_translation_for_same_source_is_not_repeated(self):
        product_id = "33333333-3333-4333-8333-333333333333"
        catalogue = {
            "products": [{
                "id": product_id,
                "names": {"ar": None, "en": "tomatoes", "fr": None},
            }]
        }
        sidecar = {
            "translations": {
                product_id: {
                    "ar": {
                        "text": "طماطم",
                        "sourceLanguage": "en",
                        "sourceText": "tomatoes",
                    }
                }
            }
        }
        tasks, _ = build_translation_tasks(catalogue, sidecar, ("ar", "fr"))
        self.assertEqual([task["targetLanguage"] for task in tasks], ["fr"])

    def test_changed_source_label_is_translated_again(self):
        product_id = "44444444-4444-4444-8444-444444444444"
        catalogue = {
            "products": [{
                "id": product_id,
                "names": {"ar": None, "en": "green tomatoes", "fr": None},
            }]
        }
        sidecar = {
            "translations": {
                product_id: {
                    "ar": {
                        "text": "طماطم",
                        "sourceLanguage": "en",
                        "sourceText": "tomatoes",
                    }
                }
            }
        }
        tasks, _ = build_translation_tasks(catalogue, sidecar, ("ar",))
        self.assertEqual(len(tasks), 1)
        self.assertEqual(tasks[0]["sourceText"], "green tomatoes")

    def test_items_without_any_label_are_not_fabricated(self):
        catalogue = {
            "products": [{
                "id": "55555555-5555-4555-8555-555555555555",
                "names": {"ar": None, "en": None, "fr": None},
            }]
        }
        tasks, without_source = build_translation_tasks(
            catalogue, {"translations": {}}, ("ar", "en", "fr")
        )
        self.assertEqual(tasks, [])
        self.assertEqual(without_source, ["55555555-5555-4555-8555-555555555555"])


if __name__ == "__main__":
    unittest.main()
