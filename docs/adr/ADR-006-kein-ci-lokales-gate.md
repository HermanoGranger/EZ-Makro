# ADR-006: Kein CI-Tooling (lokales Gradle-Gate)

## Status

Angenommen (Phase 0) — optional

## Kontext

EZ Makro ist ein privates Einzelentwickler-Projekt. Der Aufwand für
CI-Infrastruktur (Runner, Secrets, Pipeline-Pflege) steht in keinem
Verhältnis zum Nutzen für einen einzelnen Entwickler ohne
Kollaborations-/Review-Zwang.

## Entscheidung

- **Kein CI-Tooling** wird eingerichtet.
- Stattdessen dient `./gradlew check` (Build + Detekt + Ktlint + Tests) als
  **manuelles lokales Gate** vor jedem Commit.
- Diese Regel ist in `AGENTS.md` verankert.

## Alternativen

- **GitHub Actions / ähnliches:** sinnvoll bei Teams oder Open-Source mit
  Contributors — hier unverhältnismäßig.

## Trade-offs

- Keine automatische Qualitätskontrolle bei vergessenen lokalen Checks.
- Deutlich weniger Infrastrukturpflege; passt zum Solo-Kontext.
