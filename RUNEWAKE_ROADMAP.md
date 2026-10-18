# RUNEWAKE_ROADMAP.md

Modernisation-pass tracker (master prompt, 2026-10-18).
`UI_SCALING_PLAN.md` stays the source of truth for UI scaling; everything
else lives here. Honest states only.

## Gates

- [x] **GATE 0 — Audit & Baseline** (2026-10-18): `docs/AUDIT.md` committed;
  no source changed. Blocked on Matt: baseline screenshots, client FPS,
  loaded server tick (no display / no bot harness here).
- [ ] **GATE 1 — Finish UI Scaling:** 4b interiors + `NComponent`/legacy
  windows (BankPin first), one file per commit, screenshots per step.
  Needs a display — cannot proceed headless beyond code prep.
- [ ] **GATE 2 — Crisp Text & UI Art:** only if Matt confirms Option A
  "reads too blocky."
- [ ] **GATE 3 — 3D Presentation:** SSAA, fog/draw distance, camera,
  lighting/sky, upscale filter, roof hiding / item labels. Budget: ≥80%
  of Gate 0 FPS @1080p with SSAA 1.5× (baseline FPS still unmeasured).
- [ ] **GATE 4 — Gameplay & QoL:** XP drops (check `C_EXPERIENCE_DROPS`),
  keyboard dialogue, bank tabs/search, shift-drop, orbs, world map,
  collection log, tasks, events, combat clarity, auctionhouse+. One
  commit per item, `want_*` server flag, tests where logic lives.
  Balances only in the Runewake preset.
- [ ] **GATE 5 — Backend (public-world urgency, decided 2026-10-18):**
  5a secrets/startup check · 5b server to modern LTS (client stays 8) ·
  5c Docker/healthchecks/backups/Flyway-or-equivalent · 5d WS+Caddy (note:
  WS transport + HTTP routes already exist — verify, don't rebuild) ·
  5e metrics (note: `/metrics` today has players/uptime only) + rate
  limits + password-KDF review + `docs/HOSTING.md` — all urgent for a
  public world.
- [ ] **GATE 6 — Branding/Launcher/CI/Release:** README done in an
  earlier pass; left: launcher refresh, Android decision, Backups/
  Portable_Windows placement, SECURITY contacts, Dependabot,
  CONTRIBUTING rewrite (still upstream text). Decided 2026-10-18:
  GitHub-only — delete `.gitlab-ci.yml` in this gate (R5 then closes).

## Matt's decisions (2026-10-18, GATE 0 exit)

- **Java:** client stays on 8 for old machines; server moves to a modern
  LTS first (Gate 5b).
- **Visibility: public world.** Security/IP/moderation bar is higher:
  rate limits, password-KDF review, moderation tooling and the hosting
  guide (Gate 5e) are urgent, not nice-to-have.
- **Secrets:** going-forward only, no history rewrite. Rotation of the
  historically exposed values is still Matt's action (S3).
- **CI:** GitHub-only (see Gate 6).

## Parking Lot

- (GATE 0) Gradle/AGP age, MariaDB 11.4 vs 12.3, netty/JDA/guice staleness
  — see `docs/AUDIT.md` §4/§9.
- (GATE 0) `server/build.gradle` mirror, Makefile argv passwords.
- Clients share `Client_Base/` — every client change must compile PC +
  Android or be platform-guarded.
- Protocol sacred: no opcode/layout/`CLIENT_VERSION`/cache change without
  an explicit gate + version bump on PC, Android and server together.

## Definition of Done (from master prompt §7)

Secrets rotated · UI sharp 512x346→4K with UI-size setting, no hit-box
drift · optional HD fonts/sprites (authentic default) · SSAA/fog/camera/
sky within FPS budget · QoL behind flags with tests · server on LTS Java,
Docker, backups, migrations, WS/HTTPS, health+metrics · rebranded README,
modern launcher, CI, first Release · this file + `UI_SCALING_PLAN.md` current.
