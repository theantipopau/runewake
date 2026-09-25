# Free hosting a RuneWake server

GitHub Pages hosts the RuneWake landing page and the static server-browser
listing. It does **not** run the Java game server. A public world needs a
continuously running JVM, persistent database/storage, inbound game TCP, and
a separately reachable HTTPS status endpoint for the browser.

The repository now contains the building blocks for that deployment:

- `DB_*` environment overrides in the Java server, so credentials do not have
  to live in `server/connections.conf`;
- Connector/J `DB_SSL_MODE` support (`PREFERRED`, `REQUIRED`, `VERIFY_CA`,
  or `VERIFY_IDENTITY`) instead of hard-coded `useSSL=false`;
- a loopback-only MariaDB Compose service with a named volume and health check;
- `scripts/check_hosting_config.sh`, a secret-safe preflight validator;
- `deployment/systemd/` for unattended Linux service operation; and
- `scripts/backup_mariadb.sh` for private, checksummed snapshots.

These files make deployment repeatable. They do not create cloud accounts,
open firewall rules, or operate a public server on the owner's behalf.

## Recommended first world: one VM, one database

For the first public RuneWake world, keep the game and database on the same
host and use either:

1. **SQLite** for a small/private world and the simplest recovery story; or
2. **MariaDB on the same VM** for a shared account database, multiple worlds,
   and more conventional operational tooling.

Do not expose TCP `3306` to the Internet. A same-host database should be
reachable through loopback (`127.0.0.1:3306`). Use a private network or
WireGuard if a second host needs access.

### Oracle Always Free

Oracle's current documentation says Always Free Ampere A1 capacity is up to
2 OCPUs and 12 GB of memory across the tenancy, with a home-region
requirement. Capacity is not guaranteed: Oracle documents that instances over
the allowance can be disabled and later deleted, so this is a useful low-cost
starting point rather than a promise of permanent free capacity. Check the
current terms before relying on it:

- <https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier.htm>
- <https://www.oracle.com/cloud/free/>

A small first deployment can fit within that allowance, but Java heap, packet
buffers, logs, database memory, backups, and operating-system overhead all
count. Keep `max_players` conservative and monitor the VM rather than tuning
for the advertised limit.

Suggested OCI sequence:

1. Create the account and choose the home region carefully.
2. Create an Ubuntu Ampere A1 instance within the current Always Free limit.
3. Allow inbound TCP `43594` (game) and `43494` (WebSocket/status) in the
   OCI security list and the VM firewall. Do not allow TCP `3306` publicly.
4. Install a JDK and Ant, then build `server/core.jar` and
   `server/plugins.jar` on the VM.
5. Install `deployment/systemd/runewake-server.service`, create the
   `/etc/runewake/runewake.env` file, and set a dedicated database user.
6. Initialize the schema only on a new, empty database, then start the
   service and verify the logs and `/status` endpoint.
7. Put an HTTPS reverse proxy or Cloudflare Tunnel in front of the status
   endpoint if the world will be listed in the Pages server browser.

The [systemd deployment guide](../deployment/systemd/README.md) contains the
service installation and verification commands. The unit is deliberately
provider-neutral; it does not contain cloud credentials.

## Configuration and secrets

The checked-in `server/connections.conf` remains a usable local configuration
and defaults to SQLite. For a hosted database, export or place these values
in the service environment:

```text
DB_TYPE=mysql
DB_HOST=127.0.0.1:3306
DB_NAME=runewake
DB_USER=runewake
DB_PASS=<secret>
DB_TABLE_PREFIX=
DB_SSL_MODE=VERIFY_IDENTITY
DB_CONNECT_TIMEOUT=10000
```

For a local MariaDB instance whose certificate is not published under a name
matching the connection hostname, `PREFERRED` is a compatibility starting
point. It is **not** the recommended setting for an Internet-facing managed
database. `VERIFY_IDENTITY` requires both TLS and a certificate chain that
matches the hostname; Connector/J also needs the provider's CA in the JVM
trust store when it is not a publicly trusted CA.

`DB_CONNECT_TIMEOUT=10000` bounds the initial JDBC connection attempt so a
black-holed database endpoint cannot hold startup forever. Environment
variables take precedence over YAML values. They are intentionally not loaded
from a random file by the Java process; systemd's
`EnvironmentFile`, a process supervisor, or an explicit shell export gives
the operator control over secret handling. Never put a real password in
`connections.conf`, `.env`, a command-line URL, or a committed file.

Before starting a deployment, run:

```sh
bash scripts/check_hosting_config.sh
```

The validator checks database type, dedicated-user guidance, TLS mode,
identifier shape, listener ports, and the destructive nature of the schema
scripts without printing passwords. A remote database is rejected unless it
uses `VERIFY_IDENTITY`, except when the operator deliberately documents a
private-network exception in the surrounding deployment documentation.

## Local MariaDB with Compose

Copy `.env.example` to `.env` and fill in the MariaDB values. The
Compose service is intentionally private by default:

