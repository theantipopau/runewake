# Security Policy — RuneWake

> Inherited from OpenRSC and rewritten for RuneWake (2026-10-06). The AGPLv3
> licence and OpenRSC attribution are preserved in [LICENSE](LICENSE) and
> [README.md](README.md).

## Supported versions

RuneWake is a volunteer, non-commercial project. Security fixes are applied to
the current `develop` branch and the latest tagged release only.

| Version | Supported |
| ------- | --------- |
| `develop` / latest release | :white_check_mark: |
| older releases | :x: (rebuild from source) |

## Reporting a vulnerability

- Open a GitHub issue on <https://github.com/theantipopau/runewake/issues>
  for low-risk defects (please do not include exploit details publicly if the
  issue affects other players' accounts or the economy).
- For anything with a high risk of abuse (account takeover, item duplication,
  economy exploits), contact the maintainer privately via GitHub rather than
  in-game or in public chat, and note that RuneWake is non-commercial: there
  is no bug-bounty programme.
- Security issues inherited from upstream OpenRSC should also be reported to
  <https://gitlab.com/openrsc/openrsc/-/issues>.

## Automated secret scanning

Every push and pull request runs a **Gitleaks** job (GitHub Actions; a
matching `secretScan` job exists in `.gitlab-ci.yml`):

```bash
gitleaks git . -c .gitleaks.toml -b .gitleaks.baseline.json --redact --no-banner
```

- [`.gitleaks.toml`](.gitleaks.toml) extends the default rules with a narrow,
  justified allowlist of verified false positives (stock JDK policy files).
- [`.gitleaks.baseline.json`](.gitleaks.baseline.json) records the **reviewed
  historical findings only** (all secret values redacted). New findings —
  anywhere in the tree or in newly pushed history — still fail the build.
- Never add a live secret to the baseline. If a scan fails, remove the secret,
  rotate it, and only then consider whether a historical entry belongs in the
  baseline.

Local full-history scan (takes ~20 s):

```bash
gitleaks git . -c .gitleaks.toml -b .gitleaks.baseline.json --redact
```

## Known historical findings

The repository history predates the current policy and contains reviewed
findings (an `.env` file untracked in `123eff4d3`, inherited OpenRSC artefacts
such as a reCAPTCHA key set in old SQL dumps and an obfuscation CA key).
They are catalogued — with rotation guidance — in
[`docs/security/secret-rotation.md`](docs/security/secret-rotation.md).
No secret **values** are reproduced in this repository's documentation.

## Related documents

- [`docs/security/configuration.md`](docs/security/configuration.md) — where
  secrets live, precedence rules, vendored-runtime review.
- [`docs/security/threat-model.md`](docs/security/threat-model.md) — assets,
  trust boundaries, actors, accepted risks.
- [`docs/security/secret-rotation.md`](docs/security/secret-rotation.md) —
  rotation playbooks and history-cleaning options.
- [`docs/DEPENDENCIES.md`](docs/DEPENDENCIES.md) — dependency inventory and
  licences (software-bill-of-materials style report; automated vulnerability
  scanning is planned, see the configuration document).
