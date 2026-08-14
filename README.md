# Tartan Smart Home

A smart-home control platform with real security rules, simulated houses, and a web UI.

Tartan sits between residents and their house hardware. It reads sensor state (door, lock, lights, HVAC, occupancy), applies safety rules, and writes the next legal state back to the house. You can run it locally with Docker: four simulated houses, MySQL, and the control platform on port 8080.

Built as a CMPUT 402 group project (University of Alberta, Winter 2026) by Seerat Kaur, Andrew Harris, Gunish Sharma, and Abdulrahman Khafagy.

## What it does

**Home security**
- Alarm when a door opens while the house is empty and the alarm is armed
- Night lock: auto-locks the door during a configured night window (including midnight-crossing schedules like 22:00–06:00)
- Keyless entry: unlocks for an authorized approach when the feature is enabled
- Electronic lock: lock/unlock from an access panel with passcode checks
- Intruder handling that takes priority over convenience features

**Climate and occupancy**
- HVAC heater/chiller toward a target temperature
- Humidifier control
- Lights only stay on when someone is home

**Reporting and experiments**
- Per-house usage reports (for example light/energy behaviour)
- A/B report variants assigned to different houses
- Results page comparing variants: `/smarthome/experiment/results`

## Architecture

```
Browser  →  Dropwizard platform (:8080)  →  MySQL (history)
                         ↓
              Python house simulators (:5050–5053)
```

| Piece | Where | Role |
| --- | --- | --- |
| Platform | `Tartan/smart-home/Platform` | Java 11 Dropwizard REST API, rule engine, UI |
| House simulators | `Tartan/smart-home/HouseSimulator` | Python hubs that pretend to be physical houses |
| Database | `Tartan/smart-home/Database` | MySQL 8 historian |
| CI | `.github/workflows` | Build, unit tests, system tests, deploy |

The rule engine lives in `StaticTartanStateEvaluator` (with `KeylessEntry` and `ElectronicOperation` for lock behaviour). Every requested state is checked before it is applied, so the house cannot end up in an inconsistent configuration.

## Run it locally

Needs Docker.

```bash
cd Tartan/smart-home
docker compose up --build
```

Then open:

- House UI: [http://localhost:8080/smarthome/state/mse](http://localhost:8080/smarthome/state/mse)
- Other default houses: `cmu`, `eng`, `sci` (same URL pattern)
- Experiment results: [http://localhost:8080/smarthome/experiment/results](http://localhost:8080/smarthome/experiment/results)

Default logins are in `Tartan/smart-home/Platform/config.yml` (for example **mse**: `admin` / `1234`).

Rebuild after code changes:

```bash
docker compose down && docker compose build --no-cache && docker compose up
```

### Without Docker

1. Start MySQL and load `Tartan/smart-home/Database/init.sql` (user `tartan` / `tartan1234`, database `TartanHome`).
2. Start house hubs, e.g. `python3 simple_server.py localhost 5050` from `HouseSimulator`.
3. From `Platform`: `./gradlew run` (or `./gradlew shadowJar` then `java -jar build/libs/tartan-1.0-SNAPSHOT.jar server config.yml`).

More detail: [Tartan/docs/build_instructions.md](Tartan/docs/build_instructions.md).

## Tests

From `Tartan/smart-home/Platform`:

```bash
./gradlew test
```

CI also runs a Docker system test before deploy. Coverage is collected with JaCoCo.

## Continuous deployment

Pushes that pass GitHub Actions are built into Docker images. Deploy rebuilds only the **platform** container so house simulators and MySQL keep running.

- `save-backup.sh` snapshots the current deploy before a new version goes out
- `rollback.sh` restores the previous version in about a minute

Backups keep `previous_working`, `latest_working`, and a handful of timestamped snapshots.

```bash
cd Tartan/smart-home
./rollback.sh
```

## Repo layout

```
Tartan/smart-home/Platform/     Java platform, rules, UI, tests
Tartan/smart-home/HouseSimulator/
Tartan/smart-home/Database/
Tartan/docs/                    Build notes and system description
.github/workflows/              CI / CD
```
