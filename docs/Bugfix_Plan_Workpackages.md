# EZ-Makro Bugfix-Plan (Workpackages für Coding-Agents)

## Grundprinzipien
- **Nur lesen vor Schreiben.** Nutze `Glob`, `Grep`, `Read` um exakte Stellen zu finden, erst dann editieren.
- **Minimaler Diff.** Nur genau den Bug fixen. Keine kosmetischen Änderungen.
- **ADR-konform.** UserModified-Schutz, RawWeight/FinalWeight, User-Agent, OFF-Sync
- **MVI einhalten.** State immutable, Actions deterministisch, Events One-Shot
- **Nach jedem WP:** Build + detekt + ktlintCheck + relevante Tests. **Commit erst nach expliziter Freigabe durch User.**
- **Option A (WP2):** nie User-Content überschreiben; `refetchFromOff` ist expliziter Nutzer-Override.

## WP 1: HTTP-Timeouts + SafeCall Timeout-Mapping (P0)

### Ziele
- Ktor-HttpClient um `HttpTimeout` erweitern
- SafeCall mappt Timeout-Exceptions zu timeout-spezifischem Error
- Retry nur für idempotente GETs mit exponentiellem Backoff

### Betroffene Dateien
- Ktor-Client Setup (`core/network` oder `core/data/network/HttpClientFactory*.kt`)
- `SafeCall.kt` (Timeout-Mapping)
- OFF-API/Client wo User-Agent gesetzt wird

### Schritte (Agent)
1. `Glob/Grep` nach HttpClient, SafeCall, installPlugins
2. `HttpTimeout` (connect/request/read/write) Defaults: connect 10s, request 15s, socket/read konsistent
3. `HttpRequestRetry` nur GET, maxRetries 2-3, Backoff 100/200/400ms, retryOn Timeout/Connect/IOException
4. SafeCall mappt `HttpRequestTimeoutException`, `SocketTimeoutException`, `ConnectTimeoutException` → Timeout-Error
5. User-Agent unverändert lassen

### Tests
- SafeCall-Timeout-Tests, keine bestehenden Netzwerk-Tests brechen

### Build/QA
```bash
./gradlew :core:network:build :core:data:build detekt ktlintCheck 2>&1 | tail -30
./gradlew test --tests "*SafeCall*" 2>&1 | tail -40
```

### DoD
- [ ] Timeouts + Retry (idempotent-only) konfiguriert
- [ ] Timeout-Exceptions korrekt gemappt
- [ ] Build+Tests+Detekt+Ktlint grün

### Commit (erst nach User-Freigabe)
`Commit WP 1: Add HTTP timeouts and map Ktor timeout exceptions in SafeCall`

### User-Abnahme
- Agent zeigt Diff + Testergebnisse → User bestätigt `Freigabe WP 1` vor Commit

## WP 2: refetchFromOff – Guard + Snapshot-Konsistenz (Option A, P0)

### Ziele
- Guard: nie User-Content überschreiben; expliziter Nutzer-Override bleibt
- Snapshot-Konsistenz sicherstellen
- Keine Änderung an markUpdateAvailable/Enum/DAO

### Betroffene Dateien
- `core/data/src/main/java/com/example/ezmakro/core/data/food/OfflineFirstFoodRepository.kt`
- Verwandte Tests (nur bei Verhalten notwendig)

### Schritte
1. `Read` OfflineFirstFoodRepository.kt (Zeilen 1-120)
2. In `refetchFromOff(id)`: prüfe `existing`
   - Wenn `existing.cachedStatus == CachedFoodItemStatus.UserModified`: **nicht überschreiben**. Rückgabe: `Result.Success(existing)` (idempotent) ODER `Result.Success(null)` nur falls Vertrag das fordert – bevorzugt `Result.Success(existing)` um lokalen State unverändert zurückzugeben
   - Wenn `existing.isUserCreated` (falls Feld existiert): nicht überschreiben, sauber zurückgeben
   - Expliziter Override nur bei `Original`/andere nicht-UserModified (user-confirmed). Nach erfolgreichem Remote: upsert mit `fresh.copy(id = id)` und Status `Original` (aktuelle Logik beibehalten)
3. Keine Inhaltsfelder von `UserModified` mutieren, Snapshot unverändert lassen
4. Keine Netzwerk-Mutation bei Fehler, lokal unverändert

### Tests
- Testfall: UserModified bleibt unverändert bei refetchFromOff
- Bestehender Test `refetchFromOff replaces the local entry...` prüfen (Original/Edited nicht-UserModified)
- Keine Regression

### Build/QA
```bash
./gradlew :core:data:build :core:domain:build detekt ktlintCheck 2>&1 | tail -30
./gradlew :core:data:test --tests "*OfflineFirstFoodRepositoryTest*" 2>&1 | tail -60
```

### DoD
- [ ] UserModified wird nie überschrieben
- [ ] Snapshot-Konsistenz gewahrt
- [ ] Tests angepasst/ergänzt minimal
- [ ] Build+Detekt+Ktlint+Tests grün

