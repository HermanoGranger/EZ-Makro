# EZ Makro — Domain Model (Phase 0.5)

## Klassendiagramm

```mermaid
classDiagram
    direction LR

    class FoodId {
        +String value
    }
    class RecipeId {
        +String value
    }
    class DiaryEntryId {
        +String value
    }
    class WeightLogId {
        +String value
    }

    class RawWeight {
        +Double grams
        +plus(RawWeight) RawWeight
    }
    class FinalWeight {
        +Double grams
    }
    class NutritionValues {
        +Double kcal
        +Double proteinG
        +Double fatG
        +Double carbsG
        +Double fiberG
        +times(Double) NutritionValues
        +plus(NutritionValues) NutritionValues
    }
    class ServingUnit {
        +String label
        +Double gramsEquivalent
        +toGrams(Double) Double
    }

    class CachedFoodItemStatus {
        <<sealed interface>>
    }
    class Original {
        <<object>>
    }
    class UserModified {
        <<object>>
    }
    class UpdateAvailable {
        <<object>>
    }
    CachedFoodItemStatus <|.. Original
    CachedFoodItemStatus <|.. UserModified
    CachedFoodItemStatus <|.. UpdateAvailable

    class FoodItem {
        +FoodId id
        +String name
        +String barcode
        +NutritionValues nutritionPer100g
        +ServingUnit servingUnit
        +Boolean isUserCreated
        +CachedFoodItemStatus cachedStatus
        +FoodItem originalSnapshot
    }

    class RecipeIngredient {
        +FoodItem food
        +RawWeight rawWeight
        +Double servingCount
        +effectiveRawWeight() RawWeight
    }
    class Recipe {
        +RecipeId id
        +String name
        +List~RecipeIngredient~ ingredients
        +FinalWeight finalWeight
        +Int portions
        +Double scale
    }

    class MealSlot {
        <<enum>>
        BREAKFAST
        LUNCH
        DINNER
        SNACKS
    }
    class DiaryEntry {
        +DiaryEntryId id
        +MealSlot slot
        +LocalDate date
        +FoodItem food
        +Recipe recipe
        +Double amountGrams
        +Double portionCount
    }
    class WeightLog {
        +WeightLogId id
        +LocalDate date
        +Double kilograms
    }
    class NutritionGoal {
        +Double kcal
        +Double proteinG
        +Double fatG
        +Double carbsG
        +Double fiberG
    }

    FoodItem --> FoodId
    FoodItem --> NutritionValues
    FoodItem --> ServingUnit
    FoodItem --> CachedFoodItemStatus
    FoodItem --> FoodItem : originalSnapshot

    RecipeIngredient --> FoodItem
    RecipeIngredient --> RawWeight
    RecipeIngredient --> ServingUnit
    Recipe --> RecipeId
    Recipe --> RecipeIngredient
    Recipe --> FinalWeight

    DiaryEntry --> DiaryEntryId
    DiaryEntry --> MealSlot
    DiaryEntry --> FoodItem
    DiaryEntry --> Recipe
    WeightLog --> WeightLogId
```

## Sequenzdiagramm: Rezept anlegen

```mermaid
sequenceDiagram
    actor User
    participant UI as Recipe Screen (MVI)
    participant VM as ViewModel
    participant UC as CreateRecipeUseCase
    participant Repo as RecipeRepository
    participant DB as Room

    User->>UI: Zutaten + Mengen eingeben
    UI->>VM: Action.CreateRecipe(ingredients, finalWeight?)
    VM->>UC: invoke(RecipeDraft)
    UC->>UC: validate (>=1 ingredient, Rohgewicht)
    UC->>Repo: save(Recipe)
    Repo->>DB: insert + ingredients
    DB-->>Repo: RecipeId
    Repo-->>UC: Result.Success(RecipeId)
    UC-->>VM: Result.Success
    VM-->>UI: State(Recipe saved)
    UI-->>User: Bestätigung
```

## Sequenzdiagramm: Barcode-Scan → Eigenes Lebensmittel

```mermaid
sequenceDiagram
    actor User
    participant Scan as BarcodeScan
    participant Repo as FoodRepository
    participant OFF as OpenFoodFactsSource
    participant UI as Food Screen

    User->>Scan: scan barcode
    Scan->>Repo: findByBarcode(code)
    Repo-->>Scan: Result.Error(NotFound)
    Scan->>UI: Event.NoBarcodeMatch(code)
    UI-->>User: "Eigenes Lebensmittel anlegen"
    Note over UI: Barcode vorausgefüllt
    User->>UI: Name + Nährwerte eingeben
    UI->>Repo: createUserFood(barcode=code)
    Repo-->>UI: Result.Success(FoodId)
```

## Sequenzdiagramm: Sync mit UserModified-Konflikt (ADR-004)

```mermaid
sequenceDiagram
    participant Sync as SyncJob
    participant Repo as FoodRepository
    participant OFF as OpenFoodFactsSource
    participant DB as Room

    Sync->>Repo: syncCachedEntries()
    Repo->>OFF: fetchUpdated(code)
    OFF-->>Repo: newer OFF payload
    Repo->>DB: read local entry status
    alt status == UserModified
        Note over Repo: NIEMALS automatisch überschreiben
        Repo->>DB: set status = UpdateAvailable (Original-Snapshot bleibt)
    else status == Original
        Repo->>DB: replace with newer payload, keep status Original
    end
    Repo-->>Sync: Result.Success
    Note over Sync: UI zeigt Badge "lokal angepasst" / "Update verfügbar"
```
