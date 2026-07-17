# EZ Makro — Coding Style

Dieses Dokument ergänzt die automatisierten Gates (Detekt, Ktlint) um
projektspezifische Konventionen.

## Sprache & Format

- **Kotlin** ausschließlich. `ktlint` (`ktlint_official` Preset) erzwingt
  Formatierung; `detekt` prüft Code-Smells.
- Maximale Zeilenlänge: 120 Zeichen.
- Dateien enden mit Zeilenumbruch; kein trailing whitespace (ktlint).

## Namenskonventionen (siehe Skills)

- **Module:** `:core-<name>`, `:feature-<name>`, `:app`.
- **Data Layer:**
  - Entities: `<Name>Entity`, DAOs: `<Name>Dao`,
  - DTOs: `<Name>Dto`, Mapper: `mapToDomain()` / `mapToEntity()`,
  - DataSources: `Local<Name>DataSource` / `Remote<Name>DataSource`,
  - Repository-Interfaces in `:core-domain`, Impl in `:core-data`.
- **Presentation (MVI):**
  - State: `<Screen>State`, Action: `<Screen>Action`, Event: `<Screen>Event`,
  - ViewModel: `<Screen>ViewModel`, UiModel: `<Name>UiModel`.
  - `UiText` für alle nutzersichtbaren Strings (kein hartkodierter Text).
- **Domain:** UseCases enden auf `UseCase`; reine Kotlin, keine Android-Imports.

## Architektur-Regeln

- Domain-Schicht enthält keine Android-`import`s (Compile-Time erzwungen
  durch Modulgrenze).
- Dependency-Richtung zeigt nach innen (`app → feature → core-* → domain`).
- Singletons/DI ausschließlich über Koin; keine manuellen `object`-Service
  mit globalem Zustand außerhalb von Koin.

## Fehlerbehandlung

- Domänen- und Daten-Layer nutzen den `Result<T, E>`-Wrapper mit `DataError`
  (siehe `android-error-handling` Skill). Kein gefangenes `Exception` ohne
  Mapping an die UI.

## Kommentare

- **Keine erklärenden Kommentare** für offensichtlichen Code. Kommentare nur
  für nicht-triviale Domänenregeln (z. B. Roh-/Endgewicht, OFF-Status).
- `TODO`-Kommentare sind in fertigen Phasen nicht erlaubt (Acceptance-Check).

## Tests

- JUnit5 + Turbine + AssertK + `UnconfinedTestDispatcher` (siehe
  `android-testing` Skill).
- Testnamen beschreiben Szenario: `should_ReturnError_When_NetworkFails`.