### Commit (nach Freigabe)
`Commit WP 2: Preserve UserModified on OfflineFirstFoodRepository.refetchFromOff (Option A)`

### User-Abnahme
Diff + Tests zeigen Guard → `Freigabe WP 2`

## WP 3: Dezimalkomma-Parsen (P0)

### Ziele
- Zentraler Parser: Komma `,` → `.` vor Parse; Dot bleibt funktionsfähig
- Nur Nutzereingabe parsen, RawWeight/FinalWeight-Typen unverändert
- Kein Tausenderpunkt-Heuristik

### Betroffene Dateien
- `core/domain/src/main/java/com/example/ezmakro/core/domain/util/NumberParsing.kt` (erweitern/vereinheitlichen)
- `core/presentation/.../AddToDiarySheet.kt`, `EditPortionDialog.kt`
- `feature/diary/.../QuickAdd.kt`, `model/DiaryUi.kt`
- `feature/foodpicker/.../IngredientAmountDialog.kt`
- `feature/food/.../FoodEditViewModel.kt`
- `core/data/.../OffProductMapper.kt`, `core/data/.../DataStoreGoalRepository.kt`
- Sonstige `toDoubleOrNull()` an Nutzereingabestellen

### Schritte
1. `Grep` nach `toDoubleOrNull()` (source only, no build)
2. Zentralen Helper `parseDecimalFlexible`/Erweiterung ergänzen (akzeptiert `,` oder `.`)
3. Call-Sites minimal ersetzen; Validierung beibehalten (>, >=0 etc)
4. Keine Formatierungsänderungen

### Tests
- Parser-Unit-Tests (`",5"`, `"0,5"`, `"1.5"`, `"1,50"`, `"1,2,3"`, leer)
- Relevante ViewModel/Mapper-Tests

### Build/QA
```bash
./gradlew build detekt ktlintCheck 2>&1 | tail -40
./gradlew test 2>&1 | tail -30
```

### DoD
- Komma+Punkt funktionieren
- Nur Eingabeparsing, Typen intakt
- Tests grün

### Commit (nach Freigabe)
`Commit WP 3: Fix decimal comma parsing for numeric user inputs`

### User-Abnahme
Diff + Parser-Tests → `Freigabe WP 3`

## WP 4: FoodEditViewModel – Process Death + saveError (P0)

### Ziele
- SavedStateHandle für kritische Felder
- `saveError` konsistent verwalten + restaurierbar
- MVI-Vertrag einhalten

### Betroffene Dateien
- `feature/food/presentation/.../FoodEditViewModel.kt`
- `feature/food/presentation/.../FoodEditState.kt` / Contract
- Tests

### Schritte
1. `Read` ViewModel + State
2. SavedStateHandle injizieren/prüfen, nur serialisierbare Felder persistieren
3. Restore nach Process-Death, `saveError` bei neuem Save/Erfolg/Validierung clearen
4. Unit-Tests Process-Death + saveError

### Tests
- SavedStateHandle-Restore, Save-Error-Pfade

### Build/QA
```bash
./gradlew :feature:food:presentation:build detekt ktlintCheck 2>&1 | tail -30
./gradlew :feature:food:presentation:test 2>&1 | tail -40
```

### DoD
- Process-Death-robust
- saveError korrekt
- Tests grün

### Commit (nach Freigabe)
`Commit WP 4: Fix FoodEditViewModel process death handling and saveError state persistence`

### User-Abnahme
Tests + Diff → `Freigabe WP 4`

## WP 5: RecipeEditorViewModel – Process-Death (Zutaten) (P0)

### Ziele
- Zutatenliste + Mengen nach Process-Death restaurierbar
- Keine Duplikate, IDs konsistent
- RawWeight/FinalWeight gewahrt

### Betroffene Dateien
- `feature/recipes/presentation/.../RecipeEditorViewModel.kt`, State/Contract

### Schritte
1. Identifiziere zustandspflichtige Zutatenfelder (serialisierbar)
2. SavedStateHandle sichern/restaurieren
3. Tests ergänzen

### Tests
- Restore mit Zutatenliste, Add/Remove nach Restore

### Build/QA
```bash
./gradlew :feature:recipes:presentation:build detekt ktlintCheck 2>&1 | tail -30
./gradlew :feature:recipes:presentation:test 2>&1 | tail -40
```

### DoD
- Zutaten restaurierbar, konsistent
- Tests grün

### Commit (nach Freigabe)
`Commit WP 5: Restore recipe ingredients state on RecipeEditorViewModel process death`

### User-Abnahme
Diff + Tests → `Freigabe WP 5`

## WP 6: DiaryRecipeEditViewModel – Success(null) (P0)

### Ziele
- Kein `Success(null)` emittieren
- Konform zu MVI-Skill: Completion als Event oder separater State ohne nullable Payload

### Betroffene Dateien
- `feature/diary/presentation/.../DiaryRecipeEditViewModel.kt`, State/Contract, Screen minimal

