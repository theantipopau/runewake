# RuneWake dependency guide

RuneWake vendors its Java libraries in the repository rather than resolving
them with Maven or a package manager for the supported build. `server/build.xml`
and the `server/lib/` directory are the dependency manifest; `server/build.gradle`
exists as a convenience IDE/developer build and must be kept in step with it. This
guide records the current inventory, the reason for the vendoring layout, and the
checks that protect it from drift.

## How dependencies are wired

- `server/lib/` contains the server runtime and compile-time jars.
- `PC_Client/lib/discord-rpc.jar` is the desktop Rich Presence bridge.
- `server/build.xml` names the jars used by the server compile and run
  targets. The run targets also include `${lib}/*`, which is useful for the
  fat-jar/runtime layout but means an accidentally removed jar can be masked
  during a run.
- `Client_Base/build.xml` embeds the desktop client's `PC_Client/lib` jar and
  is compiled together with `Client_Base/src` and `PC_Client/src`.
- `scripts/check_dependencies.sh` scans named `${lib}/…jar` references after
  removing XML comments, fails when a named jar is absent, and warns about
  present jars that are only supplied by a wildcard classpath.

Run the guard from the repository root:

```sh
bash scripts/check_dependencies.sh
```

The guard is also run by the `dependencyGuard` job in `.gitlab-ci.yml`.
When it fails, update the build file and the vendored jar together; do not
paper over a missing file by broadening a classpath.

## Server library inventory

The following jars are present in `server/lib/` at this snapshot. Versions
are taken from the filenames. Licence names are the upstream project licence
families; consult the jar's `META-INF`/upstream distribution for the complete
notice and any bundled dependency notices.

| Jar | Version | Project role | Licence / note |
|---|---:|---|---|
| `JDA-4.0.0_55-withDependencies.jar` | 4.0.0.55 | Discord integration and bot-facing server features | LGPL-3.0; includes many dependencies and bundles an SLF4J 1.7 API — see the runtime note below |
| `commons-codec-1.19.0.jar` | 1.19.0 | Base64, digests, and encodings | Apache-2.0 |
| `commons-collections4-4.5.0.jar` | 4.5.0 | `IterableMap` used by bank presets | Apache-2.0 |
| `commons-compress-1.28.0.jar` | 1.28.0 | Compressed archive formats (BZip2 world data) | Apache-2.0; **needs `commons-io` on the runtime classpath**, see below |
| `commons-io-2.20.0.jar` | 2.20.0 | Stream helpers; `CloseShieldInputStream` is reached from `BZip2CompressorInputStream` | Apache-2.0; required by `commons-compress` 1.27+ |
| `commons-lang-2.6.jar` | 2.6 | Legacy `org.apache.commons.lang` APIs | Apache-2.0 |
| `commons-lang3-3.18.0.jar` | 3.18.0 | Current `org.apache.commons.lang3` APIs | Apache-2.0 |
| `disruptor-3.4.4.jar` | 3.4.4 | Log4j asynchronous logging | Apache-2.0 |
| `emoji-java-5.1.1.jar` | 5.1.1 | Unicode emoji parsing/rendering | Apache-2.0 |
| `gitlab4j-api-4.12.17.jar` | 4.12.17 | GitLab API client | MIT |
| `guava-33.4.8-jre.jar` | 33.4.8-jre | Collections, caches, and utility APIs | Apache-2.0 |
| `guice-5.0.2-jar-with-dependencies.jar` | 5.0.2 | Dependency injection; bundled dependencies | Apache-2.0 |
| `json-20250517.jar` | 20250517 | Legacy JSON parsing | JSON-java licence; verify the bundled notice when redistributing |
| `log4j-api-2.25.2.jar` | 2.25.2 | Log4j API | Apache-2.0 |
| `log4j-core-2.25.2.jar` | 2.25.2 | Log4j implementation | Apache-2.0 |
| `log4j-iostreams-2.25.2.jar` | 2.25.2 | Log4j stream layouts | Apache-2.0 |
| `log4j-slf4j-impl-2.25.2.jar` | 2.25.2 | SLF4J 1.7-to-Log4j2 binder | Apache-2.0; this is the 1.7 binder JDA's shaded SLF4J API needs |
| `mysql-connector-j-9.4.0.jar` | 9.4.0 | MySQL/MariaDB JDBC connectivity | GPL-2.0 with Classpath/linking exception; preserve notices |
| `netty-all-4.1.67.Final.jar` | 4.1.67.Final | Server networking and protocol transport | Apache-2.0; last release of the artifact that is still a true uber-jar |
| `sqlite-jdbc-3.50.3.0.jar` | 3.50.3.0 | Embedded SQLite database support | Apache-2.0 |
| `xpp3-1.1.4c.jar` | 1.1.4c | XML pull parsing used by XStream integrations | XPP3 licence; preserve upstream notice |
| `xstream-1.4.21.jar` | 1.4.21 | XML serialization/configuration | BSD-3-Clause |

## Client library inventory

| Jar | Version | Project role | Licence / note |
|---|---:|---|---|
| `PC_Client/lib/discord-rpc.jar` | vendored, no version in filename | Native Discord Rich Presence bridge embedded by the desktop client | MIT-family Discord RPC client distribution; preserve the upstream notice |

## Repaired stale references

The previous server build file named jars that are not in `server/lib`:
`disruptor-3.3.0.jar`, `disruptor-3.3.5.jar`, `xpp3_min-1.1.4c.jar`, and
`xstream-1.4.9.jar`. It also had commented-out Netty split-jar references.
Those were already repaired: the live compile/run paths use the vendored
jar names, the absent minimized XPP3 and old XStream entries are gone, and the
commented split-jar recipe is documentation only (the dependency guard ignores
XML comments by design).

