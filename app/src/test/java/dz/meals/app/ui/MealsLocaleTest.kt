package dz.meals.app.ui

import androidx.compose.ui.unit.LayoutDirection
import dz.meals.app.ui.theme.DEFAULT_MEALS_LOCALE_TAG
import dz.meals.app.ui.theme.ENGLISH_MEALS_LOCALE_TAG
import dz.meals.app.ui.theme.bidiIsolate
import dz.meals.app.ui.theme.isMealsEnglish
import dz.meals.app.ui.theme.isLogicalForwardSwipe
import dz.meals.app.ui.theme.logicalAnimationDirectionSign
import dz.meals.app.ui.theme.logicalStartRevealSign
import dz.meals.app.ui.theme.mealsLayoutDirection
import dz.meals.app.ui.theme.mealsText
import dz.meals.app.ui.theme.mealsUnitLabel
import dz.meals.app.ui.theme.normalizeMealsLocaleTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealsLocaleTest {
    @Test
    fun supportedLanguageTagsNormalizeWithoutDependingOnDeviceLocale() {
        assertEquals(DEFAULT_MEALS_LOCALE_TAG, normalizeMealsLocaleTag("ar"))
        assertEquals(DEFAULT_MEALS_LOCALE_TAG, normalizeMealsLocaleTag("AR-dz"))
        assertEquals(ENGLISH_MEALS_LOCALE_TAG, normalizeMealsLocaleTag("en"))
        assertEquals(ENGLISH_MEALS_LOCALE_TAG, normalizeMealsLocaleTag("EN-us"))
        assertFalse(isMealsEnglish("ar-EG"))
        assertTrue(isMealsEnglish("en-GB"))
    }

    @Test
    fun layoutDirectionUsesExplicitScriptBeforeLanguageDefault() {
        assertEquals(LayoutDirection.Rtl, mealsLayoutDirection("ar-DZ"))
        assertEquals(LayoutDirection.Ltr, mealsLayoutDirection("en"))
        assertEquals(LayoutDirection.Ltr, mealsLayoutDirection("ku-Latn"))
        assertEquals(LayoutDirection.Rtl, mealsLayoutDirection("ku-Arab"))
        // A script subtag takes precedence even when it differs from the language default.
        assertEquals(LayoutDirection.Rtl, mealsLayoutDirection("en-Arab"))
    }

    @Test
    fun physicalGesturesMapToLogicalNextAndPreviousInBothDirections() {
        assertTrue(isLogicalForwardSwipe(-24f, LayoutDirection.Ltr))
        assertFalse(isLogicalForwardSwipe(24f, LayoutDirection.Ltr))
        assertTrue(isLogicalForwardSwipe(24f, LayoutDirection.Rtl))
        assertFalse(isLogicalForwardSwipe(-24f, LayoutDirection.Rtl))
        assertFalse(isLogicalForwardSwipe(0f, LayoutDirection.Rtl))
        assertEquals(1f, logicalStartRevealSign(LayoutDirection.Ltr), 0f)
        assertEquals(-1f, logicalStartRevealSign(LayoutDirection.Rtl), 0f)
        assertEquals(1, logicalAnimationDirectionSign(LayoutDirection.Ltr))
        assertEquals(-1, logicalAnimationDirectionSign(LayoutDirection.Rtl))
    }

    @Test
    fun englishCatalogueCoversMainNavigationAndSeededRecipeContent() {
        assertEquals("Plan", mealsText("الخطة", "en"))
        assertEquals("Recipes", mealsText("الوصفات", "en"))
        assertEquals("Vegetable couscous", mealsText("كسكس بالخضرة", "en"))
        assertEquals("Medium semolina", mealsText("سميد متوسط", "en"))
        assertEquals("Gluten", mealsText("الغلوتين", "en"))
        assertEquals("Eggs", mealsText("البيض", "en"))
        assertEquals("Milk", mealsText("الحليب", "en"))
        assertEquals("Breakfast", mealsText("الفطور", "en"))
        assertEquals("g", mealsUnitLabel("g", "en"))
        assertEquals("غ", mealsUnitLabel("g", "ar-DZ"))
        assertEquals("piece", mealsUnitLabel("piece", "en"))
        assertEquals(
            "Soak the chickpeas ahead of time and rinse them well.",
            mealsText("انقع الحمص مسبقاً واغسله جيداً.", "en")
        )
    }

    @Test
    fun dynamicLabelsTranslateTheirTemplateAndKeepInsertedValuesIsolated() {
        assertEquals(
            "Step ${bidiIsolate("2")} of 5",
            mealsText("الخطوة 2 من 5", "en")
        )
        assertEquals(
            "Add ${bidiIsolate("Medium semolina")}",
            mealsText("إضافة سميد متوسط", "en")
        )
        assertEquals(
            "Note: ${bidiIsolate("جزر")}",
            mealsText("ملاحظة: جزر", "en")
        )
        assertEquals(
            "Prices are estimates; ${bidiIsolate("2")} items have no price.",
            mealsText("الأسعار تقديرية، وهناك 2 عناصر دون سعر.", "en")
        )
        assertEquals(
            "Prices are estimates; ${bidiIsolate("1")} item has no price.",
            mealsText("الأسعار تقديرية، وهناك 1 عناصر دون سعر.", "en")
        )
        assertEquals(
            "${bidiIsolate("1")} item left",
            mealsText("1 عناصر متبقية", "en")
        )
        assertEquals(
            "Notice: ${bidiIsolate("2")} items have no confirmed price.",
            mealsText("تنبيه: يوجد 2 عنصر دون سعر مؤكد.", "en")
        )
        assertEquals(
            "Notice: ${bidiIsolate("1")} item has no confirmed price.",
            mealsText("تنبيه: يوجد 1 عنصر دون سعر مؤكد.", "en")
        )
        assertEquals(
            "${bidiIsolate("1")} serving",
            mealsText("1 حصص", "en")
        )
        assertEquals(
            "${bidiIsolate("1")} of ${bidiIsolate("10")} meals selected",
            mealsText("1 من 10 وجبات محددة", "en")
        )
        assertEquals(
            "${bidiIsolate("1")} completed · ${bidiIsolate("1")} of ${bidiIsolate("10")} meals",
            mealsText("1 مكتملة · 1 من 10 وجبات", "en")
        )
        assertEquals(
            "${bidiIsolate("1")} completed · ${bidiIsolate("1")} of ${bidiIsolate("1")} meal",
            mealsText("1 مكتملة · 1 من 1 وجبات", "en")
        )
        assertEquals(
            "Choose a recipe for ${bidiIsolate("Breakfast")}",
            mealsText("اختر وصفة لـ الفطور", "en")
        )
        assertEquals(
            "${bidiIsolate("25")} min · ${bidiIsolate("2")} servings",
            mealsText("25 دقيقة · 2 حصص", "en")
        )
    }

    @Test
    fun durationAndCurrencyUseDifferentUnitsAndStableNumberFormatting() {
        assertEquals("${bidiIsolate("15")} min", mealsText("15 د", "en"))
        assertEquals("15,000 DZD", mealsText("15\u202F000 دج", "en"))
        assertEquals("23 November - 29 November 2026", mealsText("23 نوفمبر - 29 نوفمبر 2026", "en"))
        assertEquals("Monday 23 November", mealsText("الإثنين 23 نوفمبر", "en"))
        assertEquals("Quantity (${bidiIsolate("g")})", mealsText("الكمية (غ)", "en"))
        assertEquals("Base unit: ${bidiIsolate("g")}", mealsText("الوحدة الأساسية: غ", "en"))
        assertEquals("Pack size or quantity sold (${bidiIsolate("kg")})", mealsText("حجم العبوة أو الكمية المباعة (كغ)", "en"))
        assertEquals(
            "Needed ${bidiIsolate("500 g")} · in pantry ${bidiIsolate("100 g")}",
            mealsText("المطلوب 500 غ · في المخزون 100 غ", "en")
        )
        assertEquals("2 pieces", mealsText("2 حبتان", "en"))
    }

    @Test
    fun unknownAndUserAuthoredTextIsNeverGuessedOrOverwritten() {
        val userText = "دجاج خاص 2"
        assertEquals(userText, mealsText(userText, "en"))
        val customArabicText = "مكوّن خاص كتبته بنفسي 42"
        assertEquals(customArabicText, mealsText(customArabicText, "en"))
        assertEquals(customArabicText, mealsText(customArabicText, "ar-DZ"))
    }

    @Test
    fun arabicRemainsTheDefaultAndIsNeverTransformed() {
        val source = "خطة الأسبوع · 15 دج"
        assertEquals(source, mealsText(source, DEFAULT_MEALS_LOCALE_TAG))
        assertEquals("⁨15⁩", bidiIsolate("15"))
    }
}
