#!/usr/bin/env python3
"""Vendored-jar review report.

RuneWake commits its Java libraries to the repository (server/lib, PC_Client/lib)
instead of resolving them from a package manager, so Dependabot has no lockfile
to bump. This script is the substitute: it reads
.github/vendored-dependencies.json, asks Maven Central what each upstream
coordinate publishes, and writes a Markdown report that a scheduled workflow
turns into (or updates) a single maintenance issue.

It also fails when the manifest and the tree disagree - a jar on disk with no
entry is an unmanaged dependency, and a manifest entry whose file is gone is
stale bookkeeping.

Usage:
    python3 scripts/check_vendored_dependencies.py --output report.md
    python3 scripts/check_vendored_dependencies.py --verify-java8

Options:
    --output PATH   Write the Markdown report here, plus PATH.status containing
                    one word: current | review | error. Without it the report
                    goes to stdout and the status is only the exit code.
    --verify-java8  Skip the network check and instead scan every vendored jar's
                    class files, failing when a base-ABI class needs more than
                    Java 8 (class-file major 52). This is the gate that stops an
                    attractive version bump from silently raising the runtime
                    requirement of the supported build.
    --timeout SECS  Per-request network timeout (default 20).
    --manifest PATH Manifest to use (default .github/vendored-dependencies.json).

Exit codes: 0 = report written, 1 = a check failed (or --verify-java8 found a
newer class-file version), 2 = the manifest/tree could not be read.
"""

from __future__ import annotations

import argparse
import json
import re
import struct
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_MANIFEST = ROOT / ".github" / "vendored-dependencies.json"

# Scanned for jars that exist on disk but are missing from the manifest.
LIB_DIRS = ("server/lib", "PC_Client/lib")

PRERELEASE = re.compile(
    r"(?i)(?:^|[.\-_])(?:alpha|beta|rc|cr|m|ea|preview|snapshot)(?:[.\-_]?\d|$)"
)


@dataclass
class Entry:
    jar: str
    group: str | None = None
    artifact: str | None = None
    version: str | None = None
    classifier: str | None = None
    tracking: str = "maven"
    expected_ahead: bool = False
    note: str = ""
    status: str = "current"
    latest_same_major: str | None = None
    latest_overall: str | None = None
    detail: str = ""

    @property
    def coordinate(self) -> str:
        if not (self.group and self.artifact):
            return "-"
        text = f"{self.group}:{self.artifact}"
        if self.classifier:
            text += f":{self.classifier}"
        return text


def version_tokens(value: str) -> list[tuple[int, str]]:
    """Order-independent-ish sort key.

    Versions here are not all semver: `4.0.0_55`, `20250517`, `1.1.4c`,
    `4.1.139.Final` and `33.4.8-jre` all appear. Split on the usual separators,
    keep the leading number of each token numeric, and compare the rest as text
    so `4.1.139.Final` still sorts above `4.1.139` and prefix cases stay stable.
    """

    tokens: list[tuple[int, str]] = []
    for part in re.split(r"[._+\-]", value.strip().lower()):
        if not part:
            continue
        match = re.match(r"^(\d*)(.*)$", part)
        number, rest = match.group(1), match.group(2)
        tokens.append((int(number) if number else -1, rest))
    return tokens


def major_of(value: str) -> int | None:
    match = re.match(r"^\s*(\d+)", value)
    return int(match.group(1)) if match else None


def line_of(value: str) -> str:
    """The release line a version belongs to.

    Almost everything here is `<major>.<minor>...`, so the leading number is
    the line. `org.json:json` is the exception: it only publishes calendar
    versions (`20250517`), where the leading number is a date and every release
    would otherwise look like a new major line.
    """

    if re.fullmatch(r"\d{6,}", value.strip()):
        return "calendar"
    return str(major_of(value))


def is_prerelease(value: str) -> bool:
    return bool(PRERELEASE.search(value))


def compare(left: str, right: str) -> int:
    a, b = version_tokens(left), version_tokens(right)
    for x, y in zip(a, b):
        if x != y:
            return -1 if x < y else 1
    if len(a) == len(b):
        return 0
    return -1 if len(a) < len(b) else 1


