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

## Phase 0 -- Vision & Architektur

### Ziel

Produktvision, Architektur und Standards vollständig festlegen.

#### ToDos

-   Git Init (Repo, .gitignore, initiale Struktur -- **ganz am Anfang**, damit auch Phase 0 selbst versioniert ist)
-   Vision
-   Nicht-Ziele
-   Scope
-   Personas
-   User Flows
-   Modulstruktur
-   ADRs
-   Branching
-   Coding Style
-   Definition of Done

#### ADRs (mindestens)

-   ADR-001: Clean Architecture + MVI
-   ADR-002: Offline-First-Strategie (Room als Single Source of Truth)
-   ADR-003: Rohgewicht vs. Endgewicht bei Rezeptberechnung (siehe Phase 0.5)
-   ADR-004: Umgang mit nutzerbearbeiteten OpenFoodFacts-Einträgen (Konfliktstrategie, siehe Phase 4)
-   ADR-005: Widget-Technologie -- Glance (Jetpack Compose für App Widgets) --
    **VERWORFEN**: Widgets aus dem Scope gestrichen, siehe `docs/scope.md`
    ("Aus dem Scope gestrichen")
-   ADR-006 (optional): Kein CI-Tooling, da privates Einzelentwickler-Projekt -- stattdessen lokale Gradle-Tasks (`./gradlew check`) als manuelles Gate vor jedem Commit

#### Deliverables

-   vision.md
-   architecture.md
-   scope.md
-   roadmap.md
-   ADR-001..004 und ADR-006 (ADR-005 verworfen)

#### Checks

-   Architektur konsistent
-   Offline-Strategie dokumentiert
-   Keine offenen Kernentscheidungen

#### Tests

-   Dokumentenreview

#### Acceptance

-   Architektur freigegeben

------------------------------------------------------------------------

## Phase 0.5 -- Domain Modeling

### ToDos

-   Domain Models
-   Aggregate Roots
-   Value Objects (Nutrition etc.)
-   IDs
-   Berechnungslogik
-   UML
-   Sequenzdiagramme für Kernflows

#### Wichtige Value Objects / Konzepte

-   **NutritionValues** (kcal, Protein, Fett, KH, Ballaststoffe -- immer bezogen auf eine definierte Basis, z. B. 100 g oder 1 Portion)
-   **ServingUnit**: Abbildung von Mengenangaben wie "1 Ei", "1 Scheibe", "1 EL" auf ein Gramm-Äquivalent, damit Zutaten nicht ausschließlich über Gramm eingegeben werden müssen
-   **Rezept-Gewichtslogik (klar getrennt!):**
    -   Summe der Nährwerte aller Zutaten im **Rohzustand** ergibt die **Gesamtnährwerte des Gerichts** (ändert sich nicht durch Kochen)
    -   Nutzer kann optional das **gemessene Endgewicht** (z. B. nach dem Kochen) eintragen
    -   "Nährwerte pro 100 g" werden **ausschließlich** aus Gesamtnährwerte ÷ Endgewicht berechnet, niemals aus der Summe der Rohgewichte -- diese Unterscheidung muss im Domain Model erzwungen werden (z. B. durch getrennte Typen `RawWeight` und `FinalWeight`), damit sie später nicht versehentlich vermischt wird
-   **CachedFoodItem-Status**: Konzept für "unverändert / lokal bearbeitet / veraltet gegenüber Original" bereits hier als Enum/Sealed-Class modellieren (Details zur Umsetzung in Phase 4)

#### Checks

-   Keine Android-Abhängigkeiten
-   Domäne vollständig
-   Roh-/Endgewicht-Trennung ist im Typsystem abgebildet, nicht nur per Konvention

#### Tests

-   Berechnungstests
-   Edge Cases (z. B. Rezept ohne Endgewicht, Zutat ohne Portionsgröße)

#### Acceptance

-   Domainmodell eingefroren

------------------------------------------------------------------------

## Phase 1 -- Projektsetup

### ToDos

-   Gradle
-   Version Catalog
-   build-logic
-   Convention Plugins
-   Compose
-   Koin
-   Room
-   Ktor
-   KSP
-   Detekt
-   Ktlint
-   CameraX + ML Kit Barcode Scanning (Dependency-Setup, wird in Phase 4/5 verwendet)
-   Glance (Dependency-Setup) -- **ENTFERNT**: Widgets aus Scope gestrichen
    (siehe `docs/scope.md`)

#### Checks

-   Build
-   Lint
-   Formatting

#### Tests

-   ./gradlew build
-   detekt
-   ktlint

#### Acceptance

-   Clean Build

------------------------------------------------------------------------

## Phase 2 -- Core

### Module

-   core-domain
-   core-data
-   core-presentation
-   core-design-system
-   core-database

#### ToDos

-   Result
-   Errors
-   UiText
-   Theme
-   HttpClient
-   DataStore
-   Logging

#### Tests

-   Unit Tests
-   Serialization
-   Error Mapping

#### Acceptance

-   Core stabil

------------------------------------------------------------------------