## Version refresh (2026-09-26)

The whole server runtime was moved onto current, still-Java-8-compatible
releases, because the previous set was years behind and carried known
vulnerabilities (old Netty, Log4j 2.17.0, commons-compress 1.18,
xstream 1.4.9, json 20190722, commons-lang3 3.12.0, guava 30.1.1):

| Was | Now | Why |
|---|---|---|
| `netty-all-4.1.33.Final` | `netty-all-4.1.67.Final` | 4.1.33 has multiple DoS/request-smuggling advisories. 4.1.67 is the last release published as a genuine uber-jar, so the single-vendored-file model still holds. |
| `log4j-* 2.17.0` | `log4j-* 2.25.2` | 2.17.0 was the post-Log4Shell emergency release; 2.25.x is the maintained line. |
| `log4j-slf4j18-impl` | `log4j-slf4j-impl` | The 1.8 binder cannot serve JDA's shaded SLF4J 1.7 API. The 1.7 binder fixes the NOP fallback (see below). |
| `commons-compress-1.18` | `commons-compress-1.28.0` | Fixes the 1.26.0-era decompression DoS/OOM advisories. |
| `commons-lang3-3.12.0` | `commons-lang3-3.18.0` | Includes the `ClassUtils` stack-overflow fix. |
| `commons-collections4-4.0` | `commons-collections4-4.5.0` | Maintenance; also now named explicitly in the compile classpath (see below). |
| `commons-codec-1.14` | `commons-codec-1.19.0` | Maintenance. |
| `xstream-1.4.18` | `xstream-1.4.21` | 1.4.20+ closes the deserialization-gadget advisories. |
| `json-20190722` | `json-20250517` | Fixes the JSON-java parser advisories. |
| `guava-30.1.1-jre` | `guava-33.4.8-jre` | Fixes the temporary-directory and `Files.createTempDir` advisories. |
| `sqlite-jdbc-3.34.0` | `sqlite-jdbc-3.50.3.0` | Current embedded SQLite. |
| `disruptor-3.3.11` | `disruptor-3.4.4` | Matches the Log4j 2.25 async-logging expectation. |
| `slf4j-nop-2.0.0-alpha5` | *removed* | An SLF4J 2.x alpha provider that competed with the real binder. |

`commons-io-2.20.0.jar` is newly vendored: `commons-compress` 1.27+ declares
`commons-io` as a compile-scope dependency, and
`BZip2CompressorInputStream` now reaches
`org.apache.commons.io.input.CloseShieldInputStream` while loading world data.
Without it the server compiles but aborts at startup with
`NoClassDefFoundError`. Only a runtime boot catches that, which is why the
release process boots the world before shipping.

`server/build.gradle` had drifted from the vendored files (it asked for
`netty-all:4.1.107.Final`, `xstream:1.4.9`, `guice:5.0.1`, `emoji-java:4.0.0`,
so Gradle and Ant compiled against different artifacts). Every version string
in that file now matches the vendored filename, the unused
`repo.spring.io/libs-release` repository is gone, and the `jcenter` comment
that motivated the local JDA file was replaced with a note pointing here.

## Resolved runtime issue: JDA and SLF4J

Server logs used to contain:

```text
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder"
SLF4J: Defaulting to no-operation (NOP) logger implementation
```

The shaded `JDA-4.0.0_55-withDependencies.jar` bundles the SLF4J 1.7 API but
no 1.7 `StaticLoggerBinder`, and the previously vendored
`log4j-slf4j18-impl-2.17.0.jar` is a 1.8/2.x binding that cannot satisfy it.
Vendoring `log4j-slf4j-impl-2.25.2.jar` (the 1.7 binder) and dropping the
competing `slf4j-nop` alpha resolves it. A boot now logs:

```text
SLF4J: Actual binding is of type [org.apache.logging.slf4j.Log4jLoggerFactory]
```

A `SLF4J: Class path contains multiple SLF4J bindings.` line still appears
when the server is started with both `core.jar` and `lib/*` on the classpath.
That is expected and not a second binder problem: `core.jar` is built as a fat
jar by merging every `server/lib` jar into it, and the loose directory is also
on the classpath. Both listed binders are the same Log4j 2.25.2 binder. Making
`core.jar` thin would remove the message but changes the shipped artifact
contract for undocumented `java -jar core.jar` use, so it was deliberately
left alone here.

## Updating a dependency

1. Identify every source import and every Ant target that uses the library.
2. Add the replacement jar under the correct `lib/` directory and record its
   exact version and upstream licence.
3. Update every named classpath entry; remove stale comments only when the
   recipe is no longer supported.
4. Check the new jar's POM for hard (non-`test`, non-`optional`) dependencies.
   Vendoring a jar does not vendor what it needs, and `commons-compress` is the
   proof: the missing `commons-io` only surfaced at world load.
5. Keep the matching `server/build.gradle` version string in step with the
   vendored filename.
6. Run `bash scripts/check_dependencies.sh` and the relevant Ant compile
   targets. For a server update, run both `ant compile_core` and
   `ant compile_plugins`; for a client update, run `Client_Base`'s compile
   target, which also compiles `PC_Client/src`.
7. For a server runtime jar, also boot the world once and check `/healthz` on
   the WebSocket port. Compilation cannot catch a missing runtime-only class.
8. Verify the replacement is still Java 8 compatible: every base
   (non-`META-INF/versions/`) class must be at most class-file major 52, and
   only the root `module-info.class` may be newer.
5. Exercise the feature that needs the library. A successful compile does not
   prove a shaded-jar, logging, database, or native bridge runtime works.
6. Review the diff for accidental binary churn and keep the source-quality
   artwork and runtime asset rules in `docs/RUNEWAKE_ASSET_INVENTORY.md`
   separate from Java libraries.
