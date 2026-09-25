# Linux service deployment

This directory contains a provider-neutral systemd unit for a RuneWake
server on a Linux host. It is usable on an owned VPS, a home server, or an
Oracle Always Free VM. It does not provision cloud resources and it does not
open ports for you.

## Install path

The unit assumes the repository is installed at `/opt/runewake` and that Ant
is available as `/usr/bin/ant`. Adjust both paths in the unit if your layout
differs.

```sh
sudo useradd --system --home /opt/runewake --shell /usr/sbin/nologin runewake
sudo install -d -o runewake -g runewake /opt/runewake
sudo -u runewake git clone https://github.com/theantipopau/runewake.git /opt/runewake
sudo -u runewake sh -c 'cd /opt/runewake/server && ant compile_core compile_plugins'
sudo install -d -o root -g runewake -m 0750 /etc/runewake
sudo install -o root -g runewake -m 0640 deployment/systemd/runewake.env.example /etc/runewake/runewake.env
sudoedit /etc/runewake/runewake.env
sudo install -o root -g root -m 0644 deployment/systemd/runewake-server.service /etc/systemd/system/runewake-server.service
sudo systemctl daemon-reload
sudo systemctl enable --now runewake-server
```

Replace the repository URL with the exact branch or release you intend to
operate. Keep `/etc/runewake/runewake.env` private and never add it to the
repository. The Java process reads `DB_*` variables from that file; the
tracked `server/connections.conf` remains a safe fallback for local SQLite.
The unit runs `scripts/check_hosting_config.sh` before Java; an invalid database
mode, listener, or remote TLS setting therefore fails before the server opens
a socket.

The service account needs write access to the runtime data it actually uses.
For SQLite, create the database directory and file as that account before
starting. For MariaDB, the local database volume should be owned by the
database service, not by the game user.

## Verify

```sh
sudo systemctl status runewake-server
sudo journalctl -u runewake-server -b --no-pager
sudo ss -ltnp | grep -E ':(43594|43494)\b'
curl --fail http://127.0.0.1:43494/status
```

The game listener is TCP `43594` by default. The status/WebSocket listener is
TCP `43494` when `want_feature_websockets` is enabled. Open those two ports
in the host firewall and in the provider's security list, but do not open
TCP `3306` publicly. Keep MariaDB on loopback or a private WireGuard network.

## Updates and backups

Before replacing a release, take a database backup and stop the service (or
use the database provider's snapshot facility). Build the new version, update
the checkout, and run the server's normal patch mechanism once against a copy
of the database before switching production. Never replay
`server/database/mysql/core.sql` or `retro.sql` over a live database; those
are destructive initialization scripts.

`Restart=on-failure` handles a crashed JVM or a failed Ant launch (the Ant
run targets propagate the JVM's non-zero exit status), but it is not a backup
strategy.
Schedule a tested `mysqldump --single-transaction` or provider snapshot, keep
copies off the VM, and periodically restore into a scratch database.

The unit intentionally uses a fixed world configuration (`default`). For a
second world, copy the unit, give it a distinct name and port pair, and use a
separate `WorkingDirectory` or service account as appropriate.
