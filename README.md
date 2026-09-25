# ANVEX backend

This is the standalone backend for the ANVEX lab. Java 17+ and Maven are needed to build it. The finished runtime is `target/anvex-1.0-SNAPSHOT.jar` together with the entire `target/lib/` directory.

Build and test from this folder:

```text
mvn package
```

Start with `./run-backend.ps1 -Port 9090` on Windows or `./run-backend.sh 9090` on Linux/macOS. Both launch the packaged JAR without Maven. Run `./run-smoke.ps1` or `./run-smoke.sh` to exercise the packaged server, attack blocking, source isolation, event persistence and release. The smoke scripts create a separate database under `target/`.

The backend binds to 127.0.0.1 by default. For clients on other devices, set `ANVEX_HOST` to this computer's LAN address or `0.0.0.0`, and set a nonempty `ANVEX_API_TOKEN` before starting it. Open the chosen TCP port and API port only on the intended lab network. The token protects the HTTP dashboard API; the simulator's LOGIN command intentionally remains open. The supplied sourceId identifies a simulated lab source and is not a verified network identity.

The API port is 9091 by default (override with `ANVEX_API_PORT` or the `anvex.apiPort` system property). The HTTP API serves JSON and shares the same backend state, engines, run history and persistence as the TCP protocol.

## HTTP API (Device 2 — SOC Dashboard)

All endpoints are under `/api`. When `ANVEX_API_TOKEN` is configured, send `Authorization: Bearer <token>`.

| Endpoint | Purpose |
|---|---|
| `GET /api/status` | Current run ID, tracked source count, API port. |
| `GET /api/sources` | Live score, level, protection phase, expiry and evidence for every tracked source. |
| `GET /api/sources/{sourceId}` | Live snapshot for one source. |
| `GET /api/sources/{sourceId}/history` | Persisted history for a source (last 200 events). |
| `GET /api/events?after={id}&limit={n}` | Persisted events after an ID, max 200 at a time; poll and pass the last returned ID. |
| `GET /api/runs`, `GET /api/runs/{runId}` | Scenario history and run detail. A new backend session starts a run automatically. |
| `GET /api/runs/{runId}/events` | Persisted events for a run. |
| `GET /api/metrics` | Current attempt and incident counters. |
| `GET /api/config` | Read validated detection and protection settings. |
| `PUT /api/config` | Change settings with a JSON body, e.g. `{"highDelayMs": 500}`. Returns the full updated config. |
| `GET /api/events/stream` | Server-Sent Events stream pushing live security events as JSON, e.g. `{"type":"THREAT_CHANGED","sourceId":"192.168.1.24","score":73,"level":"HIGH"}` — drives the dashboard's live updates and animations. |

## TCP protocol (Device 1 — Lab Client)

The TCP protocol is UTF-8, one command and one response line per connection:

| Command | Purpose |
|---|---|
| `LOGIN\|username\|password\|ATTACKER-or-LEGITIMATE\|sourceId` | Simulator login; sourceId may be omitted to use the socket address. Returns SUCCESS, FAILURE, or BLOCKED. |
| `START_RUN\|label`, `END_RUN` | Run lifecycle from the lab client; the backend generates the authoritative run ID. Token-gated when `ANVEX_API_TOKEN` is configured (`AUTH\|token\|START_RUN\|label`). |

For example, after building:

```text
java -cp "target/anvex-1.0-SNAPSHOT.jar;target/lib/*" com.anvex.tools.SmokeClient 127.0.0.1 9090 "192.168.1.50" 20 100 lab_target wrongpass ATTACKER
```

Use `:` instead of `;` in the classpath on Linux/macOS. The runtime H2 database lives in `data/` relative to the backend process's working directory. Startup creates missing tables and adds the new score/metadata columns without deleting existing events or users. Keep the same working directory when restarting if you want the same history. Run configuration is in memory and resets to defaults when the process restarts.

The former JavaFX placeholder is excluded from this backend. The JAR starts `com.anvex.backend.AnvexBackend` and has no JavaFX runtime dependency.
