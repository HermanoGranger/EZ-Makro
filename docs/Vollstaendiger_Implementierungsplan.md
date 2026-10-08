# Vollständiger Implementierungsplan

## Entwicklungsworkflow (für **jede** Phase)

1.  Planung
2.  Implementierung
3.  Unit Tests
4.  Integrationstests
5.  UI-Tests (falls relevant)
6.  Self Review
7.  Architektur-Erklärung
8.  Offene Fragen / Human in the Loop
9.  Refinement
10. Acceptance Check
11. Commit **nur nach Freigabe**

------------------------------------------------------------------------

## Statusübersicht

| Phase | Thema | Status |
|-------|-------|--------|
| 0 | Vision & Architektur | ✅ abgeschlossen |
| 0.5 | Domain Modeling | ✅ abgeschlossen |
| 1 | Projektsetup | ✅ abgeschlossen |
| 2 | Core | ✅ abgeschlossen |
| 3 | Datenbank | ✅ abgeschlossen |
| 4 | OpenFoodFacts | ✅ abgeschlossen |
| 5 | Food | ✅ abgeschlossen |
| 6 | Recipes | ✅ abgeschlossen (Scope angepasst, siehe unten) |
| 7 | Meal Log | ✅ abgeschlossen |
| 8 | Dashboard | ✅ in den Tagebuch-Screen integriert (kein eigener Tab) |
| 9 | Goals | ✅ abgeschlossen (liegt im Diary-Module) |
| B1 | Bugfix Crashes (P1) | ✅ abgeschlossen |
| B2 | Bugfix Datenintegrität (P2) | ✅ abgeschlossen |
| B3 | Bugfix Robustheit/UX (P3) | ✅ abgeschlossen |
| 10 | Weight Tracking | 🔜 offen |
| 11 | Statistics | 🔜 offen |
| 12 | Backup | ✅ abgeschlossen (Details: `docs/Plan_Backup_System.md`) |
| S | Seed-Daten (BLS, offline-first Erstausstattung) | ✅ abgeschlossen (ADR-008) |
| 13 | Polish | 🔜 offen |
| 14 | Release Candidate | 🔜 offen |

Aktuell offener Qualitäts-Backlog (Bugfix-Archiv inkl. abgeschlossener P1–P3):
`docs/Bugfix_Plan.md`.
Detaillierte Patterns pro Layer: `.opencode/skills/`.

------------------------------------------------------------------------

## Abgeschlossene Phasen (0–9) — Kurzfassung

> Frühere Detailplanungen (ToDos, Deliverables, Tests) zu den abgeschlossenen
> Phasen sind hier entfernt; sie sind umgesetzt und durch Code + Tests belegt.
> Künftige Phasen behalten ihre Detailplanung bei.

### Phase 0 — Vision & Architektur

Vision, Nicht-Ziele, Scope, Personas, User Flows, Modulstruktur, ADRs 001–004
und 006 (ADR-005 Widgets verworfen), Coding Style, DOD. Dokumente: `vision.md`,
`architecture.md`, `scope.md`, `roadmap.md`.

### Phase 0.5 — Domain Modeling

Domainmodell eingefroren, u. a. `RawWeight`/`FinalWeight`-Trennung im Typsystem
(ADR-003), `NutritionValues`, `ServingUnit`, `CachedFoodItemStatus`.

### Phase 1 — Projektsetup

Gradle Multi-Modul, Version Catalog, build-logic Conventions, Compose, Koin
(DI), Room, Ktor, KSP, Detekt, Ktlint. Glance entfernt (Scope). Clean Build.

### Phase 2 — Core

`core-domain`, `core-data`, `core-presentation`, `core-design-system`,
`core-database` mit Result/Errors, UiText, Theme, HttpClient, DataStore,
Logging.

### Phase 3 — Datenbank

Room-Entities, DAOs, Migrationen, Indizes, TypeConverter, FTS4/FTS5 für die
lokale Volltextsuche. Room produktionsreif.

### Phase 4 — OpenFoodFacts

Offline-First (ADR-002/ADR-004), API-Client mit verpflichtendem User-Agent,
Barcode-Lookup (CameraX + ML Kit), deferred OFF-Caching
(`cacheRemoteItem`/`rememberRemoteItem`), 503-Retry mit Backoff. Ein OFF-Sync
überschreibt `UserModified`-Einträge nie; Zustände Original/UserModified/
UpdateAvailable. **Hinweis:** Der neuere `UpdateAvailable`-Status ist aktuell
toter Code (kein Produktions-Aufrufer von `markUpdateAvailable`) — offene
Feature-Lücke, siehe Bugfix-Plan (Backlog).

### Phase 5 — Food

Suche (lokal FTS + OFF), Detail- und Edit-Seite (inkl. Bearbeitungsstatus),
Barcode, Favoriten, Recent, eigene Lebensmittel. Vollständig nutzbar.

### Phase 6 — Recipes

Rezepteditor, Zutaten (Food + Rezept-als-Zutat mit Snapshot-Unabhängigkeit),
Portionen, Endgewicht, 100 g, Kategorien, Duplizieren, Favoriten/Recent
(`is_favorite`/`last_used_at`).

**Scope-Anpassung (Refinement):** Meal Prep, Skalieren und Quick Meals / Kombos
wurden entfernt (Commit `7d2cb92`) — Rezeptberechnung ist vereinheitlicht
(nur Raw-/FinalWeight-Modell, ADR-003).

