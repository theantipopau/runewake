# Running a simple RuneWake dedicated server (Windows)

No Docker, no separate database install required — this uses the same
bundled JDK/Ant already used to build the client (`Portable_Windows/`), and
the server's default database is file-based SQLite.

## Quick start

1. Clone/copy this repo onto the machine that will host the server.
2. Double-click `run-server.bat` at the repo root (or run it from a
   terminal). This compiles the server and starts it using
   `server/default.conf`.
3. To run a different game-mode config instead, pass its name (without
   `.conf`):
   ```
   run-server.bat rsccabbage
   run-server.bat openpk
   run-server.bat 2001scape
   ```
   See the `.conf` files at the repo root of `server/` for the full list.

That's it — no database server to install or configure unless you
specifically want MySQL/MariaDB instead of the SQLite default (see
`docker-compose.yml` at the repo root, which sets up a MariaDB container if
needed; SQLite needs nothing extra).

## Making it reachable from outside your network

Two ports need to be open/forwarded to the machine running the server
(check the `.conf` file you're using for the actual values — defaults
below are from `default.conf`):

- **`server_port` (default `43594`, TCP)** — the main game connection.
  Players' clients connect here directly.
- **`ws_server_port` (default `43494`, TCP)** — only needed if
  `want_feature_websockets: true` (the default). This also serves the
  JSON status endpoint used by `web/server-browser/` (see that folder's
  README) — so it's worth opening even if you don't care about websocket
  clients, if you want your server listed in a server browser.

Both are plain TCP — no UDP forwarding needed.

### On a Proxmox host with a Windows VM

There are three layers a connection has to pass through; each needs its own
step:

1. **Windows Firewall on the VM itself.** Open PowerShell as Administrator
   on the VM and run (adjust the port numbers to match your `.conf`):
   ```powershell
   New-NetFirewallRule -DisplayName "RuneWake Game Port" -Direction Inbound -Protocol TCP -LocalPort 43594 -Action Allow
   New-NetFirewallRule -DisplayName "RuneWake WS Port" -Direction Inbound -Protocol TCP -LocalPort 43494 -Action Allow
   ```
   (Or do the same via the GUI: Windows Defender Firewall with Advanced
   Security → Inbound Rules → New Rule → Port → TCP → specific ports.)

2. **The VM's network mode in Proxmox.** In the VM's Hardware settings,
   check which vNIC/bridge it's attached to:
   - **Bridged mode (`vmbr0`, the common default)** — the VM gets its own
     IP directly on your LAN (e.g. `192.168.1.50`), so your router only
     needs to forward straight to that IP. This is the simplest setup and
     what you want if you're not sure.
   - **NAT mode** — the VM sits behind Proxmox's own virtual NAT, which
     means you'd need to forward the port at the Proxmox host level *and*
     at your router — an extra hop most home setups don't need. Stick with
     bridged unless you have a specific reason for NAT.
   Give the VM a static IP (or a DHCP reservation on your router/Proxmox)
   so the port-forward mapping below doesn't break after a reboot.

3. **Your router**, if the Proxmox box is on a home network behind one:
   forward external TCP ports `43594` and `43494` to the VM's LAN IP on
   those same ports. The exact screen name varies by router (`Port
   Forwarding` / `Virtual Server` / `NAT Forwarding`), but the fields are
   always: external port, internal IP, internal port, protocol = TCP.
   If the Proxmox host has a public IP directly (a datacenter/VPS box
   rather than a home connection), skip this step — there's no router in
   the path.

### Verifying it's actually reachable

Once the server is running and the ports above are forwarded, confirm from
**outside your own network** (a phone on mobile data works well, since it
isn't on your LAN/Wi-Fi) — either point the game client at your public IP,
or use any online "TCP port checker" tool against your public IP and the
two ports. If it reports closed, work back through the three layers above
in order (Windows Firewall → Proxmox network mode → router) — that's also
the order most setups get stuck at.

## Customizing your server

Copy one of the existing `.conf` files (or edit `default.conf` directly)
and change at minimum:
- `server_name` / `server_name_welcome` — what players see.
- `server_port` / `ws_server_port` — change these if running multiple
  servers on one machine, or if your hosting provider requires specific
  ports.
- `db_name` — keep configs isolated if running more than one world.

Then run `run-server.bat <your-conf-name-without-.conf>`.

## Listing your server publicly

See `web/server-browser/README.md` — add an entry to `servers.json` there
and open a pull request once your server is reachable.

## Sharing accounts across multiple servers

By default each server's accounts live in its own local SQLite file, so an
account made on one server won't exist on another. If you're running more
than one RuneWake server and want one shared account database between them,
see `CENTRALIZED_DATABASE.md` for switching to a centralized MySQL/MariaDB
backend.

## Known limitations of this "simple" setup

- This runs the server in the foreground of the terminal window — closing
  that window stops the server. For unattended 24/7 hosting on Windows,
  consider running it via `NSSM` (Non-Sucking Service Manager, free) to
  install it as a proper Windows service, or via Task Scheduler with
  "run whether user is logged in or not."
- No automatic restart-on-crash. For that, a small wrapper loop (or NSSM's
  built-in restart-on-exit) covers it without needing anything paid.
- Java 8 is what's bundled (`Portable_Windows/`); the server's own
  `runserverzgc` Ant target exists for Java 17+ hosts with better GC — if
  you already have a newer JDK on the host, you can run
  `Portable_Windows\apache-ant-1.10.5\bin\ant.bat -f server\build.xml runserverzgc -DconfFile=<name>`
  directly instead of `run-server.bat`.
