# HardcoreSquad

Kooperatives Spigot/Paper Hardcore-Team-Plugin (1 Team, gemeinsame Leben, Milestones, Reset-Flow).

## Features

- Gemeinsame Team-Leben mit PRESTART/RUNNING/Endzustand
- Milestone-Tracking mit Zeiten
- Sidebar-Scoreboard mit Run-Statistiken
- Welt-Reset per `/hc reset`

## Build

```bash
./gradlew clean build
```

JAR liegt danach in `build/libs/`.

## Commands

- `/hc start <lives>`
- `/hc status`
- `/hc reset`

Permission: `hardcoreteam.admin`

## Release (GitHub Actions + Semantic Release)

- Workflow: `.github/workflows/release.yml`
- Runner: `self-hosted` + `linux`
- Semantic Release erstellt GitHub Releases aus Conventional Commits.
- Die gebaute Plugin-JAR wird als Release-Asset angehängt.

### Commit-Konvention (wichtig)

Beispiele:

- `feat: add scoreboard colors`
- `fix: reset world cleanup on shutdown`
- `chore: update dependencies`

Nur Commits nach Conventional-Commits lösen automatische Versionssprünge korrekt aus.