### Phase 7 — Meal Log

Tagebuch mit Mahlzeiten-Slots, Add Food/Recipe (FoodPicker + AddToDiary),
Quick Add (transient), Copy Yesterday, Verlauf, zentrale Datumsnavigation
(Ephemeride via `CurrentDateProvider`, nicht system-heute), Umlagerung der
Einträge auf das angezeigte Datum.

### Phase 8 — Dashboard

Kalorien, Makros, Ziele, Restkalorien, Charts umgesetzt, **danach** (Refinement)
als kollabierender `CollapsingSummaryHeader` in den Tagebuch-Screen integriert —
der Dashboard-Tab/Bottom-Nav-Eintrag und das Modul `:feature:dashboard` wurden
entfernt. (Ehemaliges `docs/REFINEMENT_PLAN.md` abgearbeitet und gelöscht.)

### Phase 9 — Goals

Kalorien, Protein, Fett, KH, Ballaststoffe; liegt im Diary-Module
(`feature:diary:presentation.goals`), erreichbar über das Ziele-Icon in der
Tagebuch-TopAppBar.

### Refinement-Phasen (Dashboard-Merge, Slot-Buttons, Picker-Konsolidierung)

Abgeschlossen und abgearbeitet:

1. Dashboard-Merge in den Diary-Screen (s. Phase 8)
2. Per-Slot-"+"-Buttons, fixer Slot im FoodPicker, QuickAdd-Navigation mit Slot
3. FoodPicker-Konsolidierung: kanonisches `FoodPickerContent` + zwei dünne
   Wrapper, `BarcodeScanScreen` entfernt
4. FoodPicker über alle Tabs vereinheitlicht: Tap → Detailseite, "±" 
   kontextabhängig (DIARY/BROWSE/RECIPE_INGREDIENT), Add-Button auf den
   Detailseiten, Online-Items als Session-Cache

------------------------------------------------------------------------

## Phase 10 -- Weight Tracking

Gewicht, Trend, Charts.

Genauer Umfang wird zu Beginn der Phase definiert (evtl. Integration in das
Tagebuch/Dashboard, eigene Kachel vs. Screens).

## Phase 11 -- Statistics

Wochen-, Monats-, Jahresstatistiken.

## Phase 12 -- Backup

JSON Export/Import (Datei `ezmakro-backup-<timestamp>.json`, `formatVersion`
aus `ADR-007`) inkl. Validierung in fünf Stufen mit Preview, zwei Import-Modi
(Ergänzen = Default, Ersetzen), Prüfsummen-**Warnung** statt Abbruch und
Wiederherstellung der Settings erst nach erfolgreichem Commit. **Keine
Verschlüsselung** — ausdrückliche Nutzerentscheidung (A3).

Das Format ist logisch und unabhängig vom Room-Schema: alter Import funktioniert
auch nach Schemaänderungen (Kernnachweis in `BackupRoundTripTest`).

Vollständiger Plan samt Annahmen (A1–A19), WPs und Abnahme:
`docs/Plan_Backup_System.md`. Format-ADR: `docs/adr/ADR-007-backup-format.md`.
UI: `:feature:settings:presentation` (`BackupScreen`), Code: `:core:data`
(`backup`-Package).

## Seed-Daten (BLS) — offline-first Erstausstattung

251 vegane Standardlebensmittel aus BLS 4.0 (CC BY 4.0) werden als
versioniertes JSON-Asset (`core/data/src/main/assets/seed/food_seed_v1.json`)
mitgeliefert und beim App-Start idempotent in Room eingesät
(`BlsFoodSeeder`, Marker `seed_food_version`). User-Edits, Favoriten und
eigene Lebensmittel werden durch einen SQL-Guard in
`FoodItemDao.upsertSeed` geschützt; nach REPLACE-Import wird der Marker
zurückgesetzt. Dev-Tooling: `tools/seed/` (Fetch, XLSX-Extraktion, Auswahl,
Asset-Builder; BLS-Rohdaten liegen nur im gitignored `cache/`). UI:
„Datenquellen"-Sektion in den Settings. Details: `docs/adr/ADR-008-bls-seed-daten.md`.

## Phase 13 -- Polish

Accessibility, Performance, Animationen, Tablets.

## Phase 14 -- Release Candidate

### Abschlusschecks

-   Unit Tests
-   Integrationstests
-   Compose Tests
-   E2E
-   Performance
-   Crashfrei
-   Release Build

------------------------------------------------------------------------

## Standard-Review nach jeder Phase

### Implementierungsübersicht

-   Neu
-   Geändert
-   Gelöscht

### Architekturentscheidungen

-   Warum?
-   Alternativen
-   Trade-offs

### Self Review

-   Was ist gut?
-   Was würde ich ändern?

### Human in the Loop

-   Offene Entscheidungen
-   Verbesserungsvorschläge

### Testreport

-   Unit
-   Integration
-   UI
-   Build
-   Lint

### Acceptance Checklist

-   Feature vollständig
-   Tests grün
-   Dokumentation aktuell
-   Architektur eingehalten
-   Keine TODOs

### Commit

Erst nach explizitem Befehl: `Commit Phase X`

------------------------------------------------------------------------

## Referenzen

-   Bugfix-Archiv + Qualitäts-Backlog: `docs/Bugfix_Plan.md`
-   Roadmap mit Phasen-Status: `docs/roadmap.md`
-   ADRs: `docs/adr/`