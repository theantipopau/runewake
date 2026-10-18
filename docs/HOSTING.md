# Hosting a public RuneWake world (operator runbook)

Day-2 operations for a public world: start/stop, upgrade, backup/restore,
monitoring, and incident response. Initial setup (VM, MariaDB, systemd
install) lives in [FREE_HOSTING.md](FREE_HOSTING.md) and
[deployment/systemd/README.md](../deployment/systemd/README.md); this file
assumes those steps are done and the `runewake-server` service exists.

## Routine operation

```sh
sudo systemctl start runewake-server
sudo systemctl stop runewake-server
sudo systemctl status runewake-server
sudo journalctl -u runewake-server -b --no-pager
```

Expect the log line `Game world is now online on TCP port 43594` and the
startup banner (`started in …`). The world listens on TCP `43594` (game)
and, with `want_feature_websockets`, TCP `43494` (WebSocket + HTTP).
Confirm from the host before opening any firewall:

```sh
curl --fail http://127.0.0.1:43494/healthz
curl --fail http://127.0.0.1:43494/status
curl --fail http://127.0.0.1:43494/metrics
```

Never expose TCP `3306`. MariaDB stays on loopback or a private network.

## Upgrading to a new release

1. Take a database backup (below) and stop the service.
2. Update the checkout to the exact release tag, rebuild
   (`ant compile_core compile_plugins` in `server/`).
3. Run `bash scripts/check_hosting_config.sh` — it fails fast on an
   invalid database mode, listener, or remote TLS setting.
4. Start the service and let the server apply its ordered
   `server/database/mysql/patches/` once; watch the log for patch errors.
5. Verify `/status` and `/metrics` before announcing the world open.

Never replay `server/database/mysql/core.sql` or `retro.sql` over a live
database — they contain `DROP TABLE` and are for new, empty databases only.
A blank `DB_PASS` refuses to start by design; set it in
`/etc/runewake/runewake.env` (never in a committed file).

## Backups and restore drills

```sh
read -rsp 'Database backup password: ' MYSQL_PASSWORD; echo
export MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_DATABASE=runewake
export MYSQL_USER=runewake_backup MYSQL_BACKUP_DIR=backups/mysql MYSQL_PASSWORD
bash scripts/backup_mariadb.sh
unset MYSQL_PASSWORD
```

Copy the `.sql.gz` + `.sha256` off the host. Quarterly at minimum, restore
one backup into a scratch database and boot the world against the copy —
an untested backup is not a backup. SQLite worlds: stop the server first
and copy the whole `server/inc/sqlite/` directory.

## Monitoring

- **Liveness:** external uptime monitor on `/healthz` (answers `200 ok`).
  Alert on non-200 or timeout.
- **HTTPS:** terminate TLS at the edge with the example in
  `deployment/caddy/Caddyfile.example` (status routes only) or a
  Cloudflare Tunnel; the game TCP port is never HTTP-proxied.
- **Dashboard:** scrape `/metrics` (same port) — `runewake_players_online`,
  `runewake_players_max`, `runewake_uptime_seconds`,
  `runewake_tick_last_duration_ms`, `runewake_tick_current`,
  `runewake_jvm_heap_used_bytes`, `runewake_jvm_heap_max_bytes`.
  Alert when tick duration approaches the 640 ms tick budget.
- **Process:** `systemd` `Restart=on-failure` covers crashes, not a world
  that is up but not serving — that is what the HTTP monitor is for.

## Public-world security checklist

- Unique, generated `DB_PASS` and `MARIADB_*` secrets; game account has
  only its own database privileges; backups use a separate account.
- `DB_SSL_MODE=VERIFY_IDENTITY` for any database off the local host.
- Login rate limiting and password-attempt throttling are on (see
  [network-protocol.md](architecture/network-protocol.md)); legacy
  password hashes are upgraded on login.
- Report vulnerabilities per [SECURITY.md](../SECURITY.md); never post
  exploit details publicly.
- Game rule: `::copypassword`-class staff commands and `PcapLogger`
  output are sensitive — restrict who can run and read them.

## Incident response (first 15 minutes)

1. `systemctl status` + last 200 journal lines: crash or still serving?
2. `/healthz` from the host: process alive? `/metrics`: tick stalled?
3. If the world must come down, `systemctl stop`, take a backup *before*
   touching data, then diagnose from the copy.
4. Suspected duplication/exploit: preserve logs and the database as-is,
   restrict staff commands, and follow the private-report path in
   [SECURITY.md](../SECURITY.md).
