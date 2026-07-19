package com.example.ezmakro.core.domain.model

/**
 * Sensible out-of-the-box daily targets used until the user configures their
 * own goals (Phase 9). Anchored on a 2000 kcal reference diet with a balanced
 * macro split (protein 30% / fat 25% / carbs 45% of energy) plus a fiber
 * target per common dietary guidance.
 */
val DefaultNutritionGoal =
    NutritionGoal(
        kcal = 2000.0,
        proteinG = 150.0,
        fatG = 56.0,
        carbsG = 225.0,
        fiberG = 30.0,
    )
