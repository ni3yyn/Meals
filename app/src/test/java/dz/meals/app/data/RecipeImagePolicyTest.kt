package dz.meals.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeImagePolicyTest {

    private fun recipe(
        license: String = "CC BY-SA 4.0",
        licenseUrl: String = "https://creativecommons.org/licenses/by-sa/4.0/",
        imageUrl: String = "https://upload.wikimedia.org/wikipedia/commons/thumb/example.jpg",
        sourcePageUrl: String = "https://commons.wikimedia.org/wiki/File:Example.jpg",
        author: String = "Photographer",
        credit: String = "Photographer / Wikimedia Commons"
    ) = MealRecipe(
        id = "test-recipe",
        nameAr = "وصفة اختبار",
        nameFr = "Test recipe",
        descriptionAr = "",
        descriptionFr = "",
        category = "تقليدي",
        region = "",
        durationMinutes = 30,
        baseServings = 2,
        difficulty = "سهل",
        allergens = emptySet(),
        ingredients = emptyList(),
        stepsAr = emptyList(),
        stepsFr = emptyList(),
        imageUrl = imageUrl,
        imageAuthor = author,
        imageLicense = license,
        imageSourcePageUrl = sourcePageUrl,
        imageLicenseUrl = licenseUrl,
        imageCredit = credit
    )

    @Test
    fun acceptsVerifiedReusableCommonsImage() {
        assertTrue(recipe().hasVerifiedReusableImage())
    }

    @Test
    fun rejectsNonCommercialAndNoDerivativesLicences() {
        assertFalse(
            recipe(
                license = "CC BY-NC 4.0",
                licenseUrl = "https://creativecommons.org/licenses/by-nc/4.0/"
            ).hasVerifiedReusableImage()
        )
        assertFalse(
            recipe(
                license = "CC BY-ND 4.0",
                licenseUrl = "https://creativecommons.org/licenses/by-nd/4.0/"
            ).hasVerifiedReusableImage()
        )
    }

    @Test
    fun rejectsLegacyCatalogueImageWithoutVerifiedSourceAndLicence() {
        assertFalse(
            recipe(
                license = "",
                licenseUrl = "",
                sourcePageUrl = ""
            ).hasVerifiedReusableImage()
        )
    }

    @Test
    fun rejectsImagesOutsideCommonsUploadAndDescriptionHosts() {
        assertFalse(
            recipe(imageUrl = "https://example.com/food.jpg").hasVerifiedReusableImage()
        )
        assertFalse(
            recipe(sourcePageUrl = "https://example.com/photo").hasVerifiedReusableImage()
        )
    }

    @Test
    fun requiresAuthorAndCredit() {
        assertFalse(recipe(author = "").hasVerifiedReusableImage())
        assertFalse(recipe(credit = "").hasVerifiedReusableImage())
    }

    @Test
    fun acceptsPublicDomainMarkAndCcZeroWithOfficialUrls() {
        assertTrue(
            recipe(
                license = "Public domain",
                licenseUrl = "https://creativecommons.org/publicdomain/mark/1.0/"
            ).hasVerifiedReusableImage()
        )
        assertTrue(
            recipe(
                license = "CC0 1.0",
                licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/"
            ).hasVerifiedReusableImage()
        )
    }

    @Test
    fun rejectsLicenceNameAndUrlMismatches() {
        assertFalse(
            recipe(
                license = "CC BY 4.0",
                licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/"
            ).hasVerifiedReusableImage()
        )
        assertFalse(
            recipe(
                license = "CC BY-SA 4.0",
                licenseUrl = "https://creativecommons.org/licenses/by/4.0/"
            ).hasVerifiedReusableImage()
        )
        assertFalse(
            recipe(
                license = "Public domain",
                licenseUrl = "https://example.com/publicdomain/mark/1.0/"
            ).hasVerifiedReusableImage()
        )
    }

    @Test
    fun rejectsLookalikeCreativeCommonsHostsAndMalformedLicencePaths() {
        assertFalse(
            recipe(
                license = "CC BY 4.0",
                licenseUrl = "https://creativecommons.org.attacker.example/licenses/by/4.0/"
            ).hasVerifiedReusableImage()
        )
        assertFalse(
            recipe(
                license = "CC BY 4.0",
                licenseUrl = "https://creativecommons.org/licenses/by/latest/"
            ).hasVerifiedReusableImage()
        )
    }

    private fun focusCatalogueRecipe(
        imageUrl: String = "https://ylushaayhmufwdmuqafv.supabase.co/storage/v1/object/public/focus-recipes/r/test.jpg",
        sourceUrl: String = "https://focusathletics.app/recetas/test"
    ) = recipe(
        license = "",
        licenseUrl = "",
        imageUrl = imageUrl,
        sourcePageUrl = "",
        author = "",
        credit = ""
    ).copy(
        id = "focus:test",
        attribution = RecipeAttribution(
            sourceTitle = "Focus Recipes v1.0: Test",
            sourceUrl = sourceUrl,
            revisionUrl = "",
            historyUrl = "",
            credit = "Focus Recipes v1.0 (2026). Focus.",
            sourceLicense = "CC BY 4.0",
            license = "CC BY 4.0",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
            changes = ""
        )
    )

    private fun unitoolsCatalogueRecipe(
        imageLicense: String = "CC BY 2.0",
        imageAuthor: String = "Photo author",
        imageUrl: String = "https://theunitools.com/recipes/test.jpg"
    ) = recipe(
        license = imageLicense,
        licenseUrl = "",
        imageUrl = imageUrl,
        sourcePageUrl = "",
        author = imageAuthor,
        credit = imageAuthor
    ).copy(
        id = "unitools:test",
        attribution = RecipeAttribution(
            sourceTitle = "UniTools World Recipes: Test",
            sourceUrl = "https://theunitools.com/en/data",
            revisionUrl = "",
            historyUrl = "",
            credit = "UniTools — theunitools.com",
            sourceLicense = "CC BY-SA 4.0",
            license = "CC BY-SA 4.0",
            licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
            changes = ""
        )
    )

    @Test
    fun displaysFocusCataloguePhotosUsingTheirExplicitDatasetLicence() {
        assertTrue(focusCatalogueRecipe().hasVerifiedReusableImage())
    }

    @Test
    fun displaysUniToolsPhotosUsingTheirIndividualPhotoLicence() {
        assertTrue(unitoolsCatalogueRecipe(imageLicense = "CC BY-SA 3.0").hasVerifiedReusableImage())
        assertTrue(unitoolsCatalogueRecipe(imageLicense = "CC BY 2.0").hasVerifiedReusableImage())
        assertTrue(
            unitoolsCatalogueRecipe(imageLicense = "CC0", imageAuthor = "")
                .hasVerifiedReusableImage()
        )
    }

    @Test
    fun rejectsUniToolsPhotosWithoutRequiredAuthorOrWithUnsupportedLicence() {
        assertFalse(
            unitoolsCatalogueRecipe(imageLicense = "CC BY-SA 3.0", imageAuthor = "")
                .hasVerifiedReusableImage()
        )
        assertFalse(
            unitoolsCatalogueRecipe(imageLicense = "GFDL 1.2")
                .hasVerifiedReusableImage()
        )
        assertFalse(
            unitoolsCatalogueRecipe(imageLicense = "CC BY-SA 2.5 br")
                .hasVerifiedReusableImage()
        )
    }

    @Test
    fun rejectsPhotosFromUnverifiedDzirielleSource() {
        val unverified = recipe(
            license = "not_verified",
            licenseUrl = "",
            imageUrl = "https://www.dzirielle.com/saveurs/test.webp",
            sourcePageUrl = "",
            author = "",
            credit = ""
        ).copy(
            attribution = RecipeAttribution(
                sourceTitle = "Dzirielle",
                sourceUrl = "https://www.dzirielle.com/recette/test",
                revisionUrl = "",
                historyUrl = "",
                credit = "Dzirielle",
                sourceLicense = "not_verified",
                license = "not_verified",
                licenseUrl = "",
                changes = ""
            )
        )
        assertFalse(unverified.hasVerifiedReusableImage())
    }

    @Test
    fun rejectsAValidLicenceWhenTheImageHostOrBucketDoesNotMatchItsSource() {
        assertFalse(
            focusCatalogueRecipe(
                imageUrl = "https://example.com/storage/v1/object/public/focus-recipes/test.jpg"
            ).hasVerifiedReusableImage()
        )
        assertFalse(
            focusCatalogueRecipe(
                sourceUrl = "https://focusathletics.app.attacker.example/recetas/test"
            ).hasVerifiedReusableImage()
        )
        assertFalse(
            unitoolsCatalogueRecipe(
                imageUrl = "https://theunitools.com.attacker.example/recipes/test.jpg"
            ).hasVerifiedReusableImage()
        )
    }

    @Test
    fun returnsTheCorrectPhotoCreditAndLicenceForEachCatalogue() {
        assertTrue(focusCatalogueRecipe().recipeImageCredit().startsWith("Focus Recipes"))
        assertEquals("CC BY 4.0", focusCatalogueRecipe().recipeImageLicense())
        assertEquals(
            "https://creativecommons.org/licenses/by/4.0/",
            focusCatalogueRecipe().recipeImageLicenseUrl()
        )
        assertEquals(
            "https://focusathletics.app/recetas/test",
            focusCatalogueRecipe().recipeImageSourceUrl()
        )

        assertEquals("Photo author", unitoolsCatalogueRecipe().recipeImageCredit())
        assertEquals("CC BY 2.0", unitoolsCatalogueRecipe().recipeImageLicense())
        assertEquals(
            "https://creativecommons.org/licenses/by/2.0/",
            unitoolsCatalogueRecipe().recipeImageLicenseUrl()
        )
    }

    @Test
    fun rejectsUnknownLicenceEvenWhenSourceMetadataExists() {
        assertFalse(
            recipe(
                license = "All rights reserved",
                licenseUrl = "https://example.com/licence"
            ).hasVerifiedReusableImage()
        )
    }
}