## Phase 3 -- Datenbank

#### ToDos

-   Entities
-   DAO
-   Migrationen
-   Indizes
-   TypeConverter
-   Room FTS4/FTS5 für lokale Volltextsuche über Lebensmittelnamen (statt LIKE-Queries) -- Entscheidung hier treffen, da nachträgliches Umbauen aufwendig ist

#### Tests

-   CRUD
-   Migration
-   Performance
-   Offline
-   FTS-Suchqualität (Teiltreffer, Umlaute, Groß-/Kleinschreibung)

#### Acceptance

-   Room produktionsreif

------------------------------------------------------------------------

## Phase 4 -- OpenFoodFacts

#### ToDos

-   API-Client mit **verpflichtendem, aussagekräftigem User-Agent-Header** (App-Name, Version, Kontakt) -- OFF ratelimitet/blockiert sonst
-   DTO
-   Mapper
-   RemoteDataSource
-   Offline Repository
-   Caching
-   Barcode Lookup (CameraX + ML Kit)
-   **Fallback bei Barcode ohne Treffer:** direkte Überleitung in den "Eigenes Lebensmittel anlegen"-Flow, Barcode wird dabei vorausgefüllt

#### Umgang mit nutzerbearbeiteten Einträgen (wichtig)

-   Jeder lokal gecachte OFF-Eintrag erhält ein Flag/Status, z. B. `Original`, `UserModified`, `UpdateAvailable`
-   Sobald der Nutzer einen gecachten Eintrag bearbeitet, wird er als `UserModified` markiert -- ein erneuter Sync aus der OFF-API **überschreibt diesen Eintrag nicht automatisch**
-   Die App **kommuniziert das aktiv in der UI** (z. B. Badge/Hinweis "Lokal angepasst") am Lebensmittel-Detail
-   Der Nutzer bekommt zwei explizite Optionen:
    -   **Zurücksetzen auf Original**: das ursprüngliche, zuerst gecachte OFF-Ergebnis wird wiederhergestellt (dafür muss das Original zusätzlich zur bearbeiteten Version aufbewahrt werden, z. B. in einer separaten Spalte/Snapshot statt Überschreiben)
    -   **Neu von OFF beziehen**: aktueller API-Stand wird erneut geladen und ersetzt die lokale Version (mit Bestätigungsdialog, da das eigene Anpassungen verwirft)

#### Tests

-   Netzwerk
-   Offline
-   Fehlerfälle
-   Retry
-   User-Agent wird korrekt gesetzt
-   Modifikations-Flag-Übergänge (Original → UserModified → Reset/Refetch)

#### Acceptance

-   Offline First erfüllt
-   Nutzeränderungen sind nachvollziehbar, kommunizierbar und reversibel

------------------------------------------------------------------------

## Phase 5 -- Food

#### Features

-   Suche (lokal via FTS + OpenFoodFacts)
-   Detail (inkl. Anzeige des Bearbeitungsstatus, siehe Phase 4)
-   Barcode
-   Favoriten
-   Recent
-   Eigene Lebensmittel

#### Tests

-   VM
-   Repository
-   UI
-   Navigation

#### Acceptance

-   Lebensmittel vollständig nutzbar

------------------------------------------------------------------------

## Phase 6 -- Recipes

#### Features

-   Rezepteditor
-   Zutaten
-   Portionen
-   Endgewicht (siehe Roh-/Endgewicht-Trennung aus Phase 0.5)
-   100 g
-   Kategorien
-   Meal Prep
-   Skalieren
-   Duplizieren
-   **Quick Meals / Kombos**: einfache Gruppen von Lebensmitteln als Vorlage speichern (z. B. "mein Frühstück"), ohne volle Rezept-Skalierungslogik -- schneller anzulegen als ein echtes Rezept

#### Tests

-   Mathematische Genauigkeit
-   Rundung
-   Roh- vs. Endgewicht-Berechnung explizit getestet
-   UI

#### Acceptance

-   Berechnungen korrekt

------------------------------------------------------------------------

## Phase 7 -- Meal Log

Features: - Tageslog - Mahlzeiten - Copy Yesterday - Quick Add - Verlauf

Tests: - Tagesberechnung - Historie - UI

Acceptance: - Tagestracking vollständig

------------------------------------------------------------------------

## Phase 8 -- Dashboard

Features: - Kalorien - Makros - Ziele - Charts - Restkalorien

Tests: - Diagramme - Berechnung

Acceptance: - Dashboard vollständig

------------------------------------------------------------------------

## Phase 9 -- Goals

Kalorien, Protein, Fett, KH, Ballaststoffe.

## Phase 10 -- Weight Tracking

Gewicht, Trend, Charts.

## Phase 11 -- Statistics

Wochen-, Monats-, Jahresstatistiken.

## Phase 12 -- Backup

-   JSON Export/Import
-   Validierung
-   **Optionale Verschlüsselung** des Exports mit einem Passwort (Ernährungsdaten sind potenziell sensibel), z. B. via AES mit vom Nutzer gewähltem Passwort
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
