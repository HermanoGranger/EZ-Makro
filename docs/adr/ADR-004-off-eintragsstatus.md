# ADR-004: Umgang mit nutzerbearbeiteten OpenFoodFacts-Einträgen

## Status

Angenommen (Phase 0) — Umsetzung in Phase 4

## Kontext

OpenFoodFacts liefert Schätzwerte, die Nutzer:innen lokal korrigieren wollen
(z. B. abweichende Portionsgröße). Ein erneuter API-Sync darf diese
Nutzerkorrekturen nicht stillschweigend überschreiben. Gleichzeitig muss der
Nutzer erkennen können, dass eine neuere OFF-Version existiert, und die
Originaldaten jederzeit wiederherstellen können.

## Entscheidung

- Jeder lokal gecachte OFF-Eintrag trägt einen Status
  (`Original` / `UserModified` / `UpdateAvailable`) als sealed/class-Enum.
- Bearbeitung durch den Nutzer → Status `UserModified`. Ein erneuter Sync
  **überschreibt einen `UserModified`-Eintrag niemals automatisch.**
- Das ursprüngliche OFF-Ergebnis wird **zusätzlich** zur bearbeiteten Version
  gespeichert (Snapshot-Spalte), damit "Zurücksetzen auf Original" jederzeit
  möglich ist.
- Die UI kommuniziert den Bearbeitungsstatus aktiv (Badge "lokal angepasst").
- Dem Nutzer stehen zwei explizite Aktionen zur Verfügung:
  1. **Zurücksetzen auf Original** (wiederherstellen des Snapshots)
  2. **Neu von OFF beziehen** (mit Bestätigungsdialog, da eigene
     Anpassungen verworfen werden)

## Alternativen

- **Auto-Überschreiben beim Sync:** zerstört Nutzerarbeit — nicht akzeptabel.
- **Nur lokale Kopie ohne Original-Snapshot:** "Zurücksetzen" nicht möglich.

## Trade-offs

- Zusätzliche Speicherung (Snapshot) und Konflikt-State-Maschine.
- Nutzer behält volle Kontrolle und Nachvollziehbarkeit über seine Daten.
