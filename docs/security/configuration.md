# Security Configuration — RuneWake

Phase 1 audit companion (2026-10-06, commit `17a185b9f`). Values are never
reproduced in this document — only key names, locations and behaviour.

## Where secrets live

| Location | Tracked? | Purpose |
|---|---|---|
| `.env` (repo root) | **No** — gitignored | Local operator secrets: MariaDB credentials, backup directory, optional Discord webhook |
| `.env.example` | Yes | Contract/template: every supported key with placeholder values |
| `server/connections.conf` | Yes | Database host/port/name; `db_user`/`db_pass` are intentionally blank and documented as "may be supplied with `DB_USER`/`DB_PASS`" |
| `server/*.conf` (world configs) | Yes | Gameplay settings only — no credentials |
| `server/client.pem`, `server/server.pem` | **No** — generated at first boot, gitignored | RSA key material for the game protocol, created by `Crypto.java` on demand |
| `server/inc/sqlite/*.db` | **No** — gitignored | Local world databases (player data) |
| CI (`GITHUB_TOKEN`) | n/a | Read-only (`permissions: contents: read`); no third-party secrets, no telemetry, no paid services |

### `.env` key inventory (names only)

| Key | Consumer |
|---|---|
| `DB_TYPE`, `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASS`, `DB_TABLE_PREFIX`, `DB_SSL_MODE`, `DB_CONNECT_TIMEOUT` | Server database connection (env override over `connections.conf`) |
| `MARIADB_DATABASE`, `MARIADB_ROOT_USER`, `MARIADB_ROOT_PASSWORD`, `MARIADB_USER`, `MARIADB_PASSWORD`, `MARIADB_BIND_ADDRESS`, `MARIADB_HOST_PORT` | `docker-compose.yml` MariaDB service (fails fast with `:?set … in .env` if unset) |
| `MARIADB_PASS` | Legacy alias accepted by docker-compose for older local `.env` files |
| `MYSQL_DUMPS_DIR`, `MYSQL_*` | `scripts/backup_mariadb.sh` (restricted backup account) and legacy Makefile targets |
| `TERMINAL_WEBHOOK` | Optional Discord webhook used by one Makefile cleanup-notification target |

Rules that keep this safe:

1. **`.env` must never be committed.** It was tracked historically and was
   untracked in `123eff4d3` (2026-09-16); see
   [`secret-rotation.md`](secret-rotation.md) for the residual history risk.
2. New configuration knobs belong in `.env.example` first (placeholder value),
   then in the consumer.
3. The legacy Makefile targets pass `MARIADB_ROOT_PASSWORD` on the process
   command line (visible in `ps` on shared hosts). Prefer
   `scripts/backup_mariadb.sh`, which reads the environment and uses a
   private temporary client file. The Makefile header documents this.

## CI secrets

The GitHub workflow requests only `contents: read` and uses the default
`GITHUB_TOKEN`. No API keys, no cloud credentials, no analytics. The GitLab
pipeline likewise provisions nothing beyond `ant`, `curl`, `python3`, `unzip`
and the Gitleaks binary. Adding any new secret to CI requires: an owner, a
rotation procedure, and an entry in this document.

## Vendored runtime review (Phase 1 finding)

| Artefact | Version | Why committed | Licence | Status |
|---|---|---|---|---|
| `Portable_Windows/zulu8.50.0.51-ca-jdk8.0.275-win_x64` | Zulu JDK **8u275** (late 2020) | `run-client.bat`, `run-server.bat` and the release bundle need a zero-install Windows runtime | `LICENSE` + `THIRD_PARTY_README` present in the tree | **Outdated** — 2020 build; schedule an upgrade to a current Zulu 8 build during Phase 3/19 *without* breaking the Windows packaging flow (test the bundle before removing the old runtime) |
| `Portable_Windows/apache-ant-1.10.5` | Ant 1.10.5 (2018) | Same portable flow | Apache-2.0 (bundled `LICENSE`) | Functional; upgrade alongside the JDK |
| `Portable_Windows/HeidiSQL_11.1_64_Portable` | 11.1 | Operator convenience for MariaDB hosts | Bundled with the app | Keep or drop in Phase 3 packaging review |
| `server/inc/ant/` | scripts only, **no `lib/`** | Inherited from OpenRSC | — | **Broken/inactive** — `compile_client.cmd` references it and fails (`Could not find or load main class …ant.launch.Launcher`); candidate for removal in Phase 3 |

Do **not** delete the portable runtime until the Windows packaging flow has a
tested replacement (mission rule). Large binaries should eventually move to
release artefacts rather than git history — that decision belongs to Phase 3.

## Generated key material

- `Crypto.java` generates `client.pem`/`server.pem` on first boot if absent
  and logs a warning. They are gitignored; confirm with
  `git check-ignore server/client.pem`.
- Never copy a production `.pem` into the tree or into a release bundle.
- Local SQLite world databases contain player data: never commit, and never
  use real player data in automated tests (Phase 15 rule).

## Planned (documented, not yet implemented)

These Phase 1 items require network-dependent tooling that cannot be verified
in this environment, so they are recorded rather than claimed:

- **Dependency vulnerability scanning** (OWASP dependency-check / OSV) — needs
  an NVD/OSV mirror in CI; `scripts/check_dependencies.sh` currently verifies
  presence/drift only.
- **Licence reporting / SBOM** — `docs/DEPENDENCIES.md` is the hand-maintained
  inventory; automating it (CycloneDX SBOM job) is a Phase 3 candidate.
- **Static analysis** — no Java linter in CI yet; adding SpotBugs without a
  triage baseline would produce unactionable noise.
