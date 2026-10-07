# EZ Makro -- Scope & Features

## Vision

Eine offline-first Android-App zum Tracken von Kalorien, Makros und
Rezepten ohne Werbung, Accounts oder unnötige Gamification.

## Kernprinzipien

-   Offline First (Room als Single Source of Truth)
-   Clean Architecture
-   MVI
-   Modularisierung
-   OpenFoodFacts als Datenquelle
-   Eigene Lebensmittel und Rezepte
-   Datenschutz: lokal, optionales Backup

## Features

### Lebensmittel

-   Suche lokal (Room FTS) + OpenFoodFacts
-   Barcode-Scan (CameraX + ML Kit)
    -   Kein Treffer im Scan → direkte Überleitung in "Eigenes Lebensmittel
        anlegen", Barcode wird vorausgefüllt
-   Favoriten
-   Zuletzt verwendet
-   Eigene Lebensmittel
-   **Bearbeiten lokal gecachter OpenFoodFacts-Einträge**
    -   Bearbeitete Einträge werden in der UI klar als "lokal angepasst"
        gekennzeichnet
    -   Zwei Optionen jederzeit verfügbar: Zurücksetzen auf das
        ursprünglich gecachte Original, oder Neu von OpenFoodFacts
        beziehen (mit Bestätigung, da eigene Anpassungen dabei verworfen
        werden)

### Rezepte

-   Zutaten kombinieren
-   Portionen oder Endgewicht
    -   Gesamtnährwerte immer aus den Rohzutaten berechnet; "pro 100 g"
        wird nur aus Gesamtnährwerte ÷ gemessenem Endgewicht abgeleitet,
        nie aus dem Rohgewicht-Durchschnitt
-   Mengenangaben auch jenseits von Gramm (z. B. "1 Ei", "1 Scheibe", "1 EL")
    über definierte Portionsgrößen
-   Meal Prep
-   Kategorien
-   Duplizieren
-   Skalieren
-   **Quick Meals / Kombos**: einfache Gruppen von Lebensmitteln als
    Vorlage speichern (z. B. "mein Frühstück"), ohne volle
    Rezept-Skalierungslogik -- schneller anzulegen als ein echtes Rezept

### Tagebuch

-   Frühstück, Mittag, Abend, Snacks
-   Quick Add
-   Verlauf
-   Copy Yesterday

### Dashboard

-   Kalorien
-   Makros
-   Ziele
-   Trends

### Weitere Features

-   Gewichtstracking
-   Statistiken
-   Backup/Restore (JSON Export/Import, **ohne** Verschlüsselung —
    explizite Entscheidung, s. A3 in `docs/Plan_Backup_System.md`)
-   Dark Mode
