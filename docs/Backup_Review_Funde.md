# Review-Funde Backup-System (Phase 12, WP1–WP5)

Stand: 2026-10-07 · Basis: `docs/Plan_Backup_System.md` (Revision 2) ·
Code-Stand `11866af` · Review ohne Code-Änderungen (nur Lesen + Tests).

## Prüflauf ( reproduzierbar )

| Was | Ergebnis |
|---|---|
| `./gradlew test build detekt ktlintCheck` | ✅ BUILD SUCCESSFUL (907 Tasks) |
| Frisch gererunt: `:core:data:testDebugUnitTest --rerun` | ✅ 49 Backup-/Settings-Tests, 0 Fehler |
| Frisch gererunt: `:core:database:testDebugUnitTest --rerun` | ✅ 11 BackupDao-Tests, 0 Fehler |
| Frisch gererunt: `:feature:settings:presentation:testDebugUnitTest --rerun` | ✅ 16 ViewModel-Tests, 0 Fehler |
| `:feature:settings:presentation:connectedDebugAndroidTest` (Pixel_7, Emulator) | ✅ 20/20 UI-Tests, 0 Fehler |
| Dokumentation WP5 (Passwort-Zusage gestrichen, ADR-007, coding-style, architecture, Bugfix_Plan, Phasenplan, Roadmap) | ✅ geprüft, korrekt |
| Verdrahtung A9 (ein Zahnrad → Settings-Hub → Tagesziele/Backup, `onNavigateToSettings` in MainActivity/DiaryRoutes/DiaryDayScreen) | ✅ geprüft |
| Kein `TODO`/`FIXME` in den neuen Modulen | ✅ |

Gesamt: **kein 🔴- und kein 🔴/🟠-Fehler**. Die Funde sind Kanten und
Unstimmigkeiten, die entweder seltene Randfälle betreffen oder vom Plan nicht
abschließend geregelt sind.

## Befunde

### 🟡 W1 — Preview validiert immer mit MERGE-IDs, auch wenn „Ersetzen" gewählt ist

- **Stelle:** `core/data/.../backup/BackupImporter.kt:46-51`
  (`preview()` ruft `existingIds(ImportMode.MERGE)`), Kombination mit
  `BackupViewModel.kt:61-62` (`OnModeChange` löst kein Re-Preview aus).
- **Sachverhalt:** §3.4 Stufe 4b erlaubt bei MERGE die Auflösung von
  Rezept-Zutaten gegen **bestehende Ziel-DB-Food-IDs**, bei REPLACE nur gegen
  das Archiv. Die Preview läuft aber immer in der MERGE-Variante. Ein Nutzer,
  der im Dialog auf „Ersetzen" umschaltet, sieht weiterhin das Ergebnis der
  MERGE-Prüfung: Rezepte, deren Zutaten nur lokal auflösbar sind, erscheinen
  als gültig, werden beim REPLACE-Import dann aber als
  „unbekannte Food-Referenz" übersprungen.
- **Folge:** Der Import-Report weicht nachweislich vom zuvor gezeigten Preview
  ab (mehr `skippedRows`, andere Counts), obwohl das Preview-Dialog grünes
  Licht gab. Datenverlust entsteht nicht (Zeilen werden mit Grund gelistet,
  A11), aber die Vorschau ist für diesen Modus sachlich falsch.
- **Empfehlung:** (a) `OnModeChange` löst ein Re-Preview mit den
  modus-spezifischen Known-IDs aus (ID-Queries sind billig, Preview ist
  ohnehin nur Stufe 1–4); oder (b) im Plan/ADR festhalten, dass die Preview
  unter MERGE-Annahmen rechnet und der REPLACE-Report davon abweichen kann.
