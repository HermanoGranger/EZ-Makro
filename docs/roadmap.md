# EZ Makro — Roadmap

Die Roadmap spiegelt den Phasenplan wider. Jede Phase folgt dem
Standard-Workflow (Planung → Implementierung → Tests → Self Review →
Acceptance → Commit nur nach Freigabe).

| Phase | Thema | Ziel / Acceptance |
|------|-------|-------------------|
| **0** | Vision & Architektur | Architektur freigegeben, ADRs 001–006 |
| **0.5** | Domain Modeling | Domainmodell eingefroren, Roh-/Endgewicht im Typsystem |
| **1** | Projektsetup | Clean Build, Gradle, Koin, Room, Ktor, Detekt, Ktlint |
| **2** | Core | `core-*` Module stabil (Result, UiText, Theme, HttpClient, DataStore) |
| **3** | Datenbank | Room produktionsreif, FTS-Suche, Migrationen |
| **4** | OpenFoodFacts | Offline-First erfüllt, Nutzeränderungen reversibel |
| **5** | Food | Lebensmittel vollständig nutzbar (Suche, Detail, Barcode, Favoriten) |
| **6** | Recipes | Rezeptberechnungen korrekt (Roh-/Endgewicht, Skalieren, Quick Meals) |
| **7** | Meal Log | Tagestracking vollständig |
| **8** | Dashboard | Dashboard vollständig (Kalorien, Makros, Trends) |
| **9** | Goals | Ziele konfigurierbar |
| **10** | Weight Tracking | Gewicht, Trend, Charts |
| **11** | Statistics | Wochen-/Monats-/Jahresstatistiken |
| **12** | Backup | JSON Export/Import, optional verschlüsselt |
| **13** | Polish | Accessibility, Performance, Tablets |
| **14** | Release Candidate | Alle Tests grün, Release Build, crashfrei |

## Personas

- **Lena (Alltags-Tracker):** will schnell Mahlzeiten erfassen, Barcode-Scan
  nutzen, Tagesziele im Blick. Scheut Aufwand.
- **Marco (Meal-Prep):** kocht wöchentlich große Portionen, braucht
  Rezept-Skalierung und Endgewicht für exakte Nährwerte pro Portion.
- **Sophie (Privatsphäre-Fokus):** lehnt Accounts/Cloud ab, schätzt lokales
  Backup und volle Datenkontrolle.

## Zentrale User Flows (überblickhaft)

1. **Schnell erfassen:** Barcode scannen → Treffer zeigen → Portion wählen →
   ins Tagebuch.
2. **Eigenes Lebensmittel:** Scan ohne Treffer → "Anlegen" mit vorgefülltem
   Barcode → Nährwerte eingeben → speichern.
3. **Rezept bauen:** Zutaten (Roh) hinzufügen → optional Endgewicht →
   Nährwerte pro 100 g + pro Portion → speichern → ins Tagebuch.
4. **Tagesrückblick:** Dashboard öffnen → Restkalorien/Trends sehen →
   Copy Yesterday bei Bedarf.
5. **Backup:** Einstellungen → Export (optional Passwort) → späterer Import.

## Meilensteine

- **M1 (nach Phase 3):** App startet, lokale Daten haltbar, Offline-Basis.
- **M2 (nach Phase 5):** Lebensmittel-Erfassung komplett nutzbar.
- **M3 (nach Phase 8):** Tägliches Tracking inkl. Dashboard möglich.
- **M4 (nach Phase 12):** Vollständige, backup-fähige App.
- **Release (Phase 14):** RC, Polish.