def fetch_versions(entry: Entry, repos: list[str], timeout: float) -> list[str]:
    """Return the published versions of the entry's coordinate, or raise."""

    path = f"{entry.group.replace('.', '/')}/{entry.artifact}/maven-metadata.xml"
    errors: list[str] = []
    for repo in repos:
        url = f"{repo.rstrip('/')}/{path}"
        for attempt in (1, 2):
            try:
                with urllib.request.urlopen(url, timeout=timeout) as response:
                    payload = response.read()
                root = ET.fromstring(payload)
                versions = [node.text.strip() for node in root.iter("version") if node.text]
                if versions:
                    return versions
                errors.append(f"{url}: no <version> entries")
                break
            except (urllib.error.URLError, TimeoutError, ET.ParseError, OSError) as exc:
                if attempt == 2:
                    errors.append(f"{url}: {exc}")
    raise RuntimeError("; ".join(errors) or "no repositories configured")


def assess(entry: Entry, repos: list[str], timeout: float) -> None:
    if entry.tracking == "manual":
        entry.status = "manual"
        return
    if not (entry.group and entry.artifact and entry.version):
        entry.status = "unknown"
        entry.detail = "manifest entry is missing group/artifact/version"
        return
    if not (ROOT / entry.jar).is_file():
        entry.status = "missing"
        entry.detail = "jar is listed in the manifest but not on disk"
        return
    try:
        versions = fetch_versions(entry, repos, timeout)
    except RuntimeError as exc:
        entry.status = "unknown"
        entry.detail = f"could not read upstream metadata ({exc})"
        return

    stable = [v for v in versions if not is_prerelease(v)]
    candidates = stable or versions
    line = line_of(entry.version)
    same_major = [v for v in candidates if line_of(v) == line]
    entry.latest_same_major = max(same_major, key=version_tokens) if same_major else None
    entry.latest_overall = max(candidates, key=version_tokens) if candidates else None

    # `maven` entries must exist upstream; `line` entries are published
    # elsewhere (GitHub releases, jcenter history) and are only compared by
    # release line, so a missing exact version is not an error.
    if entry.tracking != "line" and entry.version not in versions and not same_major:
        entry.status = "unknown"
        entry.detail = "the vendored version is not published at this coordinate"
        return

    if entry.latest_same_major and compare(entry.latest_same_major, entry.version) > 0:
        entry.status = "update"
        return
    if (
        entry.latest_overall
        and line_of(entry.latest_overall) != line
        and compare(entry.latest_overall, entry.version) > 0
    ):
        entry.status = "major"
        entry.detail = f"a newer release line ({entry.latest_overall}) exists"
        return
    if entry.latest_same_major and compare(entry.latest_same_major, entry.version) < 0:
        entry.status = "ahead"
        entry.detail = (
            "known ahead of upstream by design - see the note"
            if entry.expected_ahead
            else "the vendored build is newer than any published release"
        )
        return
    entry.status = "current"


def scan_manifest(manifest_path: Path) -> tuple[list[Entry], list[str], list[str]]:
    try:
        data = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit(f"cannot read manifest {manifest_path}: {exc}")
    entries = [
        Entry(
            jar=str(raw.get("jar", "")).strip(),
            group=(raw.get("group") or None),
            artifact=(raw.get("artifact") or None),
            version=(raw.get("version") or None),
            classifier=(raw.get("classifier") or None),
            tracking=str(raw.get("tracking") or "maven"),
            expected_ahead=bool(raw.get("expected_ahead")),
            note=str(raw.get("note") or ""),
        )
        for raw in data.get("jars", [])
    ]
    bad = [e.jar for e in entries if not e.jar]
    entries = [e for e in entries if e.jar]

    managed = {e.jar for e in entries}
    on_disk: list[str] = []
    for directory in LIB_DIRS:
        for jar in sorted((ROOT / directory).glob("*.jar")):
            on_disk.append(jar.relative_to(ROOT).as_posix())
    unmanaged = [jar for jar in on_disk if jar not in managed]
    return entries, unmanaged, bad