### Schritte
1. Alle Success-Emissionen prüfen
2. Falls Completion: `UiEvent.Completed` oder `object Completed : State` statt `Success(null)`
3. Tests anpassen/ergänzen

### Tests
- Keine Success(null)-Übergänge
- DiaryRecipeEdit-Tests grün

### Build/QA
```bash
./gradlew :feature:diary:presentation:build detekt ktlintCheck 2>&1 | tail -30
./gradlew :feature:diary:presentation:test 2>&1 | tail -60
```

### DoD
- Null-Propagation eliminiert
- Contract eindeutig
- Tests grün

### Commit (nach Freigabe)
`Commit WP 6: Fix DiaryRecipeEditViewModel to avoid emitting Success(null)`

### User-Abnahme
Diff + Tests → `Freigabe WP 6`

## WP 7: GoalsViewModel – Fehlerhandling (P1)

### Ziele
- Alle Fehlerpfade behandeln (Result/SafeCall)
- Error konsistent im State
- Keine stillen Fehler

### Betroffene Dateien
- `feature/goals/.../GoalsViewModel.kt`, State/Contract

### Schritte
1. Suspend-Calls prüfen
2. Error-State setzen/clearen
3. Unit-Tests Fehlerfälle

### Tests
- Repository-Error, Validation-Error

### Build/QA
```bash
./gradlew :feature:goals:presentation:build detekt ktlintCheck 2>&1 | tail -30
./gradlew :feature:goals:presentation:test 2>&1 | tail -40
```

### DoD
- Alle Fehlerpfade behandelt
- Tests grün

### Commit (nach Freigabe)
`Commit WP 7: Improve GoalsViewModel error handling and state consistency`

### User-Abnahme
Diff + Tests → `Freigabe WP 7`

## WP 8: MainActivity – Add-to-Diary-Result (P1)

### Ziele
- Activity/Navigation-Result korrekt verarbeiten
- Einmaliger Konsum (SavedStateHandle/Lifecycle-sicher)

### Betroffene Dateien
- `app/src/main/java/.../MainActivity.kt`
- Navigation/Result-Contract falls relevant

### Schritte
1. Result-Contract identifizieren
2. Listener/Observer korrekt einrichten + einmalig konsumieren
3. Minimale Integration

### Tests
- Build + Smoke, keine Nav-Regression

### Build/QA
```bash
./gradlew :app:build detekt ktlintCheck 2>&1 | tail -40
```

### DoD
- Result verarbeitet, einmalig konsumiert
- Build grün

### Commit (nach Freigabe)
`Commit WP 8: Handle Add-to-Diary activity/result in MainActivity`

### User-Abnahme
Diff + Build → `Freigabe WP 8`

## WP 9: App-Manifest – INTERNET explizit (P2)

### Ziele
- `android.permission.INTERNET` explizit in `app/src/main/AndroidManifest.xml` deklarieren
- Nur minimalste Änderung

### Betroffene Dateien
- `app/src/main/AndroidManifest.xml`

### Schritte
1. Prüfen ob bereits vorhanden
2. Falls fehlt: `<uses-permission android:name="android.permission.INTERNET" />` ergänzen
3. Manifest-Merge prüfen

### Build/QA
```bash
./gradlew :app:build detekt ktlintCheck 2>&1 | tail -20
```

### DoD
- Explizit deklariert, Merge ok
- Build grün

### Commit (nach Freigabe)
`Commit WP 9: Mark INTERNET permission appropriately in AndroidManifest`

### User-Abnahme
Diff + Build → `Freigabe WP 9`

## Arbeitsanweisung für Coding-Agents (pro WP)

1. **Lokalisieren**: `Glob/Grep/Read` (nie raten). Liste betroffener Dateien bestätigen.
2. **Implementieren**: Minimaler Fix, bestehende Contracts nicht brechen.
3. **Testen lokal**: Relevante Modul-Builds + Tests laut WP.
4. **Qualitätscheck**: `./gradlew build detekt ktlintCheck` (mind. Module + Gesamt).
5. **PRä-Abnahme vorbereiten**: Diff, Testoutput, DoD-Checklist kurz zusammenfassen.
6. **Freigabe einholen**: **NICHT commiten.** User muss explizit `Freigabe WP X` geben.
7. **Commit erst nach Freigabe**: exaktes Commit-Message aus Plan nutzen. Kein Amend nach Merge/Push unnötig.

## Gesamtreihenfolge
WP1 → WP2 → WP3 → WP4 → WP5 → WP6 → WP7 → WP8 → WP9

## Gesamt-DoD
- [ ] Alle WPs abgearbeitet
- [ ] build/detekt/ktlintCheck grün
- [ ] 524+ Tests grün (0 Failures)
- [ ] ADR-konform
- [ ] Process-Death + Success(null) + Parser + Timeouts + Guards erfüllt
- [ ] Working Tree sauber, Commits pro WP nach Freigabe
