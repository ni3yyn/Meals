import unittest

from tools.market_product_matching.pipeline import (
    build_candidate_rows,
    build_mapping_payload,
    normalize_text,
)


SALT_ID = "11111111-1111-4111-8111-111111111111"
CANNED_CHICKPEAS_ID = "22222222-2222-4222-8222-222222222222"
PLAIN_CHICKPEAS_ID = "33333333-3333-4333-8333-333333333333"


class ProductIngredientPipelineTests(unittest.TestCase):
    def test_arabic_normalization_removes_diacritics_and_tatweel(self):
        self.assertEqual(normalize_text("اَلْمِلْــــح"), "الملح")

    def test_canned_product_does_not_match_a_generic_or_dried_ingredient(self):
        products = [{
            "id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            "name": "Pois chiches en conserve",
            "nameVariants": ["Canned chickpeas"],
            "packageQuantity": 400.0,
            "packageUnit": "g",
        }]
        foods = [
            {
                "id": CANNED_CHICKPEAS_ID,
                "names": {"ar": "حمص معلب", "en": "Canned chickpeas", "fr": "Pois chiches en conserve"},
                "aliases": {"ar": [], "en": [], "fr": []},
                "categoryId": "foodcat-canned",
                "subcategoryId": "foodsub-canned-legumes",
                "acceptedUnits": ["g", "kg", "can"],
            },
            {
                "id": PLAIN_CHICKPEAS_ID,
                "names": {"ar": "حمص", "en": "Chickpea", "fr": "Pois chiche"},
                "aliases": {"ar": [], "en": [], "fr": []},
                "categoryId": "foodcat-legumes",
                "subcategoryId": "foodsub-legumes-dry",
                "acceptedUnits": ["g", "kg"],
            },
        ]
        rows = build_candidate_rows(products, foods, {CANNED_CHICKPEAS_ID, PLAIN_CHICKPEAS_ID})
        self.assertEqual([row["ingredient_id"] for row in rows], [CANNED_CHICKPEAS_ID])
        self.assertEqual(rows[0]["candidate_score"], "100")
        self.assertEqual(rows[0]["review_decision"], "")

    def test_incompatible_package_unit_is_not_suggested(self):
        products = [{
            "id": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
            "name": "Salt",
            "packageQuantity": 1.0,
            "packageUnit": "l",
        }]
        foods = [{
            "id": SALT_ID,
            "names": {"ar": "ملح", "en": "Salt", "fr": "Sel"},
            "aliases": {"ar": [], "en": [], "fr": []},
            "categoryId": "foodcat-pantry",
            "subcategoryId": "foodsub-pantry-salt",
            "acceptedUnits": ["g", "kg"],
        }]
        self.assertEqual(build_candidate_rows(products, foods, {SALT_ID}), [])

    def test_generated_candidates_remain_suggested_until_explicit_approval(self):
        product_id = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
        products = [{"id": product_id, "name": "Salt", "foodScopeReviewNeeded": False}]
        foods = [{
            "id": SALT_ID,
            "names": {"ar": "ملح", "en": "Salt", "fr": "Sel"},
            "aliases": {"ar": [], "en": [], "fr": []},
            "categoryId": "foodcat-pantry",
            "subcategoryId": "foodsub-pantry-salt",
            "acceptedUnits": ["g", "kg"],
        }]
        candidates = build_candidate_rows(products, foods, {SALT_ID})
        generated = build_mapping_payload(
            products,
            candidates,
            candidates,
            source_catalogue_revision="a" * 64,
            generated_at_epoch_millis=1234,
        )
        self.assertEqual(generated["mappings"][0]["status"], "SUGGESTED")
        self.assertIsNone(generated["mappings"][0]["reviewedAtEpochMillis"])

        reviewed = [dict(candidates[0], review_decision="APPROVE", review_note="Exact product identity reviewed.")]
        approved = build_mapping_payload(
            products,
            candidates,
            reviewed,
            source_catalogue_revision="a" * 64,
            generated_at_epoch_millis=1234,
        )
        self.assertEqual(approved["mappings"][0]["status"], "VERIFIED")
        self.assertEqual(approved["mappings"][0]["matchMethod"], "MANUAL")
        self.assertEqual(approved["mappings"][0]["reviewedAtEpochMillis"], 1234)

    def test_food_scope_review_products_cannot_be_approved(self):
        product_id = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
        products = [{
            "id": product_id,
            "name": "Salt",
            "foodScopeReviewNeeded": True,
        }]
        foods = [{
            "id": SALT_ID,
            "names": {"ar": "ملح", "en": "Salt", "fr": "Sel"},
            "aliases": {"ar": [], "en": [], "fr": []},
            "categoryId": "foodcat-pantry",
            "subcategoryId": "foodsub-pantry-salt",
            "acceptedUnits": ["g", "kg"],
        }]
        candidates = build_candidate_rows(products, foods, {SALT_ID})
        reviewed = [dict(candidates[0], review_decision="APPROVE")]
        with self.assertRaisesRegex(ValueError, "food-scope review"):
            build_mapping_payload(
                products,
                candidates,
                reviewed,
                source_catalogue_revision="b" * 64,
                generated_at_epoch_millis=1234,
            )

    def test_multiple_approvals_for_one_product_are_rejected(self):
        product_id = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
        food_b = "ffffffff-ffff-4fff-8fff-ffffffffffff"
        products = [{"id": product_id, "name": "Salt"}]
        foods = [
            {
                "id": SALT_ID, "names": {"ar": "ملح", "en": "Salt", "fr": "Sel"},
                "aliases": {"ar": [], "en": [], "fr": []}, "categoryId": "foodcat-pantry",
                "subcategoryId": "foodsub-pantry-salt", "acceptedUnits": ["g", "kg"],
            },
            {
                "id": food_b, "names": {"ar": "ملح طعام", "en": "Table salt", "fr": "Sel de table"},
                "aliases": {"ar": [], "en": [], "fr": []}, "categoryId": "foodcat-pantry",
                "subcategoryId": "foodsub-pantry-salt", "acceptedUnits": ["g", "kg"],
            },
        ]
        candidates = build_candidate_rows(products, foods, {SALT_ID, food_b})
        reviewed = [
            dict(row, review_decision="APPROVE")
            for row in candidates[:2]
        ]
        with self.assertRaisesRegex(ValueError, "Only one ingredient"):
            build_mapping_payload(
                products,
                candidates,
                reviewed,
                source_catalogue_revision="c" * 64,
                generated_at_epoch_millis=1234,
            )


if __name__ == "__main__":
    unittest.main()
