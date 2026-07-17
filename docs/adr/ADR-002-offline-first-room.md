# ADR-002: Offline-First-Strategie (Room als Single Source of Truth)

## Status

Angenommen (Phase 0)

## Kontext

Die App wird primär ohne verlässliche Netzverbindung genutzt (z. B. beim
Einkaufen oder Kochen). Nutzerdaten dürfen nie verloren gehen, nur weil das
Netz wegbricht. OpenFoodFacts ist eine ergänzende, nicht zwingende Quelle.

## Entscheidung

- **Room** ist die alleinige Single Source of Truth für alle UI-Daten.
- Remote-Daten (OpenFoodFacts) werden ausschließlich genutzt, um den lokalen
  Cache zu befüllen oder zu aktualisieren (Cache-Then-Network wo sinnvoll).
- Fehlgeschlagene Remote-Calls resultieren in einem `Result.Error` und
  niemals in einer leeren oder abstürzenden UI.
- Die App ist ohne Netz voll funktionsfähig.

## Alternativen

- **Network-First mit lokaler Fallback:** schlechtere Offline-Erfahrung und
  abhängig von API-Verfügbarkeit/Ratelimits.
- **Reine In-Memory-Haltung:** Datenverlust bei App-Neustart — nicht akzeptabel.

## Trade-offs

- Zusätzlicher Aufwand für Caching, Mapper (DTO ↔ Entity) und
  Konfliktauflösung (siehe ADR-004).
- Maximale Offline-Robustheit und Datenhoheit beim Nutzer.
