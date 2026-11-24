import io
import unittest
from unittest.mock import patch
from urllib.request import Request

from tools.translate_food_catalogue import (
    PipelineError,
    build_translation_tasks,
    request_apps_script_response,
)


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



class FakeResponse:
    def __init__(self, status, url, body=b"", headers=None):
        self.status = status
        self.url = url
        self.body = io.BytesIO(body)
        self.headers = headers or {}

    def getcode(self):
        return self.status

    def geturl(self):
        return self.url

    def read(self):
        return self.body.read()

    def close(self):
        self.body.close()


class AppsScriptRedirectTests(unittest.TestCase):
    endpoint = "https://script.google.com/macros/s/test-deployment/exec"
    content_url = (
        "https://script.googleusercontent.com/macros/echo"
        "?user_content_key=private-response-key"
    )

    def make_post(self):
        return Request(
            self.endpoint,
            data=b'{"kind":"food_items"}',
            headers={"Content-Type": "application/json"},
            method="POST",
        )

    @patch("tools.translate_food_catalogue.time.sleep")
    @patch("tools.translate_food_catalogue._open_without_redirects")
    def test_content_host_404_retries_same_get_without_reposting(
        self, open_request, sleep
    ):
        open_request.side_effect = [
            FakeResponse(302, self.endpoint, headers={"Location": self.content_url}),
            FakeResponse(404, self.content_url, b"<html>temporary 404</html>"),
            FakeResponse(200, self.content_url, b'{"ok":true,"results":[]}'),
        ]

        status, body, response_url = request_apps_script_response(
            self.make_post(), timeout_seconds=10
        )

        self.assertEqual(status, 200)
        self.assertEqual(body, b'{"ok":true,"results":[]}')
        self.assertEqual(response_url, self.content_url)
        methods = [call.args[0].get_method() for call in open_request.call_args_list]
        self.assertEqual(methods, ["POST", "GET", "GET"])
        sleep.assert_called_once_with(3)

    @patch("tools.translate_food_catalogue.time.sleep")
    @patch("tools.translate_food_catalogue._open_without_redirects")
    def test_deployment_url_404_is_not_misclassified_as_content_host_failure(
        self, open_request, sleep
    ):
        open_request.return_value = FakeResponse(
            404, self.endpoint, b"<html>deployment not found</html>"
        )

        status, body, response_url = request_apps_script_response(
            self.make_post(), timeout_seconds=10
        )

        self.assertEqual(status, 404)
        self.assertIn(b"deployment not found", body)
        self.assertEqual(response_url, self.endpoint)
        open_request.assert_called_once()
        sleep.assert_not_called()

    @patch("tools.translate_food_catalogue._open_without_redirects")
    def test_redirect_back_to_exec_is_stopped(self, open_request):
        open_request.return_value = FakeResponse(
            302, self.endpoint, headers={"Location": self.endpoint}
        )

        with self.assertRaisesRegex(PipelineError, "redirected back"):
            request_apps_script_response(self.make_post(), timeout_seconds=10)

        open_request.assert_called_once()

    @patch("tools.translate_food_catalogue.time.sleep")
    @patch("tools.translate_food_catalogue._open_without_redirects")
    def test_persistent_content_host_404_does_not_resend_post(
        self, open_request, sleep
    ):
        open_request.side_effect = [
            FakeResponse(302, self.endpoint, headers={"Location": self.content_url}),
            FakeResponse(404, self.content_url),
            FakeResponse(404, self.content_url),
            FakeResponse(404, self.content_url),
            FakeResponse(404, self.content_url),
        ]

        status, _, response_url = request_apps_script_response(
            self.make_post(), timeout_seconds=10
        )

        methods = [call.args[0].get_method() for call in open_request.call_args_list]
        self.assertEqual(status, 404)
        self.assertEqual(response_url, self.content_url)
        self.assertEqual(methods, ["POST", "GET", "GET", "GET", "GET"])
        self.assertEqual([call.args[0] for call in sleep.call_args_list], [3, 6, 9])




if __name__ == "__main__":
    unittest.main()
