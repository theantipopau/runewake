# Contributing to RuneWake

RuneWake is a volunteer, non-commercial continuation of
[OpenRSC](https://gitlab.com/openrsc/openrsc) and is licensed under the
[GNU Affero General Public License v3](LICENSE). Contributions are accepted on
[GitHub](https://github.com/theantipopau/runewake) and are assumed to be
licensed under the same terms. The RuneScape Classic game content is
third-party material; see [README.md](README.md) for provenance and credits.

This file describes the workflow this fork actually uses. The inherited
OpenRSC developer notes live in the upstream repository and are not a
description of this project.

## Before you start

- **Search the [issues](https://github.com/theantipopau/runewake/issues)** for
  an existing report or proposal. Duplicate effort helps nobody.
- **Open an issue before** changing gameplay balance, the network protocol, the
  database schema, or committed assets. Those changes need a decision before
  code, because they affect every client build and every existing world.
- **Security problems do not belong in a public issue.** Follow
  [SECURITY.md](SECURITY.md): private reports for anything that could take over
  an account or duplicate items.

## Set up a working tree

```sh
git clone https://github.com/theantipopau/runewake.git
cd runewake
scripts/dev.sh check
```

No IDE, Docker, or network access is required. `scripts/dev.sh` uses
`$JAVA_HOME`/`$ANT_HOME` when set and otherwise the vendored JDK 8 and Ant in
`Portable_Windows/`. Details:
[local-environment.md](docs/development/local-environment.md) and
[building.md](docs/development/building.md).

## Make the change

1. Branch from `develop`:

   ```sh
   git checkout -b fix/bank-pin-resize develop
   ```

2. Keep one logical change per commit, with a message that says **why**.
   Reverting one behaviour should not require unpicking an unrelated reformat.
3. Follow the surrounding code: tabs, the existing brace and naming style, and
   no drive-by reformatting of untouched lines. A diff should be reviewable.
4. Behaviour that players can notice goes behind a `want_*` world flag with a
   default that preserves current behaviour, and the balance value belongs to
   the Runewake preset rather than the authentic one.
5. Database changes are a new ordered file in
   [`server/database/mysql/patches/`](server/database/mysql/patches). Never edit
   `core.sql`/`retro.sql` for a live world — they are destructive initialization
   scripts for an empty database.
6. The protocol is sacred: no opcode, packet layout, `CLIENT_VERSION`, or cache
   change without an explicit decision and a matching bump for the PC client,
   the Android client, and the server together.
7. Update the documentation the change invalidates. A flag nobody can find is
   not finished.

## Verify before opening a pull request

```sh
scripts/dev.sh check
```

That is the same sequence CI runs: shell and literal guards, dependency and
hosting-configuration checks, all three compiles, the theme-parity assertions,
the Pages build and the world-boot smoke test. A green local `check` means a
green pipeline; `.github/workflows/ci.yml` is the only pipeline.

When a check fails, fix the cause. Do not delete an assertion, weaken a test,
or silence a warning to get to green — if a check is genuinely wrong, say so in
the pull request and explain the evidence.

Individual checks, when you need one of them:

```sh
bash scripts/check_theme_parity.sh     # needs Client_Base/Open_RSC_Client.jar
bash scripts/check_boot.sh             # needs server/core.jar + plugins.jar
bash scripts/check_characterization_tests.sh
bash scripts/check_hosting_config.sh
```

## Pull requests

- Target `develop` — it is this fork's default branch and the branch releases
  are cut from. Describe the player-visible effect, the verification you ran,
  and anything you could not verify.
- Keep the branch small enough to review; split unrelated work.
- Do not commit `dist/`, built jars, `server/inc/sqlite/*.db`, `.env`, or log
  files — they are gitignored for a reason. If you need a secret, it does not
  belong in the repository at all.
- CI must pass and a maintainer must approve before merge.

## Assets and dependencies

- Record new artwork, fonts, sounds, or model files in
  [docs/RUNEWAKE_ASSET_INVENTORY.md](docs/RUNEWAKE_ASSET_INVENTORY.md) with its
  origin and licence. Unattributed art cannot be shipped.
- New Java libraries are vendored in `server/lib/` or `PC_Client/lib/` and
  listed in [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md);
  `scripts/check_dependencies.sh` fails if a referenced jar is missing. Prefer
  not adding one at all.

## Upstream

Fixes to the shared engine may also be useful to OpenRSC itself — send them
upstream to <https://gitlab.com/openrsc/openrsc> where the change is not
fork-specific. The upstream attribution in [README.md](README.md) and
[LICENSE](LICENSE) is preserved by design.
