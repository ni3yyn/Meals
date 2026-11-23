/**
 * Midati's no-card recipe translation endpoint.
 *
 * Deploy as a Web App:
 *   Execute as: Me
 *   Who has access: Anyone
 *
 * The endpoint is protected by a random token stored in Script Properties.
 * It uses one LanguageApp.translate call per recipe, not one call per field.
 */

const MIDATI_TARGET_LANGUAGE = "ar";
const MIDATI_MAX_BATCH_SIZE = 20;
const MIDATI_MAX_EXECUTION_MS = 4 * 60 * 1000;

function doGet() {
  return jsonResponse_({
    ok: true,
    service: "Midati Google Apps Script translation bridge",
    tokenConfigured: Boolean(
      PropertiesService.getScriptProperties().getProperty("MIDATI_TRANSLATION_TOKEN")
    ),
    targetLanguage: MIDATI_TARGET_LANGUAGE
  });
}

function doPost(event) {
  try {
    if (!event || !event.postData || !event.postData.contents) {
      return jsonResponse_({ error: "missing_request_body" });
    }

    const request = JSON.parse(event.postData.contents);
    const configuredToken = PropertiesService.getScriptProperties()
      .getProperty("MIDATI_TRANSLATION_TOKEN");

    if (!configuredToken || request.token !== configuredToken) {
      return jsonResponse_({ error: "unauthorized" });
    }

    const recipes = request.recipes;
    if (!Array.isArray(recipes) || recipes.length < 1 || recipes.length > MIDATI_MAX_BATCH_SIZE) {
      return jsonResponse_({
        error: "invalid_batch_size",
        message: "Send between 1 and " + MIDATI_MAX_BATCH_SIZE + " recipes per request."
      });
    }

    const startedAt = Date.now();
    const results = [];
    const errors = [];
    let quotaExceeded = false;
    let deferred = false;

    for (let index = 0; index < recipes.length; index += 1) {
      if (Date.now() - startedAt > MIDATI_MAX_EXECUTION_MS) {
        deferred = true;
        break;
      }

      const recipe = recipes[index] || {};
      const id = String(recipe.id || "");
      try {
        if (!id) throw new Error("Recipe is missing its ID.");
        const translation = translateRecipe_(recipe);
        results.push({ id: id, translation: translation });
      } catch (error) {
        const message = String(error && error.message ? error.message : error).slice(0, 500);
        const isQuota = /too many times|quota|limit exceeded|service using too much computer time/i
          .test(message);
        errors.push({ id: id, error: message, quotaExceeded: isQuota });
        if (isQuota) {
          quotaExceeded = true;
          break;
        }
      }
    }

    return jsonResponse_({
      ok: errors.length === 0,
      results: results,
      errors: errors,
      quotaExceeded: quotaExceeded,
      deferred: deferred
    });
  } catch (error) {
    return jsonResponse_({
      error: "request_failed",
      message: String(error && error.message ? error.message : error).slice(0, 500)
    });
  }
}

/**
 * Run once in the Apps Script editor, then copy the logged value into the
 * GitHub Actions secret GOOGLE_APPS_SCRIPT_TOKEN.
 */
function generateMidatiTranslationToken() {
  const token = Utilities.getUuid().replace(/-/g, "") +
    Utilities.getUuid().replace(/-/g, "");
  PropertiesService.getScriptProperties()
    .setProperty("MIDATI_TRANSLATION_TOKEN", token);
  Logger.log("MIDATI_TRANSLATION_TOKEN=" + token);
  return token;
}

/** Run once in the editor to authorize LanguageApp and verify Polish -> Arabic. */
function testMidatiTranslationService() {
  const result = LanguageApp.translate("Sól, pieprz i oliwa", "pl", "ar");
  Logger.log("Polish-to-Arabic test: " + result);
  return result;
}

