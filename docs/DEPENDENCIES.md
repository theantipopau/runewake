# RuneWake dependency guide

RuneWake vendors its Java libraries in the repository rather than resolving
them with Maven, Gradle, or a package manager. The source directories and Ant
build files are therefore the dependency manifest. This guide records the
current inventory, the reason for the vendoring layout, and the checks that
protect it from drift.

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
| `commons-codec-1.14.jar` | 1.14 | Base64, digests, and encodings | Apache-2.0 |
| `commons-compress-1.18.jar` | 1.18 | Compressed archive formats | Apache-2.0 |
| `commons-lang-2.6.jar` | 2.6 | Legacy `org.apache.commons.lang` APIs | Apache-2.0 |
| `commons-lang3-3.12.0.jar` | 3.12.0 | Current `org.apache.commons.lang3` APIs | Apache-2.0 |
| `disruptor-3.3.11.jar` | 3.3.11 | Log4j asynchronous logging | Apache-2.0 |
| `emoji-java-5.1.1.jar` | 5.1.1 | Unicode emoji parsing/rendering | Apache-2.0 |
| `gitlab4j-api-4.12.17.jar` | 4.12.17 | GitLab API client | MIT |
| `guava-30.1.1-jre.jar` | 30.1.1-jre | Collections, caches, and utility APIs | Apache-2.0 |
| `guice-5.0.2-jar-with-dependencies.jar` | 5.0.2 | Dependency injection; bundled dependencies | Apache-2.0 |
| `json-20190722.jar` | 20190722 | Legacy JSON parsing | JSON-java licence; verify the bundled notice when redistributing |
| `log4j-api-2.17.0.jar` | 2.17.0 | Log4j API | Apache-2.0 |
| `log4j-core-2.17.0.jar` | 2.17.0 | Log4j implementation | Apache-2.0 |
| `log4j-iostreams-2.17.0.jar` | 2.17.0 | Log4j stream layouts | Apache-2.0 |
| `log4j-slf4j18-impl-2.17.0.jar` | 2.17.0 | SLF4J 1.8-to-Log4j2 adapter | Apache-2.0; not an SLF4J 1.7 binder |
| `mysql-connector-j-9.4.0.jar` | 9.4.0 | MySQL/MariaDB JDBC connectivity | GPL-2.0 with Classpath/linking exception; preserve notices |
| `netty-all-4.1.33.Final.jar` | 4.1.33.Final | Server networking and protocol transport | Apache-2.0; old but intentionally retained for this fork |
| `slf4j-nop-2.0.0-alpha5.jar` | 2.0.0-alpha5 | No-op SLF4J implementation | MIT; alpha release, not a replacement for a matching server binding |
| `sqlite-jdbc-3.34.0.jar` | 3.34.0 | Embedded SQLite database support | Apache-2.0 |
| `xpp3-1.1.4c.jar` | 1.1.4c | XML pull parsing used by XStream integrations | XPP3 licence; preserve upstream notice |
| `xstream-1.4.18.jar` | 1.4.18 | XML serialization/configuration | BSD-3-Clause |

## Client library inventory

| Jar | Version | Project role | Licence / note |
|---|---:|---|---|
| `PC_Client/lib/discord-rpc.jar` | vendored, no version in filename | Native Discord Rich Presence bridge embedded by the desktop client | MIT-family Discord RPC client distribution; preserve the upstream notice |

## Repaired stale references

The previous server build file named jars that are not in `server/lib`:
`disruptor-3.3.0.jar`, `disruptor-3.3.5.jar`, `xpp3_min-1.1.4c.jar`, and
`xstream-1.4.9.jar`. It also had commented-out Netty split-jar references.
The live compile/run paths now use the vendored `disruptor-3.3.11.jar` and
`xstream-1.4.18.jar`, remove the absent minimized XPP3 and old XStream
entries, and name `log4j-slf4j18-impl-2.17.0.jar` where the server classpath
needs it. The commented split-jar recipe remains documentation only and is
intentionally ignored by the guard.

## Known runtime issue: JDA and SLF4J

A server log can currently contain:

```text
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder"
SLF4J: Defaulting to no-operation (NOP) logger implementation
```

This is not caused by the dependency guard. The shaded
`JDA-4.0.0_55-withDependencies.jar` contains an SLF4J 1.7 API
(`org.slf4j.Logger` and `org.slf4j.LoggerFactory`) but no 1.7
`StaticLoggerBinder`. The vendored `log4j-slf4j18-impl-2.17.0.jar` is an
SLF4J 2.x service-provider binding, so it cannot satisfy the bundled 1.7 API;
`slf4j-nop-2.0.0-alpha5.jar` is also an SLF4J 2.x implementation. RuneWake's
server source does not import SLF4J directly; JDA is the consumer.

Do not fix this by adding the 2.x jars again. The safe options are:

1. replace the shaded JDA jar with a current, non-shaded JDA release and
   declare its matching SLF4J API/binding pair; or
2. retain the JDA line and add a matching SLF4J 1.7 API plus
   `slf4j-log4j12` (or another deliberately selected 1.7 binding), then
   review duplicate classes and the fat-jar ordering.

That choice needs a runtime integration test with Discord enabled and should
be a separate dependency migration. Until then, the compile paths and the
named-file guard are verified, but Discord logging can still fall back to NOP.

## Updating a dependency

1. Identify every source import and every Ant target that uses the library.
2. Add the replacement jar under the correct `lib/` directory and record its
   exact version and upstream licence.
3. Update every named classpath entry; remove stale comments only when the
   recipe is no longer supported.
4. Run `bash scripts/check_dependencies.sh` and the relevant Ant compile
   targets. For a server update, run both `ant compile_core` and
   `ant compile_plugins`; for a client update, run `Client_Base`'s compile
   target, which also compiles `PC_Client/src`.
5. Exercise the feature that needs the library. A successful compile does not
   prove a shaded-jar, logging, database, or native bridge runtime works.
6. Review the diff for accidental binary churn and keep the source-quality
   artwork and runtime asset rules in `docs/RUNEWAKE_ASSET_INVENTORY.md`
   separate from Java libraries.
