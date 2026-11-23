# Arabic recipe corpus translation

This workflow generates Arabic translations for the 3,013 unique recipes in the
pinned Open Recipe Corpus and saves them in:

    data/recipes/ar/recipes.json

The English text records in this source release are machine-translated from
Polish and overlap the Polish recipe IDs. The workflow translates the Polish
records once, avoiding duplicate Arabic records.

## Why Google Apps Script

The translation bridge uses the built-in Apps Script Language service
(LanguageApp), not Google Cloud Translation. A personal Google account currently
has a published allowance of 5,000 Translate calls per day; Google Workspace
accounts list 20,000 per day. The quota resets 24 hours after the first request
and Google may change it. The pipeline makes one LanguageApp translation call
per recipe by sending the recipe fields together as HTML. A personal account
therefore has enough published daily quota for this corpus in one full run,
subject to any other Apps Script use on the same account.

No credit card, Google Cloud billing account, or paid API key is needed. A
Google account is required to create and deploy the Apps Script bridge.

Official references:
- https://developers.google.com/apps-script/reference/language/language-app
- https://developers.google.com/apps-script/guides/services/quotas
- https://developers.google.com/apps-script/guides/web

## One-time Google setup

1. Open https://script.google.com/home/start while signed into the Google
   account whose free translation quota you want to use.
2. Create a new standalone Apps Script project.
3. Open the Apps Script file in the repository at
   .github/apps-script/recipe_translation_api.gs, copy its entire contents,
   and replace the starter contents of Code.gs with it.
4. In the function selector, run generateMidatiTranslationToken. Approve the
   Google authorization prompt. Open the execution log and copy the value
   printed after MIDATI_TRANSLATION_TOKEN=.
5. Run testMidatiTranslationService once. Confirm the execution log contains
   Arabic output; this authorizes the translation service for your account.
6. Select Deploy > New deployment > Web app. Set Execute as to Me and Who has
   access to Anyone, then deploy and approve the permissions. Copy the URL
   ending in /exec.
7. In the Meals GitHub repository, open Settings > Secrets and variables >
   Actions and create these repository secrets:
   - GOOGLE_APPS_SCRIPT_URL: the deployed /exec URL.
   - GOOGLE_APPS_SCRIPT_TOKEN: the generated token from step 4.

The endpoint is publicly reachable so GitHub Actions can call it without
Google sign-in. Every request must include the random token. The token itself
is kept in Apps Script Properties and GitHub Actions Secrets, never in Git.

If you change Code.gs after deploying, create a new deployment version and
update the URL secret if Google gives you a different /exec URL.

## Run it

Open the Actions tab and choose Translate recipe corpus to Arabic, then select
Run workflow. The default limit is 4,000 new recipes, above the corpus size.
The workflow also runs daily at 03:17 UTC until all source recipe IDs are
translated.

The workflow downloads the same pinned, checksum-verified corpus used by the
debug tester. It skips every recipe ID already present in the output JSON, so
manual reruns and daily continuation do not retranslate successful recipes.
Successful results are saved atomically after each batch. If the daily Google
quota is reached, completed work remains in the JSON and the next scheduled run
continues from the first missing recipe.

The output file includes the translated recipe text, ingredient quantities and
units from source metadata, category/cuisine/tags/allergens, cooking times,
source-language metadata, and recipe-level provenance/licence links. It marks
the content as machine-translated. Review the output before adding it to the
production catalogue.

## Quota and privacy notes

The published personal-account quota is 5,000 Translate calls per day, not
5,000 characters. Each recipe consumes one Translate call. Recipe text is sent
to Google's built-in Language service through the Apps Script project. Do not
send private or user-submitted recipes through this corpus pipeline.

The workflow stores only the Apps Script URL and token as GitHub Actions
Secrets. Never paste the token into a commit, issue, or chat. If the token is
exposed, run generateMidatiTranslationToken again, redeploy if needed, and
replace the GitHub secret.

The source dataset is licensed CC BY-SA 4.0. The generated translations are
adapted material, so recipe-level attribution, source links, licence details,
and change notes are carried into the generated JSON. Keep the required
attribution and share-alike notices if the recipes are reused or redistributed.
