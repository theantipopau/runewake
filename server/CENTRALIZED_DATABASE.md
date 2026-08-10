# Sharing one account database across multiple RuneWake servers

By default each server config (`default.conf`, `rsccabbage.conf`, etc.) uses
the SQLite backend, which stores its database as a local file at
`server/inc/sqlite/<db_name>.db`. Two server processes — even on the same
machine — never see each other's SQLite file, so an account made on one
doesn't exist on the other.

To share accounts, point every server instance at the same **MySQL/MariaDB**
database instead. This is built into the server already (`db_type: mysql` in
`connections.conf`) — no code changes needed, just configuration.

## Where this fits on your Proxmox setup

Since you're already planning to run the dedicated server on your Windows
VM, the simplest setup is to run MariaDB on that **same VM** and have the
server connect to it over `localhost` — no port-forwarding or firewall
changes needed for the database itself (only the game ports
`server_port`/`ws_server_port` need to be reachable from players, same as
today).

If you later add a second server instance on a *different* machine that
should share the same accounts, see "Connecting from another machine" below
— that's the part that needs actual network exposure and hardening.

## 1. Install MariaDB on the Windows VM

Download and run the MariaDB Community Server MSI installer from the
official MariaDB site (search "MariaDB Server download" — pick the current
stable release, Windows x64 MSI). During setup:
- Set a strong root password when prompted.
- Leave the default port `3306` unless you already have something using it.
- The installer can register it as a Windows service, so it starts
  automatically with the VM (equivalent to what you already have for the
  game server, just one more background service).

## 2. Create a dedicated database + user (don't use root)

Open a command prompt and connect as root:

```
"C:\Program Files\MariaDB <version>\bin\mysql.exe" -u root -p
```

Then create a database and a **non-root** application user scoped to just
that database:

```sql
CREATE DATABASE runewake;
CREATE USER 'runewake'@'localhost' IDENTIFIED BY 'choose-a-strong-password-here';
GRANT ALL PRIVILEGES ON runewake.* TO 'runewake'@'localhost';
FLUSH PRIVILEGES;
```

(If a second server on another machine will also connect, see step 4 for
using a specific host instead of `localhost` in the `CREATE USER` line.)

## 3. Import the schema

The core table definitions live in the repo already — import them into the
database you just created:

```
"C:\Program Files\MariaDB <version>\bin\mysql.exe" -u runewake -p runewake < "server\database\mysql\core.sql"
```

If any of your servers run a retro ruleset, also import
`server\database\mysql\retro.sql` the same way. Check
`server\database\mysql\addons\` for any optional feature tables matching
`custom_features` flags you've enabled in your `.conf` (e.g. clans,
auction house, equipment tab) — only import the ones matching features you
actually turned on.

## 4. Point the server(s) at it

Edit `server/connections.conf` (this is shared by every world/config
running from this checkout):

```
db_type: mysql

mysql:
	db_host: localhost:3306
	db_user: runewake
	db_pass: choose-a-strong-password-here
	db_table_prefix:
```

Then, in **every** `.conf` file for the servers that should share accounts
(`default.conf`, `rsccabbage.conf`, whichever you're running), make sure
`db_name` is set to the **same** value:

```
database:
	db_name: runewake
```

Servers with a *different* `db_name` will still connect to the same MySQL
instance but get their own separate set of tables — useful if you
deliberately want, say, a hardcore/ironman mode to have isolated
characters, but not what you want if the goal is one shared account across
all of them.

## Connecting from another machine

If a second server instance runs somewhere else (another VM, a friend's
machine, a VPS) and needs to share this same database:

- In step 2, create the user scoped to that machine's IP instead of
  `localhost` — e.g. `CREATE USER 'runewake'@'203.0.113.5' ...` — rather
  than `'runewake'@'%'`, which would allow connections from anywhere.
- Open port `3306` on the Windows VM's firewall and, if the VM is behind
  NAT, forward it on your router — but treat this the same as any other
  exposed database port: prefer restricting it to a known set of source
  IPs, or better, put a WireGuard tunnel (free, open-source) between the
  two machines and only allow MySQL connections over the tunnel's private
  IP range instead of the raw internet.
- Set `db_host` in that other server's `connections.conf` to the VM's
  reachable IP/hostname instead of `localhost`.

## Migrating existing accounts

If any of the servers you're consolidating already have real player data in
their SQLite `.db` files, that data needs to be migrated into the new MySQL
schema before switching — it won't happen automatically. Let me know if you
have existing accounts you need carried over and I'll help write that
migration rather than guessing at what's actually in those files.
