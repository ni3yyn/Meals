package dz.meals.app.data

fun buildShoppingList(recipes: List<MealRecipe>): List<ShoppingListItem> =
    recipes.flatMap { it.ingredients }
        .groupBy { it.id to it.unit }
        .map { (key, ingredients) ->
            ShoppingListItem(
                ingredientId = key.first,
                name = ingredients.first().name,
                quantity = ingredients.sumOf { it.quantity },
                unit = key.second
            )
        }
        .sortedBy { it.name }

fun formatQuantity(quantity: Double): String =
    if (quantity % 1.0 == 0.0) quantity.toLong().toString()
    else String.format(java.util.Locale.ROOT, "%.1f", quantity)