```sh
cp .env.example .env
# edit .env; do not commit it
mkdir -p backups
docker compose up -d mariadb
docker compose ps
```

The database is published only on `127.0.0.1` by default and stores its data
in the named `mariadb_data` volume. Set the game process's `DB_NAME` to the
same value as `MARIADB_DATABASE`; the Compose service does not automatically
export server settings. A server container on the same Compose network can
use `mariadb:3306` without a host port. The health check uses the official image's
[`healthcheck.sh`](https://mariadb.com/docs/server/server-management/automated-mariadb-deployment-and-administration/docker-and-mariadb/using-healthcheck-sh).
Keep the MariaDB image on a tested 11.4
LTS release (or pin its digest in a production deployment), and do not use
`latest`.

The Compose file intentionally does **not** start the Java server or import a
schema. This prevents an accidental container restart from replaying a
destructive SQL file over player data. Initialize a new database explicitly,
then let the server's normal patch mechanism handle subsequent upgrades.

## Initializing and upgrading safely

`server/database/mysql/core.sql` and `retro.sql` are initialization scripts:
they contain `DROP TABLE IF EXISTS`. They are appropriate only for a new,
empty database after a backup. They are not migrations.

For an existing SQLite world, stop the server, copy the database, and migrate
to MariaDB with a tested, purpose-built conversion. Do not assume that
copying a SQLite file into a MySQL directory is a valid migration. For an
existing MySQL world, take a backup before deploying a new release. The
server applies the ordered files in `server/database/mysql/patches/` and
records them in its patch table; a failed patch should stop the rollout.

Use least privilege. The game account should normally have only the
privileges needed for its database and patch operations. Keep an independent
administrative account for backups, schema creation, and recovery, and store
it separately from the game service.

## Backups

For a same-host MariaDB deployment, the repository helper keeps the password
out of the process list, writes private files, and creates a SHA-256 checksum:

```sh
read -rsp 'Database backup password: ' MYSQL_PASSWORD; echo
export MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_DATABASE=runewake
export MYSQL_USER=runewake_backup MYSQL_BACKUP_DIR=backups/mysql MYSQL_PASSWORD
bash scripts/backup_mariadb.sh
# or: make backup-mariadb-safe
unset MYSQL_PASSWORD
```

The account still needs the dump privileges required by the selected dump
options. Copy the resulting `.sql.gz` and `.sha256` off the VM, encrypt them
where appropriate, and test a restore into a scratch database. For a managed
provider, use its snapshot/export feature in addition to an off-host copy.
Set retention so backups do not fill a small free disk.

SQLite worlds should use an application-consistent copy while the server is
stopped (or a tested SQLite backup mechanism), and the copy must include the
`server/inc/sqlite/` directory. A VM snapshot is not a substitute for an
off-host backup.

## Status, browser, and firewall boundaries

The game protocol uses TCP `43594` by default. If
`want_feature_websockets: true`, the status endpoint and WebSocket support use
TCP `43494` by default. The server-browser page is HTTPS and browsers block it
from fetching plain HTTP; a Cloudflare Tunnel or a real reverse proxy with a
certificate can publish `/status`. A tunnel does not carry the raw game
protocol, so TCP `43594` still needs its own reachable route.

Check from outside the host:

```sh
curl --fail http://127.0.0.1:43494/status
curl --fail http://127.0.0.1:43494/healthz
curl --fail http://127.0.0.1:43494/metrics
nc -vz YOUR_PUBLIC_HOST 43594
nc -vz YOUR_PUBLIC_HOST 43494
```

Use an external network for the public-port checks. A service listening on
loopback is not proof that the provider firewall or home router permits
player traffic.

### Uptime monitoring

Point an external uptime monitor at `/healthz` on the published `ws_server_port`
(it answers `200 ok` without parsing JSON) and alert on non-200 or timeout.
For a dashboard, scrape `/metrics` on the same port: it exposes
`runewake_players_online`, `runewake_players_max`, and
`runewake_uptime_seconds` in the Prometheus text format, read from the same
public state as `/status` and never touching the database. All three routes
accept `GET`/`HEAD` and return `404`/`405` for unknown paths or other methods.

A container-level healthcheck is separate: `docker compose` health-checks the
MariaDB service, not the game process, so still monitor the game world over
HTTP from off-host. `systemd`'s restart policy covers process crashes; the
HTTP monitor covers a process that is up but not serving.

## What is intentionally not promised

There is no guaranteed permanently free managed MySQL database in this
repository. A provider must be checked for current terms, storage limits,
backup/export support, TLS trust requirements, connection limits, private
networking, and suspension policy before it is used. If those requirements
are unclear, keep MariaDB on the game VM and treat the VM's free-tier
availability as the limiting factor.

The original OpenRSC login-server proposal
[<https://gitlab.com/openrsc/openrsc/-/issues/2943>](https://gitlab.com/openrsc/openrsc/-/issues/2943)
is a later multi-cluster architecture, not a prerequisite for deploying one
world. The next phase is operational hardening and backups first; cluster and
cross-domain login protocols should follow only after one public world has
restore-tested operations.
