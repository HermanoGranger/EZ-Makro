# ADR-007: Versioniertes Backup-Format (JSON, `formatVersion`)

## Status

Angenommen (Phase 12) — WP2; finalisiert in WP5

## Kontext

Phase 12 braucht einen Export/Import, der Daten über Geräte- und
App-Versionen hinweg transportiert. Ein Dump der SQLite-Datei wäre an das
aktuelle Room-Schema gekoppelt: jede Spaltenänderung, jede Migration und das
FTS-Handling würden bestehende Backups unlesbar machen. Gleichzeitig soll die
Datei für Nutzer:innen lesbar und debugbar bleiben, und spätere
Felderweiterungen dürfen alte Backups nicht brechen.

## Entscheidung

- **Reines JSON** (Datei `ezmakro-backup-<timestamp>.json`,
  `"format": "ezmakro.backup"`), **kein ZIP-Container, keine Verschlüsselung**
  (A3/A4). Die offene `formatVersion` erlaubt später einen verschlüsselten
  Container ohne Bruch.
- **Eigenes Formatversionierungssystem, unabhängig vom DB-Schema (A2):**
  `formatVersion` steuert den Import. `dbSchemaVersion` ist rein informativ —
  der Import verzweigt **nie** darauf.
- **Logisches Format statt Spaltenabzug (A1):** Die DTO-Felder in
  `BackupArchive.kt` sind das CamelCase-Pendant der heutigen Spalten, aber eine
  Spaltenumbenennung oder ein Tabellenumbau ändert das Format nicht.
- **Prüfsumme (A14):** SHA-256 über das kanonisch re-encodierte `content`,
  geschrieben als `"sha256:<hex>"`. `settings` ist dafür eine **sortierte
  Liste** (keine `Map`), damit derselbe Datenbestand immer denselben Hash
  liefert. Ein Mismatch — oder ein fehlendes `checksum`-Feld — ist **kein**
  Abbruch, sondern eine Warnung im Import-Report; eine abgeschnittene Datei
  fällt ohnehin schon beim JSON-Parser auf und ist dann ein harter Fehler.
- **Zwei Import-Modi (A6/A7):** *Ergänzen* (Default) fügt nur Fehlendes hinzu
  und überschreibt nie (`INSERT … IGNORE` auf den natürlichen Schlüsseln);
  *Ersetzen* leert die fünf Tabellen FK-sicher und spielt das Backup 1:1 ein.
- **Kein `rowId`, keine FTS-Sektion (A13); Snapshots bleiben opake Strings
  (A8).**

### Forward-Kompatibilitätsregeln (verbindlich)

| Regel | Umsetzung |
|---|---|
| Formatversion unabhängig vom DB-Schema | `formatVersion` steuert den Import; `dbSchemaVersion` ist nur Info für den Nutzer |
| Vorwärtskompatibilität | Alle **optionalen** Felder mit Default, `Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }` |
| Pflichtfelder | `format`, `formatVersion`, `appVersion*`, `exportedAtEpochMillis`, `dbSchemaVersion`, `content` bleiben **default-frei** (A12) und enden als `Corrupted`, bei `format` als `NotAZBackup`; `checksum` ist optional (`Default ""` → Warnung, A14) |
| Versionsbereich | `MIN_SUPPORTED_FORMAT_VERSION = 1 <= formatVersion <= FORMAT_VERSION`; darüber → harter Fehler „Backup stammt aus einer neueren App-Version" (`UnsupportedVersion`) |
| Snapshots opak | `food_snapshot`, `recipe_snapshot`, `recipe_snapshot_json` laufen als `String?` unverändert durch; die Dekodierbarkeit wird beim Import einmal geprüft (A8) |
| Determinismus der Prüfsumme | `settings` als sortierte Liste; alle übrigen Sektionen sind `List` |
| Neue Domain- und Format-Felder brauchen Defaults | A12, `docs/coding-style.md` |
| Unbekannte `cachedStatus`-Werte | bleiben als `String` im Format; der Import fällt wie `ScalarConverters.textToCachedStatus` auf `ORIGINAL` zurück |

## Alternativen

- **SQLite-Datei kopieren:** kleinste Änderung, aber bricht bei jeder
  Migration, bindet das Backup an die App-Version und ist nicht debugbar —
  abgelehnt (A1, §1.3).
- **Room-Spalten 1:1 exportieren:** jede Schemaänderung bricht alte Backups;
  genau das soll das Format überleben.
- **ZIP/gzip oder Verschlüsselung von Anfang an:** Debugging wird schwer,
  der Nutzerentscheid lautet ausdrücklich dagegen (A3/A4).

## Trade-offs

- Ein zweites, gepflegtes Schema neben der DB — Mapper und Tests pro
  Formatversion.
- Unbekannte Felder werden stillschweigend verworfen: ein Import verliert
  Daten, die eine neuere App geschrieben hat (abgefangen durch den
  Versionsbereich oberhalb von `FORMAT_VERSION`).
- Unverschlüsselte Datei im SAF: Nutzer:innen müssen selbst auf den Ort
  achten — bewusste Entscheidung (A3).
