# RuneWake releases

RuneWake uses GitHub Releases for downloadable player/operator bundles. The
source repository remains the canonical project history; a release archive is
a separately built distribution artifact.

## 0.1.0

Release tag: [`v0.1.0`](https://github.com/theantipopau/runewake/releases/tag/v0.1.0)

The first packaged RuneWake release is a Windows-friendly player/operator
bundle. It contains:

- the built desktop client and launcher jars;
- the distributable client cache, with per-install state removed;
- the portable JDK 8 and Apache Ant runtime used by the Windows batch files;
- the built server core and plugin jars;
- the server library jars, server configuration, maps, definitions, and
  database schema;
- a newly generated empty `preservation.db` for the default server world;
- the `run-client.bat` and `run-server.bat` convenience entry points; and
- the server-browser documentation and static browser source.

The archive is not a Git checkout and does not contain `.env`, logs, local
player data, Discord webhook values, IDE metadata, or the developer's local
SQLite databases. A private server's own player database should be backed up
and treated as data, not copied from a public release.

### Verify a download

Each archive contains `MANIFEST.sha256`, which lists SHA-256 hashes for every
other file in the bundle. The release page also records the archive's own
SHA-256 hash. On a machine with `sha256sum`:

```sh
sha256sum RuneWake-0.1.0.zip
unzip -t RuneWake-0.1.0.zip
```

Then compare the archive hash with the release page before extracting it.
After extraction, the manifest can be checked from the bundle directory:

```sh
cd RuneWake-0.1.0
sha256sum -c MANIFEST.sha256
```

### Rebuild the bundle

From a clean `develop` checkout with the portable Windows JDK/Ant available:

```sh
python scripts/build_release.py --version 0.1.0
```

The command rebuilds the client, server core/plugins, and launcher before
staging the distribution. It writes `dist/RuneWake-0.1.0/` and
`dist/RuneWake-0.1.0.zip`. It does not commit, tag, push, or publish.

## Release policy

- Release tags use `vMAJOR.MINOR.PATCH`.
- Release notes must state what was built and what remains unverified.
- Native client appearance is not marked verified without a human visual
  pass; see [`docs/RUNEWAKE_VISUAL_TEST_MATRIX.md`](RUNEWAKE_VISUAL_TEST_MATRIX.md).
- The release archive and the source repository are both AGPLv3-licensed;
  preserve the license and historical attribution files when redistributing.
