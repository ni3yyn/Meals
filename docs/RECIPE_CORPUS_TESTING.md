# Local recipe corpus and translation test

This is a temporary, Android-debug-only integration. It does not use Supabase,
Firestore, or a production translation service.

## 1. Download the dataset locally

Requirements: Python 3.10 or newer. Run from the repository root:

\`\`\`bash
python tools/download_recipe_corpus.py
\`\`\`

The script downloads a pinned release of
[Rippy1911/open-recipe-corpus](https://github.com/Rippy1911/open-recipe-corpus)
into \`local-data/open-recipe-corpus/\`. It verifies the Git blob SHA-1 and
expected file size of each data file before writing it.

The corpus contains 3,013 Polish recipe records. It also contains 1,561
English-language text records, but those English texts are machine-translated
from Polish and share recipe IDs with the Polish originals. They are retained
as a separate language view for translation testing, not counted as 1,561
additional independent recipes.

## 2. Build and open the tester

\`\`\`bash
./gradlew assembleDebug
\`\`\`

Open the Recipes tab and select **اختبار الترجمة والبيانات**. The tester loads
the English and Polish text records from debug APK assets. It does not add
these records to the planner, shopping calculations, or production recipe
catalogue.

Open a recipe and request its Arabic translation. The app translates the
title, summary, ingredient names/notes, and each instruction step. Quantities
and units stay in the source metadata and are never sent as translated fields.
Numeric values and common measurement tokens inside instructions are protected
and restored after translation.

Successful translations are cached as JSON files in the app's private files
directory under \`recipe-corpus-translations/\`. Reopening the same recipe
uses the local cache and does not call the translation service again.

## 3. Temporary translation provider

The debug tester currently calls the keyless MyMemory endpoint directly:

\`https://api.mymemory.translated.net/get\`

The anonymous allowance is documented as 5,000 characters per day, with a
500-byte maximum per translation segment. Long fields are split into smaller
segments, and independent recipe fields/segments are translated concurrently
with a four-request limit. Results are reassembled in source order and cached
locally. This is for small-scale quality testing only; provider latency, rate
limits, or weak culinary translations can still affect results.

Do not ship this direct-from-device integration in a production build. Before
release, replace it with a server-side translation adapter and persist
approved translations in the chosen backend. No API secret is embedded in
the app.

## 4. Local-only data and licensing

The downloaded corpus is deliberately excluded from Git by \`.gitignore\`.
Gradle copies it into generated assets for the **debug** variant only. It is
not packaged into release APKs.

The recipe data is licensed CC BY-SA 4.0. Keep recipe-level provenance,
attribution, source URL, history URL, licence, and change notes when
evaluating or reusing a recipe. Adapted translations must retain the
share-alike licence and attribution requirements. See the downloaded
\`LICENSE-DATA.md\` and \`ATTRIBUTION.md\`.

The upstream English records are already machine-translated from Polish; the
tester displays that provenance so the two language versions are not mistaken
for independent originals.
