# OpenRSC issue triage and RuneWake next phase

This is a focused triage of useful leads from the original public OpenRSC
project. It is based on the public issue descriptions and the repository
state; the full historical issue export and private notes were not available.
Links point to the original project so the context is not lost when the
implementation diverges.

## Hosting and reliability leads

| Original issue | What it teaches us | RuneWake action/status |
|---|---|---|
| [OpenRSC #3525 — Android checking updates should have a timeout](https://gitlab.com/openrsc/openrsc/-/issues/3525) | A blocked update host must not prevent startup. | **Improved in both clients**: Android version/APK/cache requests and desktop manifest/client/launcher downloads now have bounded connect/read timeouts; unknown content lengths no longer cause divide-by-zero progress, and partial launcher downloads report failure. |
| [OpenRSC #3599 — Servers seem to be down](https://gitlab.com/openrsc/openrsc/-/issues/3599) | “Running” is not the same as reachable or observable. | **Groundwork laid**: `/status`, systemd restart policy, a database health check, and a configuration preflight exist. Next is external uptime/alerting and a repeatable restore drill. |
| [OpenRSC #3385 — database truncation involving `prayer`](https://gitlab.com/openrsc/openrsc/-/issues/3385) | Gameplay values and schema types can drift; failed saves need safe diagnosis. | **Follow-up**: audit player skill column ranges against current code, add a schema/type compatibility check, and test an upgrade with a deliberately extreme value. Do not paper over a truncation with a blind data rewrite. |
| [OpenRSC #3518 — websocket certificate reload command](https://gitlab.com/openrsc/openrsc/-/issues/3518) | Certificate rotation should not require a full process restart. | **Already present in this fork**: `Server.refreshWebsocketSSLContext(...)` and its moderator command were found during triage. No duplicate command was added. Keep a rotation test in the operations checklist. |

## Architecture lead

[OpenRSC #2943 — Login Server](https://gitlab.com/openrsc/openrsc/-/issues/2943)
proposes Domain, Cluster, World, LoginServer, and MasterLoginServer concepts,
with worlds accessing account data through a login service and reconnecting
when that service is unavailable.

That is a substantial protocol, schema, launcher, and failure-mode project.
It is **not** a prerequisite for one public RuneWake world and should not be
copied wholesale into the first hosting phase. The current phase keeps one
world/database relationship explicit and testable. A later phase can extract
a login service only after account/session invariants, reconnect behavior,
and migration boundaries are specified.

## Lower-priority gameplay leads

- [OpenRSC #3513 — online-player count in `::info`](https://gitlab.com/openrsc/openrsc/-/issues/3513)
  is a useful small observability improvement once the public server has a
  stable baseline.
- [OpenRSC #3618 — permanent Cabbage bank fix](https://gitlab.com/openrsc/openrsc/-/issues/3618)
  should be reproduced against the current RuneWake ruleset before changing
  behavior.

These are separate from hosting readiness and should not delay database
safety or backup work.

## Phased plan

### Phase 0 — repository safety (complete in this pass)

- Keep production credentials out of tracked configuration.
- Make SQLite the zero-setup default.
- Validate database type, credentials shape, TLS mode, and listener ports.
- Distinguish initialization SQL from online migrations.
- Preserve a clean, repeatable build and Pages/dependency guards.

### Phase 1 — one recoverable public world (next)

- Provision one VM or owned host; prefer a same-host private MariaDB.
- Install the systemd unit and a protected environment file.
- Initialize one empty database once, then rely on ordered patches.
- Expose only game/status TCP ports; put HTTPS in front of `/status`.
- Run the backup helper and complete a restore into a scratch database.
- Add external uptime checks and a documented rollback procedure.

**Exit criteria:** a clean machine can be restored from the repository plus
one backup, the service restarts after a JVM failure, the game port is
reachable externally, and the status endpoint is HTTPS-backed.

### Phase 2 — measured operations

- Add player-count/queue metrics to `/status` without exposing secrets.
- Audit the `prayer`/skill schema range issue from #3385.
- Add a release migration checklist and test each patch against a copy of
  production-shaped data.
- Review #3513 and reproduce #3618 only after the baseline is observable.

### Phase 3 — optional multi-world architecture

- Define the minimum login/session protocol and threat model.
- Separate account data from world/player data deliberately.
- Specify reconnect, duplicate-login, graceful-drain, and migration behavior.
- Prototype against a test cluster before changing the public launcher.

## Verification rule

A hosting change is not complete merely because the server starts. Completion
requires a build, a clean configuration check, a database backup, a restore
test, an externally reachable game port, and a documented result. This keeps
the fork honest while provider accounts and infrastructure are still
unavailable.
