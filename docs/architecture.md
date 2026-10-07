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
- `:feature:settings:presentation` — Sammel-Seite „Settings" (Tagesziele,
  Backup); Cross-Feature-Routen laufen wie alle anderen über `:app`

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

## Backup

- **Logischer Export statt DB-Kopie (A1):** eine JSON-Datei mit
  `formatVersion`, den fünf Tabellen (`food_items`, `recipes`,
  `recipe_ingredients`, `diary_entries`, `weight_logs`) und allen Settings.
  Das Format ist ausdrücklich **nicht** an Spalten oder die Room-Schema-
  Version gebunden — alter Import funktioniert auch nach Schemaänderungen
  (Kernnachweis in `BackupRoundTripTest`), Formatregeln: `ADR-007`.
- Zwei Import-Modi (Ergänzen = Default, Ersetzen), Validierung in fünf
  Stufen mit Preview; der Datenbankimport läuft in **einer**
  Room-Transaktion, die Settings werden erst nach erfolgreichem Commit
  wiederhergestellt.
- **Keine Verschlüsselung, keine Passwort-Sperre** — ausdrückliche
  Nutzerentscheidung (A3).
- Code: `:core:data` (`backup`-Package: Codec, Mapper, Validator, Exporter,
  Importer, Repository); UI in `:feature:settings:presentation`
  (`BackupScreen`). `:app` übernimmt die Koin-Assemblierung des
  Settings-/Backup-Bereichs.
- Android Auto Backup (`allowBackup`, `backup_rules.xml`) bleibt
  unangetastet (A17); nach dem Import ist **kein** App-Neustart nötig (A16).

## Dependency Injection

Koin. Pro Layer ein Koin-Modul; `:app` assembliert alle Module in
`startKoin`. ViewModels via `koinViewModel()` in Composables.

## Build & Qualität

- Gradle Version Catalog + `buildSrc` Convention Plugins
- Detekt + Ktlint als lokales Gate (`./gradlew check`) vor jedem Commit
- Kein CI (Solo-Projekt) — siehe ADR-006
- Keine App-Widgets (aus Scope gestrichen, ADR-005 verworfen)