def verify_java8(entries: list[Entry]) -> int:
    print("Java 8 ABI check (base classes must be class-file major 52 or lower)")
    print("META-INF/versions/ classes are multi-release variants and are skipped.\n")
    failures = 0
    for entry in entries:
        path = ROOT / entry.jar
        if not path.is_file():
            print(f"SKIP  {entry.jar}: not on disk")
            continue
        try:
            with zipfile.ZipFile(path) as archive:
                majors = set()
                for name in archive.namelist():
                    if not name.endswith(".class"):
                        continue
                    if name.startswith("META-INF/versions/"):
                        continue
                    # A root module-info.class is routinely compiled for Java 9+
                    # even in Java 8 compatible jars; Java 8 ignores it.
                    if name.endswith("module-info.class"):
                        continue
                    header = archive.read(name)[:8]
                    if len(header) < 8 or header[:4] != b"\xca\xfe\xba\xbe":
                        continue
                    majors.add(struct.unpack(">H", header[6:8])[0])
        except (OSError, zipfile.BadZipFile) as exc:
            print(f"ERROR {entry.jar}: {exc}")
            failures += 1
            continue
        newest = max(majors) if majors else 0
        if newest > 52:
            print(f"FAIL  {entry.jar}: class-file major {newest} (> 52, needs a newer JDK)")
            failures += 1
        else:
            print(f"OK    {entry.jar}: major {newest}")
    if failures:
        print(f"\nFAILED: {failures} vendored jar(s) are not Java 8 compatible.")
        return 1
    print("\nOK: every vendored jar is loadable by Java 8.")
    return 0