function translateRecipe_(recipe) {
  const sourceLanguage = String(recipe.sourceLanguage || "pl");
  const fields = [];
  const output = {
    name: String(recipe.name || ""),
    summary: String(recipe.summary || ""),
    category: String(recipe.category || ""),
    cuisine: String(recipe.cuisine || ""),
    tags: Array.isArray(recipe.tags) ? recipe.tags.slice() : [],
    allergens: Array.isArray(recipe.allergens) ? recipe.allergens.slice() : [],
    ingredients: Array.isArray(recipe.ingredients)
      ? recipe.ingredients.map(function (item) {
          item = item || {};
          return {
            name: String(item.name || ""),
            note: String(item.note || "")
          };
        })
      : [],
    steps: Array.isArray(recipe.steps) ? recipe.steps.map(String) : []
  };

  addTextField_(fields, "name", output.name);
  addTextField_(fields, "summary", output.summary);
  addTextField_(fields, "category", output.category);
  addTextField_(fields, "cuisine", output.cuisine);

  output.ingredients.forEach(function (ingredient, index) {
    addTextField_(fields, "ingredients." + index + ".name", ingredient.name);
    addTextField_(fields, "ingredients." + index + ".note", ingredient.note);
  });

  output.steps.forEach(function (step, index) {
    addTextField_(fields, "steps." + index, step);
  });
  output.tags.forEach(function (tag, index) {
    addTextField_(fields, "tags." + index, String(tag));
  });
  output.allergens.forEach(function (allergen, index) {
    addTextField_(fields, "allergens." + index, String(allergen));
  });

  if (fields.length === 0) return output;

  const html = fields.map(function (field, index) {
    return '<p id="midati-field-' + index + '">' +
      protectNumbersAsHtml_(field.value) + "</p>";
  }).join("\n");

  const translatedHtml = LanguageApp.translate(
    html,
    sourceLanguage,
    MIDATI_TARGET_LANGUAGE,
    { contentType: "html" }
  );

  fields.forEach(function (field, index) {
    const pattern = new RegExp(
      '<p\\b[^>]*\\bid="midati-field-' + index +
      '"[^>]*>([\\s\\S]*?)<\\/p>',
      "i"
    );
    const match = translatedHtml.match(pattern);
    if (!match) {
      throw new Error("Translation response lost field marker " + index + ".");
    }
    setPath_(output, field.path, htmlToText_(match[1]));
  });

  return output;
}

function addTextField_(fields, path, value) {
  const text = String(value || "");
  if (text.trim()) fields.push({ path: path, value: text });
}

function setPath_(target, path, value) {
  const parts = path.split(".");
  let current = target;
  for (let index = 0; index < parts.length - 1; index += 1) {
    current = current[parts[index]];
  }
  current[parts[parts.length - 1]] = value;
}

function protectNumbersAsHtml_(text) {
  const pattern = /\d+(?:[.,]\d+)?(?:\s?(?:°\s?[CF]|%|g|kg|mg|ml|l|tsp|tbsp|cups?|minutes?|mins?|hours?|hrs?|seconds?|secs?))?/gi;
  let output = "";
  let lastIndex = 0;
  let match;

  while ((match = pattern.exec(text)) !== null) {
    output += escapeHtml_(text.slice(lastIndex, match.index));
    output += '<span class="notranslate" translate="no">' +
      escapeHtml_(match[0]) + "</span>";
    lastIndex = match.index + match[0].length;
  }
  output += escapeHtml_(text.slice(lastIndex));
  return output;
}

function escapeHtml_(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function htmlToText_(value) {
  return String(value)
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/(?:div|p|li)>/gi, "\n")
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&#(\d+);/g, function (all, code) {
      return String.fromCharCode(Number(code));
    })
    .replace(/[ \t]+\n/g, "\n")
    .trim();
}

function jsonResponse_(value) {
  return ContentService
    .createTextOutput(JSON.stringify(value))
    .setMimeType(ContentService.MimeType.JSON);
}
