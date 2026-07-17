# ADR-003: Rohgewicht vs. Endgewicht bei Rezeptberechnung

## Status

Angenommen (Phase 0) — Details in Phase 0.5

## Kontext

Beim Kochen verändert sich das Gewicht von Zutaten (Wasser verdampft,
Flüssigkeit zieht ein), die Nährwerte der Rohzutaten selbst bleiben jedoch
erhalten. Würde man "pro 100 g" aus dem Rohgewicht-Durchschnitt ableiten,
kämen falsche Angaben heraus. Die Unterscheidung ist zentral für die
Ernährungsgenauigkeit und darf nicht nur als Konvention existieren.

## Entscheidung

- Gesamtnährwerte eines Rezepts = Summe der **Rohzutaten**-Nährwerte.
- Das **gemessene Endgewicht** ist ein optionales, vom Nutzer erfasstes Feld.
- "Nährwerte pro 100 g" werden **ausschließlich** aus
  `Gesamtnährwerte ÷ FinalWeight` berechnet — niemals aus der Summe der
  Rohgewichte.
- Die Trennung wird **im Typsystem erzwungen**: zwei getrennte Value Objects
  `RawWeight` und `FinalWeight`. Eine Vermischung ist compile-time unmöglich.

## Alternativen

- **Ein einzelner `Weight`-Typ mit Konvention:** fehleranfällig, Vermischung
  erst zur Laufzeit bemerkbar.
- **Endgewicht verpflichtend:** bricht den Flow für Nutzer, die nur
  Rohdaten kennen; daher optional.

## Trade-offs

- Etwas mehr Typ-Verwaltung in der Domain-Schicht.
- Vermeidet systematisch falsche Nährwertangaben — Kernanforderung der App.
