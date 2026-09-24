#!/usr/bin/env python3
"""Build a clean RuneWake release bundle from the current checkout.

The bundle is intentionally a player/operator distribution, not a second
source checkout. It contains the built client, built server, portable build
runtime, required server data/configuration, launcher, and concise operator
documentation. Local state, secrets, logs, and source/build scratch are never
copied.

Usage:
    python scripts/build_release.py --version 0.1.0
    python scripts/build_release.py --version 0.1.0 --output-dir dist

The script does not commit, tag, push, or publish anything.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import subprocess
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def run(*args: str, cwd: Path = ROOT) -> None:
    print("+", " ".join(args), flush=True)
    subprocess.run(args, cwd=cwd, check=True)


def copy_tree(source: Path, destination: Path, excludes: set[str] | None = None) -> None:
    excludes = excludes or set()
    shutil.copytree(
        source,
        destination,
        ignore=shutil.ignore_patterns(*excludes) if excludes else None,
    )


def copy_file(source: Path, destination: Path) -> None:
    if not source.is_file():
        raise FileNotFoundError(f"Required release file is missing: {source.relative_to(ROOT)}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="Release version, for example 0.1.0")
    parser.add_argument("--output-dir", default="dist", help="Output directory relative to the repository root")
    args = parser.parse_args()

    version = args.version
    if not version or any(char not in "0123456789.-" for char in version):
        raise SystemExit("Version must contain only digits, dots, and hyphens")
    output_dir = (ROOT / args.output_dir).resolve()
    if output_dir.exists():
        raise SystemExit(f"Output directory already exists: {output_dir}")
    bundle = output_dir / f"RuneWake-{version}"
    archive = output_dir / f"RuneWake-{version}.zip"
    output_dir.mkdir(parents=True)

    # Build outputs are the release inputs. The project has no test suite, so
    # these are the same Ant targets used by the CI pipeline.
    java_home = ROOT / "Portable_Windows/zulu8.50.0.51-ca-jdk8.0.275-win_x64"
    ant_home = ROOT / "Portable_Windows/apache-ant-1.10.5"
    env = os.environ.copy()
    env["JAVA_HOME"] = str(java_home)
    env["ANT_HOME"] = str(ant_home)
    env["PATH"] = str(java_home / "bin") + os.pathsep + str(ant_home / "bin") + os.pathsep + env.get("PATH", "")
    print("Building client and server artifacts...", flush=True)
    subprocess.run([str(ant_home / "bin/ant.bat"), "-f", "Client_Base/build.xml", "compile"], cwd=ROOT, env=env, check=True)
    subprocess.run([str(ant_home / "bin/ant.bat"), "-f", "server/build.xml", "compile_core", "compile_plugins"], cwd=ROOT, env=env, check=True)
    subprocess.run([str(ant_home / "bin/ant.bat"), "-f", "PC_Launcher/build.xml", "compile"], cwd=ROOT, env=env, check=True)

    bundle.mkdir()
    (bundle / "README.md").write_text(
        f"# RuneWake {version}\n\n"
        "This is a clean player/operator distribution bundle. It includes the "
        "prebuilt client and server, the portable Java/Ant runtime used by the "
        "Windows helpers, and the required server data/configuration.\n\n"
        "## Start locally\n\n"
        "1. Run `run-client.bat` to start the local server and client.\n"
        "2. Run `run-server.bat` to start only the server.\n\n"
        "The server uses the bundled SQLite world database and listens on TCP "
        "ports 43594 (game) and 43494 (WebSocket/status) by default.\n\n"
        "## Operator notes\n\n"
        "- See `server/SIMPLE_HOSTING.md` before exposing ports publicly.\n"
        "- See `web/server-browser/README.md` before adding a public server.\n"
        "- This archive contains no `.env`, Discord webhook values, logs, local "
        "player databases, or source-control metadata.\n"
        "- The release is built from the repository's `develop` branch at the "
        "time of packaging; verify the release tag and SHA-256 file after download.\n",
        encoding="utf-8",
    )
    (bundle / "LICENSE").write_bytes((ROOT / "LICENSE").read_bytes())
    (bundle / "RELEASE.txt").write_text(
        f"RuneWake {version}\n"
        f"Packaged UTC: {datetime.now(timezone.utc).isoformat()}\n"
        f"Source commit: {subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()}\n"
        "Build targets: Client_Base compile; server compile_core + compile_plugins; PC_Launcher compile\n"
        "Bundle type: player/operator distribution (not a source checkout)\n",
        encoding="utf-8",
    )

    # Player/operator entry points and documentation.
    for name in ("run-client.bat", "run-server.bat", "Windows Getting Started Guide.md", "Linux Getting Started Guide.md", "MacOS Getting Started Guide.md"):
        copy_file(ROOT / name, bundle / name)
    copy_file(ROOT / "server/SIMPLE_HOSTING.md", bundle / "server" / "SIMPLE_HOSTING.md")
    copy_file(ROOT / "server/CENTRALIZED_DATABASE.md", bundle / "server" / "CENTRALIZED_DATABASE.md")
    copy_file(ROOT / "PC_Launcher/README.md", bundle / "PC_Launcher" / "README.md")
    copy_file(ROOT / "PC_Launcher/SECURITY.md", bundle / "PC_Launcher" / "SECURITY.md")

    # Build/runtime tools. These are intentionally included so a fresh Windows
    # machine can run the supplied batch files without installing Java/Ant.
    copy_tree(ROOT / "Portable_Windows/zulu8.50.0.51-ca-jdk8.0.275-win_x64", bundle / "Portable_Windows/zulu8.50.0.51-ca-jdk8.0.275-win_x64")
    copy_tree(ROOT / "Portable_Windows/apache-ant-1.10.5", bundle / "Portable_Windows/apache-ant-1.10.5")

    # Client runtime and the source/build inputs required by run-client.bat.
    # The script recompiles the client, so shipping only the prebuilt jar
    # would make the convenience entry point fail on a fresh machine.
    copy_file(ROOT / "Client_Base/build.xml", bundle / "Client_Base/build.xml")
    copy_tree(ROOT / "Client_Base/src", bundle / "Client_Base/src")
    copy_tree(ROOT / "PC_Client/src", bundle / "PC_Client/src")
    copy_tree(ROOT / "PC_Client/lib", bundle / "PC_Client/lib")
    copy_file(ROOT / "Client_Base/Open_RSC_Client.jar", bundle / "Client_Base/Open_RSC_Client.jar")
    copy_tree(
        ROOT / "Client_Base/Cache",
        bundle / "Client_Base/Cache",
        excludes={"ip.txt", "port.txt", "uid.dat", "config.txt", "discord_inuse.txt", "credentials.txt"},
    )
    # run-client.bat reads port.txt before starting the server, and the client
    # expects an IP file. These are safe distribution defaults, not local state.
    (bundle / "Client_Base/Cache/ip.txt").write_text("localhost\\n", encoding="ascii")
    (bundle / "Client_Base/Cache/port.txt").write_text("43594\\n", encoding="ascii")
    copy_file(ROOT / "PC_Launcher/OpenRSC.jar", bundle / "PC_Launcher/OpenRSC.jar")

    # Server runtime and data. Local sqlite DBs/logs are deliberately omitted;
    # the release starts from the checked-in SQL schema and creates a fresh DB
    # only when the operator follows the database setup instructions.
    copy_file(ROOT / "server/build.xml", bundle / "server/build.xml")
    copy_tree(ROOT / "server/src", bundle / "server/src")
    copy_file(ROOT / "server/core.jar", bundle / "server/core.jar")
    copy_file(ROOT / "server/plugins.jar", bundle / "server/plugins.jar")
    copy_tree(ROOT / "server/lib", bundle / "server/lib")
    copy_tree(ROOT / "server/conf", bundle / "server/conf")
    copy_tree(ROOT / "server/database", bundle / "server/database")
    copy_tree(ROOT / "server/plugins", bundle / "server/plugins")
    copy_tree(ROOT / "server/inc", bundle / "server/inc", excludes={"*.db", "Dockerfile", "innodb.cnf", "my.cnf"})
    # Seed databases are generated from checked-in SQL, never copied from the
    # developer's local server/inc/sqlite directory. The default bundle uses
    # the same auth-style schema as the default world and adds the tables that
    # the default config can enable through server-side features.
    import sqlite3
    db_path = bundle / "server/inc/sqlite/preservation.db"
    db_path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(db_path)
    try:
        connection.executescript((ROOT / "server/database/sqlite/core.sqlite").read_text(encoding="utf-8"))
        for addon in ("add_auctionhouse.sqlite", "add_bank_presets.sqlite", "add_clans.sqlite", "add_equipment_tab.sqlite", "add_npc_kill_counting.sqlite"):
            connection.executescript((ROOT / "server/database/sqlite/addons" / addon).read_text(encoding="utf-8"))
        connection.commit()
    finally:
        connection.close()
    for path in ROOT.glob("server/*.conf"):
        copy_file(path, bundle / "server" / path.name)
    for path in (ROOT / "server/globalrules.txt", ROOT / "server/connections.conf", ROOT / "server/ipbans.txt", ROOT / "server/ipmutes.txt"):
        copy_file(path, bundle / "server" / path.name)

    # Preserve the static web tools as source-like documentation in the
    # distribution; they are harmless without a web server and let operators
    # inspect how the server browser obtains status information.
    copy_tree(ROOT / "web/server-browser", bundle / "web/server-browser")

    # The manifest intentionally covers every payload file except itself; a
    # manifest cannot contain its own stable digest without a fixed-point hash.
    files = sorted(path for path in bundle.rglob("*") if path.is_file() and path.name != "MANIFEST.sha256")
    manifest_lines = [f"{sha256(path)}  {path.relative_to(bundle).as_posix()}" for path in files]
    (bundle / "MANIFEST.sha256").write_text("\n".join(manifest_lines) + "\n", encoding="utf-8")

    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zipped:
        for path in sorted(bundle.rglob("*")):
            if path.is_file():
                zipped.write(path, Path(bundle.name) / path.relative_to(bundle))

    print(f"Bundle: {bundle}")
    print(f"Archive: {archive} ({archive.stat().st_size} bytes)")
    print(f"SHA256: {sha256(archive)}")
    print(f"Files: {len(files) + 1}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as error:
        print(f"ERROR: build command failed with exit code {error.returncode}", file=sys.stderr)
        raise
