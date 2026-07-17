# AGENTS.md — EZ Makro (Android/Kotlin)

## Projekt

Offline-first Android-App zum Tracken von Kalorien, Makros und Rezepten.
Kein Backend, keine Accounts, keine Werbung. Solo-Entwicklerprojekt.

## Tech-Stack

Kotlin, Jetpack Compose, MVI, Koin (DI), Room (lokale DB, Single Source of
Truth), Ktor (HTTP-Client), KSP, OpenFoodFacts als externe Datenquelle,
CameraX + ML Kit für Barcode-Scan.
Detaillierte Patterns pro Layer stehen in den Skills unter `.opencode/skills/`
(z. B. `android-data-layer`, `android-presentation-mvi`, `android-testing`) —
diese werden automatisch geladen, wenn relevant.

## Referenzdokumente

- Vollständiger Phasenplan: `docs/Vollstaendiger_Implementierungsplan.md`
- Scope & Features: `docs/Scope_und_Features.md`

### Nach Phase 0:
- Vision: `docs/vision.md`
- Architektur: `docs/architecture.md`
- Scope: `docs/scope.md`
- Phasenplan: `docs/Vollstaendiger_Implementierungsplan.md`
- ADRs: `docs/adr/ADR-00X-*.md`
- Coding Style: `docs/coding-style.md`
- DOD: `docs/definition-of-done.md`
- Roadmap: `docs/roadmap.md`

### Nach Phase 0.5 zusätzlich
- Domain Model: `docs/domain-model.md`

Vor Beginn einer neuen Phase: das entsprechende Kapitel im Phasenplan lesen.
Diese Dokumente NICHT komplett in den Kontext kopieren — gezielt das jeweils
relevante Kapitel nachschlagen.

## Zentrale Domain-Regeln (gelten in JEDER Phase)

- **Rohgewicht vs. Endgewicht bei Rezepten:** Gesamtnährwerte eines Rezepts
  werden IMMER aus der Summe der Rohzutaten berechnet. "Pro 100 g" wird
  ausschließlich aus Gesamtnährwerte ÷ optionalem, vom Nutzer gemessenem
  Endgewicht abgeleitet — niemals aus der Summe der Rohgewichte. Diese
  Unterscheidung muss im Typsystem erzwungen werden (`RawWeight` /
  `FinalWeight`), nicht nur per Konvention.
- **OpenFoodFacts-Einträge und Nutzeränderungen:** Jeder lokal gecachte
  OFF-Eintrag hat einen Status (`Original` / `UserModified` /
  `UpdateAvailable`). Ein Sync überschreibt einen `UserModified`-Eintrag NIE
  automatisch. Original-Snapshot bleibt zusätzlich zur bearbeiteten Version
  gespeichert, damit "Zurücksetzen auf Original" jederzeit möglich ist. Der
  Bearbeitungsstatus wird in der UI sichtbar kommuniziert (z. B. Badge).
- **OpenFoodFacts-API:** Jeder Request braucht einen aussagekräftigen
  User-Agent-Header (App-Name, Version, Kontakt).

## Build & Test

- `./gradlew build`
- `./gradlew detekt`
- `./gradlew ktlintCheck`
- Kein CI-Tooling (Solo-Projekt) — diese Befehle gelten als lokales Gate vor
  jedem Commit.

## Entwicklungsworkflow (für jede Phase, siehe Implementierungsplan)

1. Planung
2. Implementierung
3. Unit Tests
4. Integrationstests
5. UI-Tests (falls relevant)
6. Self Review
7. Architektur-Erklärung
8. Offene Fragen / Human in the Loop
9. Refinement
10. Acceptance Check
11. Commit — **nur nach expliziter Freigabe**

## Commit-Regel

Niemals von dir aus committen. Erst nach explizitem Befehl `Commit Phase X`.

## Namenskonventionen

Siehe Skills — insbesondere `android-module-structure` (Modul-Layout),
`android-presentation-mvi` (State/Action/Event/ViewModel-Namen),
`android-data-layer` (DataSource/Repository/DTO/Entity-Namen).
