# Security Threat Model — RuneWake

Phase 1 audit companion (2026-10-06, commit `17a185b9f`). A pragmatic model
for a non-commercial, self-hostable retro MMO. Nothing here implies a
guarantee; it records what we protect, from whom, and what is consciously
accepted.

## Assets

| Asset | Why it matters |
|---|---|
| Player accounts (password hashes in the world DB) | Account takeover, impersonation |
| World database (items, banks, quests, economy) | Duplication, economy destruction, rollback needs |
| Game server integrity (server-side authority) | Fairness of combat, trading, skills |
| Release artefacts + GitHub Actions | Supply chain: a poisoned release affects every player |
| Launcher update channel (`PC_Launcher`) | Same supply chain, delivered to end users |
| Operator `.env` / backup credentials | Host and database compromise |
| Discord webhooks | Channel spam / phishing relay |
| Source repository (AGPL obligations, attribution) | Legal and project continuity |

## Trust boundaries

1. **Internet → game ports** (TCP 43594, WS `ws_server_port` 43494): every
   client packet is untrusted input. Authoritative gameplay stays on the
   server (mission rules 9/10); packet validation hardening is Phase 13.
2. **Client binary → server**: the client is fully attacker-controlled; its
   claims about movement, inventory, combat and economy must never be
   trusted. RSA in `Crypto.java` protects transport confidentiality, not
   client honesty.
3. **GitHub Actions → repository**: mitigations are `permissions: contents:
   read`, concurrency cancellation, no self-modifying workflows, and the
   Gitleaks gate on every push.
4. **Operator machine → `.env`/databases**: local trust; documented in
   [`configuration.md`](configuration.md).
5. **Release download → player machine**: bundles ship with a
   `MANIFEST.sha256`; the launcher audit (integrity verification, safe
   extraction, rollback) is Phase 19.

## Actors

| Actor | Capability | Primary mitigation |
|---|---|---|
| Anonymous player | Send arbitrary packets, flood, probe endpoints | Phase 13 validation/rate limits per action type; health endpoints expose only operational stats |
| Modified client | Cheat clients, autoers | Server authority; server-side success rolls, movement and economy checks |
| Malicious contributor / drive-by PR | Commit secrets, poison CI | Secret scan, read-only CI token, guard jobs, review of `scripts/*.sh` changes |
| Supply-chain attacker | Compromise a dependency or the portable runtime | Vendored dependencies pinned in-tree (`scripts/check_dependencies.sh`), JDK/Ant versions frozen, no runtime downloads during build |
| Local attacker on a shared host | Read `ps` for Makefile passwords, read `.env` | Prefer `scripts/backup_mariadb.sh`; file permissions on `.env`; documented residual risk |

## Accepted risks (conscious, documented)

- **Health/status/metrics endpoints are unauthenticated** on the configured WS
  port. They expose server name, player counts and Prometheus gauges only.
  Operators must treat that port as internal (firewall / reverse proxy).
- **Historical secrets in git history** (`.env`, inherited OpenRSC artefacts):
  catalogued and rotation-guided in
  [`secret-rotation.md`](secret-rotation.md); full history rewrite declined
  for now (see that document for the trade-off).
- **2020-era portable JDK** in the Windows flow until Phase 3/19 upgrades it.
- **Legacy Makefile targets** pass a root password on argv; superseded but
  retained for compatibility, documented with a safer alternative.
- **No fuzzing/penetration testing** has been performed; Phase 13 adds
  malformed-packet tests and targeted fuzzing.

## Out of scope (for now)

- Denial-of-service against a deliberately saturated host.
- Physical security of operator machines.
- Player-side malware pretending to be RuneWake (branding/licence review is
  tracked separately in `docs/RUNEWAKE_BRANDING_AUDIT.md`).
- Telemetry/analytics — explicitly excluded by mission rules.
