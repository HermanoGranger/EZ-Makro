# EZ Makro — Architecture

## Überblick

EZ Makro folgt **Clean Architecture** mit **MVI** im Presentation-Layer.
Alle dauerhaften Daten liegen lokal in **Room** (Single Source of Truth);
OpenFoodFacts ist eine ergänzende Remote-Quelle, deren Ergebnisse lokal
gecacht werden.

```
UI (Compose)            ── MVI: State / Action / Event
   │
Presentation (MVI)      ── ViewModel, UiModels, UiText
   │
Domain                  ── UseCases, Models, Value Objects (pure Kotlin)
   │
Data                    ── Repository ── Local (Room)  ┐
                          │              └─ Remote (Ktor/OFF) ┘
```

## Module (siehe auch android-module-structure Skill)

- `:app` — Compose-Navigation, Koin-Assemblierung
- `:core:domain` — Models, Value Objects, UseCases, Berechnungslogik
  (KEINE Android-Abhängigkeit)
- `:core-data` — Repository-Impl, RemoteDataSource (Ktor), LocalDataSource
  (Room), Mapper, DTOs
- `:core-database` — Room: Entities, DAOs, Migrations, TypeConverters, FTS
- `:core-presentation` — MVI-Basistypen, UiText, geteilte Compose-Helfer
- `:core-design-system` — Theme, Farben, Typografie, Basis-Composables
- Feature-Module (später, z. B. `:feature-food`, `:feature-recipes`) —
  hängen nur von `:core-*` ab, nicht untereinander

> **Hinweis:** App-Widgets (Glance) sind aus dem Scope gestrichen. Die
> ursprüngliche ADR-005 wurde daher verworfen.

Abhängigkeitsrichtung zeigt nach innen: `app → feature → core-* → domain`.
Feature-Module kennen einander nicht.

## Zentrale Domänenregeln (im Typsystem erzwungen)

1. **Rohgewicht vs. Endgewicht:** `RawWeight` und `FinalWeight` sind
   getrennte Value Objects. Gesamtnährwerte eines Rezepts = Summe der
   Rohzutaten. "pro 100 g" = Gesamtnährwerte ÷ `FinalWeight`. Eine Vermischung
   ist compile-time unmöglich.
2. **OFF-Eintragsstatus:** `CachedFoodItemStatus` als sealed/class-Enum
   (`Original`, `UserModified`, `UpdateAvailable`). Ein Sync überschreibt
   `UserModified` nie automatisch; Original-Snapshot wird separat gehalten.
3. **OpenFoodFacts-API:** Jeder Request trägt einen aussagekräftigen
   User-Agent-Header (App-Name, Version, Kontakt).

## Offline-First-Strategie

- Room ist die alleinige Wahrheitsquelle für die UI.
- Remote-Daten werden nur zum Befüllen/Aktualisieren des lokalen Caches
  genutzt (Cache-Then-Network wo sinnvoll).
- Die App ist ohne Netz voll funktionsfähig; fehlgeschlagene Remote-Calls
  führen zu `Result.Error`, niemals zum Absturz oder zur leeren UI.

## Dependency Injection

Koin. Pro Layer ein Koin-Modul; `:app` assembliert alle Module in
`startKoin`. ViewModels via `koinViewModel()` in Composables.

## Build & Qualität

- Gradle Version Catalog + `buildSrc` Convention Plugins
- Detekt + Ktlint als lokales Gate (`./gradlew check`) vor jedem Commit
- Kein CI (Solo-Projekt) — siehe ADR-006
- Keine App-Widgets (aus Scope gestrichen, ADR-005 verworfen)