- **Status (2026-10-07):** ✅ umgesetzt wie Variante (a) —
  `BackupRepository.preview(uri, mode)`, `OnModeChange` löst bei offenem
  Dialog ein Re-Preview aus, verspätete Ergebnisse werden verworfen
  (Tests: Importer „known ids of its mode", VM „re-runs the preview" /
  „dialog closed meanwhile"). Plan an Ablauf, §3.4, WP3/WP4 und ACs
  aktualisiert.

### 🟡 W2 — Settings-Restore-Fehler nach DB-Commit meldet „Import fehlgeschlagen"

- **Stelle:** `core/data/.../backup/BackupImporter.kt:53-69` und
  `:264-265` (`restoreSettings`), UI-Reaktion in
  `BackupViewModel.kt:107-126`.
- **Sachverhalt:** A11 schreibt korrekt, dass die Settings erst nach
  erfolgreichem Commit restored werden — das Verhalten, **wenn** der
  DataStore-Schreibvorgang danach fehlschlägt, ist aber nicht geregelt:
  `import()` hängt `restoreSettings` per `flatMap` an und liefert dann
  `Result.Error`, obwohl die fünf Tabellen bereits vollständig committed sind.
- **Folge:** Snackbar zeigt einen Fehler; `state.preview` bleibt offen,
  `state.importReport` bleibt `null`, die Bestandszahlen werden **nicht**
  aktualisiert (nur der Success-Pfad ruft `loadCounts()` auf). Der Nutzer
  hält den Import für gescheitert, die DB ist aber schon verändert. Ein
  erneuter Klick importiert ein zweites Mal (MERGE idempotent, REPLACE
  erneut — jeweils unkritisch, aber verwirrend).
- **Empfehlung:** Nach dem DB-Commit toleranter behandeln — z. B. Erfolgs-
  Report mit `settingsRestored = 0` plus Warnung (neuer `BackupWarning`-Case
  oder `skippedRows`-Eintrag), oder zumindest bei Fehler `loadCounts()` und
  einen eindeutigen Text („Daten übernommen, Einstellungen nicht").
  Mindestens in Plan §3.4 als bewusste Randfallregel festhalten.
- **Status (2026-10-07):** ✅ umgesetzt — neuer
  `BackupWarning.SettingsRestoreFailed`; `import()` liefert nach einem
  Post-Commit-Fehler weiterhin `Result.Success` mit `settingsRestored = 0`
  und der Warnung (Anzeige im Report-Dialog), Cancellation bleibt hart.
  Randfallregel in Plan §3.4, Test „keeps the committed import as a success
  with a warning".

### 🟡 W3 — `servingUnit`-Regel des Validators prüft Ziel-DB-Food-IDs nicht → importiertes Rezept kann unsichtbar werden

- **Stelle:** `core/data/.../backup/BackupValidator.kt:112-125`
  (`referenceIssue`), Gegenstück
  `core/data/.../recipe/mapper/RecipeEntityMapper.kt:51` + `:58-75`
  und `toRecipe` `:17-35`.
- **Sachverhalt:** Die Spiegel-Regel „`servingCount` ohne `rawWeightGrams`
  braucht eine `servingUnit` des Lebensmittels" wird im Validator nur
  geprüft, wenn das Food **im Archiv** liegt (`validFoods[foodId]`). Für
  Food-IDs, die nur in der Ziel-DB existieren (MERGE; Stufe 4 darf die DB
  nicht lesen — planvorgeschrieben), wird die Zutat **ohne** diesen Check
  akzeptiert. Die Laufzeit `resolveIngredient` prüft sie dagegen immer und
  verwirft die Zutat bei Verstoß. Fällt **alle** Zutaten eines Rezepts
  durch, liefert `toRecipe` `null` → das Rezept existiert als Zeile in der
  DB (zählt als „eingefügt"), wird aber in keiner Liste angezeigt.
- **Erreichbarkeit:** Gering. A5 schreibt vor, dass referenzierte Foods immer
  exportiert werden — ein solcher Import braucht eine Archivdatei, deren
  Food entweder als ungültig übersprungen wurde, während die Ziel-DB die
  gleiche ID (z. B. nach lokal korrigiertem Namen) besitzt, oder eine
  handbearbeitete Datei.
- **Empfehlung:** (a) Check in Stufe 5 / Schreibvorbereitung nachziehen
  (dort ist ein DB-Lesen ohnehin schon Teil von `existingIds`), oder
  (b) in `docs/Bugfix_Plan.md` als bekannte Lücke dokumentieren — wie schon
  bei `JsonConverters.decodeFoodItem`/`decodeRecipe`.
- **Status (2026-10-07):** ✅ umgesetzt — `BackupLocalDataSource.readFoodItemIds`
  ersetzt durch `readFoodServingUnits(): Map<String, ServingUnit?>` (DAO-Query
  liefert dazu `serving_label`/`serving_grams_equivalent`), und `referenceIssue`
  prüft `servingCount`-Zutaten gegen die ServingUnit, die **nach dem Import**
  gilt: Ziel-DB vor Archiv (MERGE behält bei Duplikaten die lokale Zeile, der
  Laufzeit-Mapper `resolveIngredient` löst immer gegen die DB auf — damit sind
  Validator und Laufzeit in allen Fällen konsistent). Verstöße landen schon in
  Stufe 4 als `skippedRows`-Grund, das Rezept wird gar nicht erst geschrieben —
  kein „eingefügtes, aber unsichtbares" Rezept mehr und der Report ist ehrlich
  (A11). Tests: „validates the serving unit against a food that exists only in
  the target database", „accepts a target-db-only food that has a serving unit",
  „the target-db serving unit wins over the archive's". Plan §3.4/A19 und
  WP3-AC angepasst. *Hinweis:* `readFoodServingUnits()` bleibt plan-konform
  DB-frei für Stufe 4 — der Ziel-DB-Kontext wird wie bisher von `existingIds`
  angeliefert, der Validator selbst fragt nichts ab.

### 🟢 W4 — Validator strikter als Laufzeit-Mapper: Zutat mit Food-Referenz **und** Snapshot

- **Stelle:** `core/data/.../backup/BackupValidator.kt:89-97`
  (`sourceIssue` verlangt XOR) vs.
  `RecipeEntityMapper.kt:58-74` (beides gesetzt → Food-Referenz gewinnt,
  Zutat wird akzeptiert).
- **Bewertung:** Konservativ und ungefährlich — nur handbearbeitete Dateien
  können beide Felder gleichzeitig haben, die Domain (`RecipeIngredient`)
  erzwingt XOR, Exporte erzeugen es nie. Die KDoc sagt „mirrors the ingredient
  rules", nennt die eine Richtung der Abweichung aber nicht. Ein Satz in der
  KDoc oder in ADR-007 genügt.

### 🟢 W5 — `catchReason` fängt generische `Exception` (inkl. `CancellationException`) und übernimmt Exception-Messages als Grund

- **Stelle:** `core/data/.../backup/BackupValidator.kt:140-150`.
- **Bewertung:** Die Blöcke sind synchron ohne Suspension — ein
  `CancellationException` kann dort praktisch nicht entstehen; ein reales
  Abbruch-/Korruptionsrisiko besteht nicht (anders als in den
  `runCatchingLocal`-Pfaden, die `CancellationException` korrekt
  durchreichen — geprüft in `RoomBackupLocalDataSource.kt:84-94`,
  `BackupImporter.kt:96-97`, `BackupExporter.kt:117-119`). Englische
  Exception-Messages (z. B. von `DateTimeParseException`) landen als
  `skippedRows`-Grund im Report — KDoc begründet das bewusst. Nur als
  Feinheit notiert.

### 🟢 W6 — `versionCode` über deprecated `PackageInfo.versionCode`

- **Stelle:**
  `core/data/.../backup/PackageManagerAppVersionProvider.kt:28-31`
  (`@Suppress("DEPRECATION")`).
- **Bewertung:** `AppVersionProvider.versionCode` ist in der Domäne `Int`,
  das Feld `appVersionCode` im Format ist `Int` (ADR-007) — der Suppress ist
  folgerichtig und der Plan-Vorgabe (kein `buildConfig = true` in
  `:core:data`) geschuldet. Kosmetisch; Alternative wäre
  `PackageInfoCompat.getLongVersionCode(...).toInt()`.

### 🟢 W7 — `pendingUri` überlebt das Schließen des Preview-Dialogs

- **Stelle:** `BackupViewModel.kt:66-67` (`OnDismissPreviewClick` setzt nur
  `preview = null`).
- **Bewertung:** Kein Funktionsfehler: Der Import-Button existiert nur bei
  offenem Preview, die nächste Auswahl überschreibt die URI. Privater
  Zustand bleibt aber länger leben als das UI, das ihn legitimiert. Sauber
  wäre `pendingUri = null` mit dabei.

### 🟢 W8 — Preview-Counts zählen Dateiinhalt, Report zählt Eingefügtes

- **Stelle:** `BackupImporter.kt:298-320` (`buildPreview` nutzt
  `content.*.size`), `buildReport` nutzt `inserted` aus der Transaktion.
- **Bewertung:** Semantisch vertretbar („was steckt in der Datei"), und
  ungültige Zeilen erscheinen als `InvalidRow`-Warnungen im selben Dialog.
  Trotzdem können „4/2/5" im Preview vs. „3 eingefügt" im Report ohne
  Erklärung wirken; ein Hinweis im Dialog oder Plan wäre hilfreich. Nicht
  geregelt im Plan.

### 🟢 W9 — AC-Checkboxen im Plan sind noch alle ungehakt

- **Stelle:** `docs/Plan_Backup_System.md` — Abnahme-Listen der WP1
  (`:552-564`), WP2 (`:835-850`), WP3 (`:1241-1264`), WP4 (`:1583-1603`),
  WP5 (`:1751-1761`).
- **Bewertung:** Rein prozessual: Die Listen wirken wie der menschliche
  Abnahme-Schritt („Human in the Loop" vor `Commit Phase 12`). Inhaltlich
  wurden alle Punkte im Rahmen dieses Reviews geprüft und für erfüllt
  befunden — Ausnahme der bewussten Randfälle W1–W3.

## Geprüft und unauffällig (Auszug)

- **A3–A19 vollständig abgebildet**, u. a.: A5 Export-Umfang
  (`BackupExporter.kt:77-104`, Test „default export contains only own
  favorite and referenced foods"), A7 MERGE-Idempotenz inkl.
  `preSkipped`-Zählung (`BackupDao.kt:132-160`, Tests „insertAll called
  twice …"), A11 Stufenfolge und eine einzige `@Transaction`
  (`replaceAll`-Löschreihenfolge Kinder vor Eltern), A12 Defaults/Frame-
  Pflichtfelder (`BackupCodec.kt:94-123`), A13 kein `rowId`/FTS im Format
  (FTS-Tests grün), A14 Checksumme optional + Warnung, A18 getrennte
  `skippedDuplicates`, A19 Known-IDs-Mengen exakt wie §3.4
  (`BackupImporter.kt:104-143`).
- **CancellationSafety** in allen I/O-Pfaden (Import/Export/Room) korrekt —
  `CancellationException` wird durchgereicht, `BackupValidator`-Sync-Blöcke
  ausgenommen (W5).
- **Roundtrip-Kernnachweis:** Schema-Änderung via `ALTER TABLE` auf lebender
  Verbindung, Export→Import→Export-Determinismus, Abbruch-Test mit echtem
  Rollback (Query-Callback zählt die DELETEs), 10 000-Eintraege-Smoke —
  alles in `BackupRoundTripTest`.
- **UI/Verdrahtung:** SAF-Abbruch (`null`) löst nichts aus, doppelte Klicks
  werden per Flag gesperrt, REPLACE hat den Extra-Bestätigungsdialog,
  Report zeigt alle vier Zahlen getrennt, Snackbars über
  `ObserveAsEvents`/`UiText`, Settings-Hub und Ziele-Routing wie A9
  vorsieht.
- **Abhängigkeiten:** `:feature:settings:presentation` hängt nur an
  `core:domain`/`core:presentation`/`core:design-system` (AC WP4),
  `:core:database` enthält keinen Format-/`@Serializable`-Code außer dem
  bestehenden `JsonConverters`, alle `@Query`s ohne List-Parameter.

## Manueller E2E-Smoke-Test (2026-10-07, WP5 Schritt-für-Schritt)

Durchlauf am Pixel_7-Emulator (Steuerung via `uiautomator dump`, da Screenshots
nicht auswertbar). Datenanlage (WP5-Schritt 2) ersetzte durch eine vorbereitete
Testdatei, hostseitig mit dem echten `BackupCodec` erzeugt und über
`BackupValidator` geprüft: 4 Foods, 2 Rezepte (inkl. Unter-Rezept-Zutat),
5 Tagebucheinträge (inkl. Quick-Add-Snapshot + `recipeOverridden`), 5 Settings,
1 Gewichtseintrag, gültige SHA-256-Checksumme.

| WP5-Schritt | Ergebnis |
|---|---|
| 1 Settings-Hub (Tagesziele/Backup, A9) | ✅ |
| 3 Export prüfen (formatVersion 1, 4/2/5/5) | ✅ `formatVersion: 1`, 4 Foods (A5-Scope), 2 Rezepte, 5 Einträge, 5 Settings, 1 Gewicht |
| 4 Frische Datenbank (`pm clear`) | ✅ |
| 5 Preview 4/2/5 + Zeitraum → Ergänzen → Report | ✅ nach Patch, s. u. |
| 6 Tagebuch: Quick-Add, Tagesanpassung, Rezept-Makros | ✅ Badges korrekt; Makros exakt (655 kcal gesamt, 163,75/Portion, „Pro 100 g" aus Endgewicht) |
| 7 Lokale FTS-Suche | ✅ „Lokale Treffer: Haferflocken selbstgemacht" |
| 8 Zweiter Import: 0 eingefügt, Duplikate getrennt | ✅ 0/16/0/0, keine doppelten Zutatenzeilen |
| 9 Ersetzen mit Bestätigungsdialog | ✅ Dialog + 16 eingefügt, Bestand exakt 4/2/5/1 |
| 10 Export mit Cache-Schalter | △ Schalter bedient, aber keine plain-cached-OFF-Foods vorhanden (nicht teilbar ohne Scan-Daten) |
| 11 Goals nach Import | ✅ 2200/160/70/230/30 im Ziel-Screen; Export enthält dieselben 5 Keys |
| 12 Kaputte Datei (`formatVersion: 99`) | ✅ Snackbar „stammt aus einer neueren App-Version", 0 Crashes, Daten unverändert |
| — Roundtrip-Bytegleichheit | ✅ Export-Checksumme identisch mit Quell-Checksumme (`sha256:3f4cf867…`) |
| — W1 Re-Preview bei Moduswechsel | ✅ zweimal im Dialog bedient, keine Fehler |

**Befund aus dem Durchlauf (behoben):** Der Report-Dialog zeigte
`settingsRestored` nie an, obwohl WP5-Schritt 5 „5 eingefügt" für die Settings
erwartet. Ergänzt: `backup_report_settings_restored`
(„Einstellungen: %1$d wiederhergestellt", nur bei > 0), Test
`reportDialog_showsAllCountersSeparately` erweitert,
`reportDialog_showsSettingsRestoreFailedWarning` prüft die Nicht-Anzeige bei 0.
Gates nach dem Patch grün (build/detekt/ktlint, 907 Tasks), connected 21/21,
Live-Nachführung im Emulator bestätigt „Einstellungen: 5 wiederhergestellt".

Anmerkungen: Die Badges „Rezept geändert" bei den Fixture-Einträgen stammen
aus den bewusst abweichenden Snapshot-`lastModifiedAt`-Werten der Testdatei —
korrektes App-Verhalten (Stale-Snapshot-Erkennung), kein Importfehler.

## Offene Fragen für den Menschen

1. ~~**W1:** Re-Preview bei Moduswechsel (Code-Änderung) oder nur im Plan als
   bekannte Einschränkung dokumentieren?~~ → **erledigt:** Re-Preview
   umgesetzt (Variante a, siehe W1-Status).
2. ~~**W2:** Wie soll sich die UI verhalten, wenn die Settings nach dem
   DB-Commit nicht geschrieben werden können — Toleranz + Warnung oder
   weiterhin harter Fehler?~~ → **erledigt:** Toleranz + Warnung
   (Variante a, siehe W2-Status).
3. ~~**W3:** Nachziehen (Stufe 5) oder als Backlog-Eintrag ins
   `docs/Bugfix_Plan.md`?~~ → **erledigt:** Fix umgesetzt (Ziel-DB-ServingUnit-
   Kontext statt Stufe-5-Nachziehen, siehe W3-Status).
4. ~~**W9:** AC-Checkboxen im Plan abhaken, sobald die Freigabe erteilt ist.~~ →
   **erledigt:** nach Freigabe zum Commit alle AC-Listen der WP1–WP5 (inkl.
   der neuen W1/W2/W3-AC-Zeilen) abgehakt; Phasenplan/Roadmap führen
   Phase 12 als abgeschlossen.
