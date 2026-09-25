# Sharing one account database across multiple RuneWake servers

By default each world uses SQLite and stores its data at
`server/inc/sqlite/<db_name>.db`. Two processes using different SQLite files
do not share accounts, even on the same machine. To share accounts, run a
MySQL/MariaDB database and point every participating world at the same
database name and table prefix.

This guide is for one shared account database. It does not implement the
later OpenRSC multi-cluster/login-server design.

## Recommended topology

For the first hosted world, run the database on the same VM as the game and
connect through `127.0.0.1:3306`. Only the game and status TCP ports
(`43594` and `43494` by default) need public reachability. Do not publish
TCP `3306` to the Internet.

For multiple worlds on the same VM, use one database and choose either:

- one `DB_NAME` and an empty `DB_TABLE_PREFIX` when all worlds should share
  the same account tables; or
- separate database names/prefixes when worlds must remain isolated.

A remote world should use a private network such as WireGuard or a provider's
private database endpoint. Do not create a MySQL user for `%` merely to make
a remote connection convenient.

## Create the database and application user

Use the MariaDB/MySQL administration account only for setup. Create a
separate game account scoped to the intended database:

```sql
CREATE DATABASE runewake CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'runewake'@'localhost' IDENTIFIED BY 'use-a-generated-secret';
GRANT ALL PRIVILEGES ON runewake.* TO 'runewake'@'localhost';
FLUSH PRIVILEGES;
```

`ALL PRIVILEGES` is limited to this one database and is useful for the
initial schema/patch setup; narrow it to the permissions the chosen build
actually needs after testing. Keep a separate administrative/backup account
for recovery and schema changes. Never put either password in the repository.

## Configure the server without checked-in secrets

The server reads `DB_*` environment variables before the corresponding
`connections.conf` values. For a systemd deployment, put the values in the
protected environment file described in
[`deployment/systemd/README.md`](../deployment/systemd/README.md):

```text
DB_TYPE=mysql
DB_HOST=127.0.0.1:3306
DB_NAME=runewake
DB_USER=runewake
DB_PASS=<secret>
DB_TABLE_PREFIX=
DB_SSL_MODE=PREFERRED
DB_CONNECT_TIMEOUT=10000
```

For a remote database, use `VERIFY_IDENTITY` and ensure the provider's CA is
trusted by the JVM. Connector/J supports the following modes (see the official
[SSL connection guide](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-using-ssl.html)):

- `PREFERRED`: use TLS when available, but fall back to plaintext;
- `REQUIRED`: require encryption without certificate identity verification;
- `VERIFY_CA`: require encryption and validate the certificate chain;
- `VERIFY_IDENTITY`: also require the certificate hostname to match;
- `DISABLED`: explicit plaintext, for an isolated development network only.

A local MariaDB with no matching certificate can start with `PREFERRED` while
its TLS setup is being completed. Do not use that compatibility mode for an
Internet-facing managed database. Run
`bash scripts/check_hosting_config.sh` before starting the service; it checks
these settings without printing the password.

The YAML fallback remains available for local development. It should contain
placeholders, not production credentials:

```yaml
db_type: mysql
mysql:
    db_host: 127.0.0.1:3306
    db_user: runewake
    db_pass:
    db_table_prefix:
    db_ssl_mode: PREFERRED
```

## Initialize a new database safely

The files in `server/database/mysql/` are split into initialization schema,
optional add-ons, ordered patches, and legacy upgrade scripts. The
`core.sql` and `retro.sql` initialization files contain destructive
`DROP TABLE IF EXISTS` statements. They are safe only for a brand-new empty
database. Never replay them over a live or backed-up production database.

For a new MariaDB database:

1. create the database and dedicated user;
2. verify the target is empty;
3. import `server/database/mysql/core.sql` once;
4. import only the optional add-on tables required by the chosen config;
5. import `retro.sql` only for a new retro schema, never as a migration;
6. start the server once so its ordered patch mechanism can record and apply
   future patches;
7. take a backup and test a restore.

The server's patch applier reads `server/database/mysql/patches/` and records
successful patches. A failed patch is a deployment failure, not a reason to
run the destructive initialization file again.

## Existing SQLite data

There is no automatic SQLite-to-MariaDB conversion. If real player accounts
already exist, stop the server, make a copy of the SQLite file, inspect the
source schema and row counts, and write/test a migration for that exact data
set. Validate account/login credentials, characters, inventories, banks,
friends, bans, and patch history before switching the public world over.

A simple file copy into `server/inc/databases` is not a migration and may
silently lose or corrupt data.

## Backups and recovery

For a local MariaDB instance, use the repository helper with a dedicated
backup account:

```sh
read -rsp 'Database backup password: ' MYSQL_PASSWORD; echo
export MYSQL_DATABASE=runewake MYSQL_USER=runewake_backup MYSQL_PASSWORD
export MYSQL_BACKUP_DIR=/var/backups/runewake/mysql
bash scripts/backup_mariadb.sh
unset MYSQL_PASSWORD
```

The helper uses a private temporary client option file, a consistent
transaction, restrictive permissions, and a SHA-256 checksum. Copy the
resulting archive off the host and periodically restore it into a scratch
database. For a managed provider, use its snapshot/export feature as well;
check retention, egress, and deletion policy.

For SQLite, stop the server or use a tested SQLite backup method, then copy
`server/inc/sqlite/<db_name>.db` to off-host storage. A VM snapshot alone is
not a tested restore.

## Remote connections

Prefer, in order:

1. database on the same VM over loopback;
2. a provider private endpoint or WireGuard/Tailscale-style private network;
3. a managed database with verified TLS and a restricted source network.

If raw TCP `3306` must be opened temporarily, restrict the cloud/firewall
rule to known source addresses, use a dedicated account, require TLS, and
close the rule after migration/testing. It should not be the permanent
public topology.
