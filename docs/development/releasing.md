# Releasing RuneWake

## Policy

- Tags are `vMAJOR.MINOR.PATCH` (`v0.1.1` is the current release).
- Every release states whether it was **built** (a real bundle exists and was
  verified) or is merely a source tag; never claim otherwise.
- AGPLv3: bundles ship `LICENSE`, sources and attribution
  (`README.md` credits) must remain intact.
- Published release notes, contents and verification steps live in
  [`../RELEASES.md`](../RELEASES.md).

## One reproducible packaging path

```bash
scripts/dev.sh release 0.1.2
```

This is the only supported way to build a bundle. It:

1. rebuilds the client, server core/plugins and launcher from source with the
   vendored JDK/Ant (no downloads);
2. stages `dist/RuneWake-<version>/` — jars, cache, server data, schema,
   portable runtime, run scripts, `LICENSE`, guides — **excluding** `.env`,
   logs, local SQLite databases, IDE metadata and developer state;
3. generates a fresh `preservation.db` from the tracked schema (never copied
   from a developer machine);
4. writes `MANIFEST.sha256` (explicit LF bytes, `sha256sum`-compatible) and
   `RELEASE.txt` (version, UTC timestamp, **source commit = `HEAD` at build
   time**, build targets);
5. produces `dist/RuneWake-<version>.zip` and prints its SHA-256.

It commits, tags, pushes and publishes nothing.

### The build-order trap

`RELEASE.txt` records `HEAD` when the build runs. Build the bundle **after**
the last release commit, from a clean tree — building from a dirty or stale
tree produces an archive naming the wrong commit (documented after the 0.1.1
rebuild, `8f7e40d1a`).

```bash
git status            # must be clean
scripts/dev.sh release 0.1.2
```

### Overwrite guard

The builder refuses to overwrite an existing `dist/RuneWake-<version>/` or
`.zip` (delete the old output to rebuild the same version), while allowing
`dist/` itself to exist — the guards and Pages build create it on every
machine. Fixed 2026-10-06; previously the documented default command failed
whenever `dist/` existed.

## Verifying a bundle

```bash
cd dist/RuneWake-<version>
sha256sum -c MANIFEST.sha256     # every file matches
grep "Source commit" RELEASE.txt # matches the intended tag
```

Then smoke-test it like a player would: `run-server.bat` +
`run-client.bat` (the bundle is a clean checkout equivalent — the boot smoke
test already proves the server side).

## Before tagging

1. `scripts/dev.sh check` — full validation green.
2. Confirm the GitHub Actions run on the release commit is green
   (5 jobs: guards, secret scan, parity, boot, build).
3. `scripts/dev.sh release <version>` from the clean tree.
4. Verify the manifest (above).
5. Tag `v<version>` at the `RELEASE.txt` commit, create the GitHub release,
   upload `RuneWake-<version>.zip` and record its SHA-256 in the release notes
   ([`../RELEASES.md`](../RELEASES.md) format).

## CI artefacts

Every green `build` job on GitHub also publishes `OpenRSC-jar` (the launcher
jar) as a downloadable artefact — useful for quick testing, but **not** a
release bundle; only the path above produces one.
