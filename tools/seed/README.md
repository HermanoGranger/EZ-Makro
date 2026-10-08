# tools/seed — BLS-Seed-Pipeline

Erzeugt das versionierte Seed-Asset
`core/data/src/main/assets/seed/food_seed_v1.json` (251 vegane Lebensmittel,
BLS 4.0, CC BY 4.0, ADR-008). Nur Dev-Tooling — läuft nicht auf dem Gerät.

## Pipeline

```bash
# 1. BLS-Excel (14 MB) laden → tools/seed/cache/ (gitignored)
python3 tools/seed/fetch_bls.py

# 2. XLSX → CSV (stdlib-Parser, braucht kein openpyxl)
python3 tools/seed/bls_extract.py

# 3. Auswahl + Asset-Generierung; prüft Validität (vegan, eindeutige Namen …)
python3 tools/seed/build_seed.py --check

# Vorschau der Auswahl mit Anzeigename + Nährwerten:
python3 tools/seed/build_seed.py --list

# Asset schreiben (default, ohne --check):
python3 tools/seed/build_seed.py
```

## Auswahl & Benennung

- `selection.py`: `HANDPICKED` (explizite BLS-Codes pro Kategorie),
  `CATEGORIES` (Auto-Auswahl Obst/Gemüse per Keyword-Budget),
  `NAME_OVERRIDES` (BLS-Jargon → App-taugliche Namen; Duplikate sind ein
  Build-Fehler), `NON_VEGAN_PATTERN`/`VEGAN_ALLOW_PATTERN`.
- Änderungen an Auswahl/Namen hier vornehmen, dann Schritt 3 neu erzeugen.
  Nährwert-Änderungen an einem Seed erfordern einen neuen `seedVersion` im
  Asset (siehe `build_seed.py`), damit `BlsFoodSeeder` das Re-Seed auslöst.

## Lizenz-Regel

BLS-Daten (CC BY 4.0) nur als dieses kuratierte Derivat mit Nennung
(Max Rubner-Institut + DOI) committen — **nie** die Rohdaten
(`tools/seed/cache/` ist gitignored).