def render(entries: list[Entry], unmanaged: list[str], bad: list[str]) -> tuple[str, str]:
    rows = {
        "update": [],
        "major": [],
        "ahead": [],
        "unknown": [],
        "missing": [],
        "current": [],
        "manual": [],
    }
    for entry in entries:
        rows.setdefault(entry.status, []).append(entry)

    unexpected_ahead = [e for e in rows["ahead"] if not e.expected_ahead]
    flagged = (
        rows["update"]
        + rows["major"]
        + unexpected_ahead
        + rows["unknown"]
        + rows["missing"]
        + [Entry(jar=jar, status="unmanaged") for jar in unmanaged]
        + [Entry(jar=jar, status="unknown") for jar in bad]
    )
    status = "review" if flagged else "current"

    stamp = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    lines = [
        f"<!-- vendored-deps: status={status} -->",
        "Automated review of the Java libraries vendored in this repository. "
        "Dependabot cannot see committed jars, so this issue is the standing "
        "checklist for them: it is reopened or updated by "
        "`.github/workflows/vendored-dependencies.yml`, and closed automatically "
        "once every jar matches its upstream release line again.",
        "",
        f"Generated {stamp} from `.github/vendored-dependencies.json` "
        f"({len(entries)} tracked jars).",
        "",
    ]

    if status == "current":
        lines += [
            "## Nothing to do",
            "",
            "Every tracked jar matches the newest release in its current major "
            "line, the manifest covers every jar on disk, and every listed jar "
            "is present.",
            "",
        ]
    else:
        lines += [
            f"## Summary",
            "",
            f"- {len(rows['update'])} jar(s) with a newer release in the same major line",
            f"- {len(rows['major'])} jar(s) with a newer major line (migration, not a bump)",
            f"- {len(rows['unknown']) + len(bad)} jar(s) that could not be checked",
            f"- {len(unexpected_ahead)} jar(s) unexpectedly newer than any published release",
            f"- {len(rows['missing'])} manifest entr(y/ies) whose jar is gone",
            f"- {len(unmanaged)} jar(s) on disk that the manifest does not describe",
            "",
        ]

    def table(title: str, body: str, items: list[Entry], show_upstream: bool) -> None:
        if not items:
            return
        lines.append(f"## {title}")
        lines.append("")
        lines.append(body)
        lines.append("")
        if show_upstream:
            lines.append("| Jar | Vendored | Same line | Newest overall | Coordinate | Note |")
            lines.append("| --- | --- | --- | --- | --- | --- |")
            for e in items:
                lines.append(
                    "| `{jar}` | {current} | {same} | {overall} | {coord} | {note} |".format(
                        jar=e.jar,
                        current=e.version or "?",
                        same=e.latest_same_major or "-",
                        overall=e.latest_overall or "-",
                        coord=e.coordinate,
                        note=(e.note or e.detail or "").replace("|", "\\|"),
                    )
                )
        else:
            lines.append("| Jar | Detail |")
            lines.append("| --- | --- |")
            for e in items:
                lines.append(f"| `{e.jar}` | {(e.detail or e.note or '').replace('|', chr(92) + '|')} |")
        lines.append("")

    table(
        "Actionable: newer release in the current line",
        "Same major line, so API compatibility is expected. These are safe to "
        "treat as maintenance updates.",
        rows["update"],
        True,
    )
    table(
        "New major line available",
        "A newer release line exists. This is a migration with an API review, "
        "not a drop-in bump, and it is listed mainly so the decision is not "
        "forgotten.",
        rows["major"],
        True,
    )
    table(
        "Newer than upstream",
        "The vendored build is ahead of any published release at its "
        "coordinate. Entries marked `expected_ahead` are known and accepted; "
        "anything else needs its provenance confirmed.",
        rows["ahead"],
        True,
    )
    table(
        "Could not check",
        "The upstream metadata could not be read, or the manifest entry is "
        "incomplete. Check these by hand before closing the issue.",
        rows["unknown"] + [Entry(jar=jar, status="unknown") for jar in bad],
        False,
    )
    table(
        "Missing jars",
        "Listed in the manifest but absent from the tree. Either the file was "
        "removed without updating the manifest, or the build is broken.",
        rows["missing"],
        False,
    )
    if unmanaged:
        lines += [
            "## Unmanaged jars",
            "",
            "Present on disk but absent from `.github/vendored-dependencies.json`, "
            "so nothing tracks their upstream version. Add an entry (or remove "
            "the file) in the same commit as the change that introduced it.",
            "",
            "| Jar |",
            "| --- |",
        ]
        lines += [f"| `{jar}` |" for jar in unmanaged]
        lines.append("")

    if rows["manual"]:
        lines += [
            "## Tracked by hand",
            "",
            "No usable upstream coordinate, so these are reviewed manually.",
            "",
            "| Jar | Note |",
            "| --- | --- |",
        ]
        lines += [
            f"| `{e.jar}` | {(e.note or '').replace('|', chr(92) + '|')} |"
            for e in rows["manual"]
        ]
        lines.append("")

    if rows["current"]:
        lines += [
            "<details><summary>Current jars "
            f"({len(rows['current'])})</summary>",
            "",
            "| Jar | Version |",
            "| --- | --- |",
        ]
        lines += [f"| `{e.jar}` | {e.version or '?'} |" for e in rows["current"]]
        lines += ["", "</details>", ""]

    lines += [
        "## Review checklist",
        "",
        "- [ ] Confirm each candidate is still loadable by Java 8: "
        "`python3 scripts/check_vendored_dependencies.py --verify-java8` "
        "(base classes must be class-file major 52 or lower)",
        "- [ ] Read the candidate's POM for new hard (non-test, non-optional) "
        "dependencies - vendoring a jar does not vendor what it needs",
        "- [ ] Check the release notes and advisories that motivated the bump, "
        "especially for `netty-codec-http` (public 43494 port) and `log4j-core`",
        "- [ ] Update the jar, `server/build.xml`, `server/build.gradle`, this "
        "manifest and `docs/DEPENDENCIES.md` in one commit",
        "- [ ] Run `bash scripts/dev.sh check` (guards, compiles, parity, world "
        "boot) and, for a runtime jar, verify `/healthz`",
        "",
        "Process: [`docs/DEPENDENCIES.md`](../blob/develop/docs/DEPENDENCIES.md#periodic-review).",
    ]
    return "\n".join(lines) + "\n", status


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--manifest", default=str(DEFAULT_MANIFEST))
    parser.add_argument("--output")
    parser.add_argument("--verify-java8", action="store_true")
    parser.add_argument("--timeout", type=float, default=20.0)
    args = parser.parse_args(argv)

    manifest_path = Path(args.manifest)
    entries, unmanaged, bad = scan_manifest(manifest_path)

    if args.verify_java8:
        return verify_java8(entries)

    repos = ["https://repo1.maven.org/maven2"]
    try:
        repos = json.loads(manifest_path.read_text(encoding="utf-8")).get("repositories") or repos
    except (OSError, json.JSONDecodeError):
        pass

    for entry in entries:
        assess(entry, repos, args.timeout)

    report, status = render(entries, unmanaged, bad)

    if args.output:
        output = Path(args.output)
        output.write_text(report, encoding="utf-8")
        Path(f"{output}.status").write_text(status + "\n", encoding="utf-8")
        counts = {name: sum(1 for e in entries if e.status == name) for name in ("update", "major", "unknown")}
        print(
            f"{status}: {counts['update']} update(s), {counts['major']} major, "
            f"{counts['unknown']} unknown, {len(unmanaged)} unmanaged -> {output}"
        )
    else:
        sys.stdout.write(report)

    return 0


if __name__ == "__main__":
    sys.exit(main())
