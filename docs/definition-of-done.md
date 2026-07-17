# EZ Makro — Definition of Done

Eine Phase (bzw. ein Feature/Task) gilt erst dann als "Done", wenn alle
Punkte erfüllt sind.

## Funktional

- [ ] Geforderte Features aus dem Phasenplan sind vollständig implementiert.
- [ ] Akzeptanzkriterien der Phase sind erfüllt.

## Qualität & Tests

- [ ] `./gradlew build` läuft fehlerfrei durch.
- [ ] `./gradlew detekt` ohne Verstöße.
- [ ] `./gradlew ktlintCheck` ohne Verstöße.
- [ ] Unit Tests geschrieben und grün (Mindestabdeckung für kritische Logik
      wie Rezeptberechnung, Fehler-Mapping).
- [ ] Integrationstests / UI-Tests wo laut Phasenplan gefordert, grün.

## Architektur & Konventionen

- [ ] Clean-Architecture- und MVI-Konventionen eingehalten.
- [ ] Domain-Schicht ohne Android-Abhängigkeiten.
- [ ] `UiText` für nutzersichtbare Strings verwendet.
- [ ] Benannte Domänenregeln (Roh-/Endgewicht, OFF-Status) im Typsystem
      erzwungen, nicht nur per Konvention.

## Dokumentation

- [ ] Relevante Docs (Architektur, ADRs, Skills) bei Architekturänderungen
      aktualisiert.
- [ ] Keine offenen `TODO`-Kommentare im ausgelieferten Code.
- [ ] Standard-Review (Selbstreview, Architekturentscheidungen, Human in the
      Loop, Testreport, Acceptance-Checkliste) dokumentiert.

## Commit

- [ ] Commit **erst nach expliziter Freigabe** durch den Nutzer
      (`Commit Phase X`). Kein eigenständiger Commit.
