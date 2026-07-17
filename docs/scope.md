# EZ Makro — Scope

Dieses Dokument konkretisiert `Scope_und_Features.md` für Phase 0: Was ist
drin, was ist ausdrücklich nicht drin.

## In Scope (MVP bis Release)

### Lebensmittel

- Lokale Suche (Room FTS) + OpenFoodFacts-Suche
- Barcode-Scan (CameraX + ML Kit)
  - Kein Treffer → direkte Überleitung in "Eigenes Lebensmittel anlegen",
    Barcode vorausgefüllt
- Favoriten, Zuletzt verwendet
- Eigene Lebensmittel anlegen/bearbeiten
- Lokales Bearbeiten gecachter OpenFoodFacts-Einträge
  - Status `Original` / `UserModified` / `UpdateAvailable`
  - UI-Badge "lokal angepasst"
  - Zurücksetzen auf Original / Neu von OFF beziehen (mit Bestätigung)

### Rezepte

- Zutaten kombinieren (immer aus Rohzutaten berechnet)
- Portionen oder gemessenes Endgewicht
- "pro 100 g" ausschließlich aus Gesamtnährwerte ÷ Endgewicht
- Mengenangaben jenseits von Gramm via `ServingUnit` (z. B. "1 Ei")
- Meal Prep, Kategorien, Duplizieren, Skalieren
- Quick Meals / Kombos (vereinfachte Vorlagen ohne Skalierungslogik)

### Tagebuch

- Mahlzeiten: Frühstück, Mittag, Abend, Snacks
- Quick Add, Verlauf, Copy Yesterday

### Dashboard & Ziele

- Kalorien, Makros (Protein, Fett, KH, Ballaststoffe), Trends
- Tagesziele konfigurierbar

### Weitere Features

- Gewichtstracking mit Trend
- Statistiken (Woche/Monat/Jahr)
- Backup/Restore (JSON, optional passwortverschlüsselt)
- Dark Mode

## Aus dem Scope gestrichen

- **App-Widgets (Glance):** Ursprünglich in ADR-005 geplant, aber verworfen.
  Die App bietet keine Home-Screen-Widgets.

## Nicht-Ziele (explizit ausgeschlossen)

- Kein Backend, keine Cloud-Sync, keine Accounts
- Keine Social-Features, keine Teilen/Community
- Keine Werbung, kein In-App-Purchase, kein Monetarisierungsdruck
- Keine künstliche Gamification (keine Streaks-Belohnungen, keine Punkte)
- Keine Ernährungsberatung / KI-Coaching
- Keine Multi-Device-Sync (Backup/Restore ist bewusst manuell)
- Keine iOS-/Web-/Desktop-Variante (Android-only)
- Keine Sprachauswahl über Deutsch hinaus im MVP (i18n-tauglich gebaut,
  aber nur de-Strings gepflegt)
