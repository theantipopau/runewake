# Local Development Environment — RuneWake

## Prerequisites

| Where | Needed |
| --- | --- |
| Windows (Git Bash) | `git` only — the portable JDK 8 and Ant are committed in `Portable_Windows/` |
| Linux | JDK 8 + Ant (e.g. `apt install ant`, Temurin 8) or keep `JAVA_HOME`/`ANT_HOME` unset to use the vendored copies, which also work here |
| macOS | as Linux, but unverified — see [building.md](building.md) |
| Anything | Python 3 with `sqlite3` (only for seeding the SQLite DB and building releases) |

No Docker, no cloud services, no IDE, no network access is required to build
or run the game locally.

## Quick start

```bash
git clone https://github.com/theantipopau/runewake.git && cd runewake
scripts/dev.sh check        # validate: guards, compiles, parity, boot
scripts/dev.sh run          # foreground server; Ctrl-C to stop
```

Then start the client. On Windows, `run-client.bat` builds the client and
starts the server for you; on Linux/macOS build first
(`scripts/dev.sh build-client`) and launch the jar, or use the
`Start-Linux.sh` / `Start-Windows.cmd` menus for the inherited interactive
flow.

## Ports and endpoints

| Port | Protocol | Purpose |
| --- | --- | --- |
| 43594 | TCP | Game socket (classic client) |
| 43494 | HTTP/WS | WebSocket client + operational endpoints: `/healthz` → `ok`, `/status` → JSON, `/metrics` → Prometheus, unknown → 404 |

The health endpoints are **unauthenticated** by design; treat the WS port as
internal (see [../security/threat-model.md](../security/threat-model.md)).
Ports come from `server/<world>.conf` (`ws_server_port`), not from defaults in
code.

## Databases

- **Default: SQLite** — `server/connections.conf` (`db_name: preservation`).
  The `*.db` files are gitignored; on a fresh clone
  `scripts/check_boot.sh` (and therefore `scripts/dev.sh check`) seeds
  `server/inc/sqlite/preservation.db` from the tracked schema in
  `server/database/sqlite/`.
- **Optional: MariaDB** — `docker-compose.yml` + the `MARIADB_*` keys in
  `.env`; used for shared/production-style hosting. `scripts/check_hosting_config.sh`
  validates whichever mode is configured.

## `.env` (optional)

A fresh clone works without one: the top-level `Makefile` now uses
`-include .env`, so `make` targets that do not touch MariaDB run regardless.
You only need `.env` for the legacy MariaDB backup targets, docker-compose,
or the optional Discord webhook — copy `.env.example` and fill in values
(never commit `.env`; see [../security/configuration.md](../security/configuration.md)).

## Logs and outputs

| Path | Content |
| --- | --- |
| `dist/boot_smoke.log` | Last boot-smoke run (echoed in full on failure) |
| `Client_Base/client_log*.txt` | Client logs |
| `Client_Base/Cache/` | Client cache + per-install `port.txt`/`ip.txt` state |
| `server/*.log`, `logs/` | Server logging (see `server/conf`) |
| `dist/` | All scratch output: pages staging, release bundles, test harnesses — safe to delete |

## Troubleshooting

- **`port 43494 already answers /healthz`** — another server (or `dev.sh run`)
  is still up; stop it before `check`.
- **`no JDK found`** — set `JAVA_HOME` to a JDK 8 install, or keep the
  vendored `Portable_Windows/<jdk>` directory in place.
- **Theme parity fails after a client change** — the parity script runs
  against the built jar; rebuild with `scripts/dev.sh build-client` (check
  does this for you, in order).
- **Windows path/`\r` oddities** — repository scripts are stored with LF endings
  by design so Linux runners can execute them; do not convert them to CRLF.
- **Server says the database file does not exist** — run
  `scripts/dev.sh check` once: the boot smoke test seeds
  `server/inc/sqlite/<db_name>.db` from the tracked schema before booting
  (see `scripts/check_boot.sh`).
