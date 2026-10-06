# Secret Rotation and History Cleaning — RuneWake

Phase 1 audit companion (2026-10-06, commit `17a185b9f`).
**No secret values are reproduced in this document.**

## Why this document exists

`.env` was tracked in git until `123eff4d3` ("security: actually stop
tracking .env", 2026-09-16) and therefore exists in **at least 7 earlier
commits** of the shared history. Deleting it from the tip does not remove it
from the history — anyone with repo access can read older revisions.

Key names present in the historical `.env` (values deliberately not read into
any audit output):

```
MARIADB_ROOT_USER, MARIADB_ROOT_PASSWORD, MARIADB_USER, MARIADB_PASS, MYSQL_DUMPS_DIR
```

Note: Gitleaks does **not** flag the historical `.env` contents (the values
did not meet any secret rule's shape — most likely low-entropy or placeholder
values), which is exactly why this manual record matters.

### Rotation checklist (do this regardless of any history decision)

- [ ] On any MariaDB host where these values were ever real credentials,
      change `MARIADB_ROOT_PASSWORD` and the application user's password.
- [ ] Replace the local `.env` with fresh values; keep `.env.example`
      placeholders unchanged.
- [ ] Confirm no copy of the old `.env` sits in backups, shell history
      exports, or ticket text.
- [ ] `TERMINAL_WEBHOOK` (current local secret): rotate in the Discord
      channel settings if it was ever exposed anywhere; it has **never** been
      committed (it postdates untracking).

## Full-history findings (Gitleaks, reviewed 2026-10-06)

A full-history scan reports 18 findings recorded in
`.gitleaks.baseline.json` (all values redacted). Classification:

| Finding (file / rule) | Origin | Action |
|---|---|---|
| `Client/obfuscation/ca.key`, `client/obfuscation/ca.key` (private-key) | Inherited OpenRSC obfuscation CA — public in upstream since birth | Never reuse for anything RuneWake; left in history |
| `Client_Base/Cache/rscplus/config.ini` (twitch-api-token) | Inherited rscplus cache config, history only | None for RuneWake; upstream token should be treated as burned |
| `database/openrsc.sql`, `openrsc_game.sql`, `Databases/openrsc_forum.sql` (generic-api-key ×9) | Inherited forum dumps containing reCAPTCHA key placeholders/keys | Upstream's; reported upstream if still valid |
| `Portable_Windows/.../openjsse.security`, `java.security`, old JRE copy (generic-api-key ×3) | Stock OpenJDK policy files (documentation examples) | Allow-listed in `.gitleaks.toml` (current copies); historical copies sit in the baseline |
| `server/src/.../Crypto.java` (private-key) | Code that **strips** PEM headers at runtime | `// gitleaks:allow` on the literal, with a comment |
| `client/Cache/MD5CHECKSUM`, `RSCL_Launcher/Cache/MD5CHECKSUM.old` (generic-api-key ×3) | Checksum text colliding with a key-shaped rule | Historical only; ignored |
| `.idea/php-docker-settings.xml` (generic-api-key) | Inherited IDE metadata | IDE metadata is no longer tracked (except the Android `.idea/`, Phase 1 cleanup item) |

Findings in the **current tree**: zero (verified by scanning a snapshot of
the whole `HEAD` tree: `gitleaks dir <snapshot> -c .gitleaks.toml` → no
leaks).

## History cleaning: options and decision

**Option A — rotate and keep history (chosen).**
Rotate the affected credentials, keep the history intact, keep the reviewed
baseline so CI stays green while still catching *new* leaks.

- Pros: no force-push, no rewritten SHAs, tags/releases stay valid, open PRs
  and clones keep working, the fork stays merge-compatible with upstream.
- Cons: the old values remain readable forever. Acceptable because the
  exposed values are old local-database credentials on a non-commercial
  project — and rotation makes them inert.

**Option B — rewrite history (`git filter-repo` / BFG).**
Removes the blobs entirely.

- Cons: every commit SHA from the rewrite point changes (all previously
  published SHAs — including release tags — must be re-published), every
  clone must be re-cloned, the GitHub force-push requires coordination, and
  upstream merge history is disturbed. It is warranted only if a *live*
  high-value secret (production key, token) were ever committed.

**Recommendation:** rotate first (checklist above). Re-evaluate Option B only
if rotation is impossible or a higher-value secret is discovered.

## Regenerating the baseline (only for reviewed historical findings)

```bash
# 1. Full-history scan with the project config:
gitleaks git . -c .gitleaks.toml --redact --report-format json --report-path /tmp/hist.json

# 2. Review every entry (file, rule, commit). NEVER add a live secret.
# 3. Replace the baseline with the reviewed redacted report:
cp /tmp/hist.json .gitleaks.baseline.json

# 4. Prove CI stays green:
gitleaks git . -c .gitleaks.toml -b .gitleaks.baseline.json --redact
echo $?   # must be 0
```

## Local verification commands (all run clean today)

```bash
# current tree (full snapshot):
gitleaks dir <snapshot-dir> -c .gitleaks.toml          # 0 findings
# full history minus baseline:
gitleaks git . -c .gitleaks.toml -b .gitleaks.baseline.json --redact   # exit 0
```
