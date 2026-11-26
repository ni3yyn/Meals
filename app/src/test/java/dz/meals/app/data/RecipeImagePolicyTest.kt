package dz.meals.app.data

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
    fun rejectsUnknownLicenceEvenWhenSourceMetadataExists() {
        assertFalse(
            recipe(
                license = "All rights reserved",
                licenseUrl = "https://example.com/licence"
            ).hasVerifiedReusableImage()
        )
    }
}
