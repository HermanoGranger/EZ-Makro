# ADR-001: Clean Architecture + MVI

## Status

Angenommen (Phase 0)

## Kontext

EZ Makro ist eine langfristig gepflegte Solo-App mit wachsender Feature-Menge
(Lebensmittel, Rezepte, Tagebuch, Statistik). Die Architektur muss
Änderungen an einzelnen Layern (z. B. Persistenz-Technologie oder UI-Toolkit)
ohne Dominoeffekte erlauben und testbar bleiben.

## Entscheidung

- **Clean Architecture** mit den Layern UI → Presentation → Domain → Data.
  Die Domain-Schicht ist reines Kotlin ohne Android-Abhängigkeiten.
- **MVI** im Presentation-Layer: unidirektionaler Datenfluss über
  `State` / `Action` / `Event`. Der `ViewModel` ist die einzige Stelle, die
  State mutiert.
- Feature-Module hängen nur von `:core-*` ab, nicht voneinander.

## Alternativen

- **MVVM ohne MVI:** weniger Boilerplate, aber schwächerer Vertrag für
  unidirektionalen State — bei wachsender UI-Komplexität fehleranfälliger.
- **Redux-artige globale Store-Lösung:** überdimensioniert für eine
  Solo-App ohne geteilten globalen State jenseits der Repository-Daten.

## Trade-offs

- Mehr Boilerplate (Action/Event-Klassen) im Vergleich zu lockerem MVVM.
- Klarer, vorhersagbarer Datenfluss und deutlich einfachere UI-Tests
  rechtfertigen den Aufwand.
