# Free hosting a RuneWake server

GitHub Pages hosts the RuneWake landing page and the static server-browser
listing. It does **not** run the Java game server. GitHub Pages is a static
site service for HTML, CSS, and JavaScript; it cannot keep a TCP game server
alive.

The two public web locations are separate pages in the same Pages site:

- <https://theantipopau.github.io/runewake/> — project landing page.
- <https://theantipopau.github.io/runewake/server-browser/> — static server
  browser. It reads each listed server's HTTPS `/status` endpoint from the
  visitor's browser; it is not a game-server host.

## What a public RuneWake server needs

The default configuration exposes two TCP ports:

- `43594` — the game protocol.
- `43494` — WebSocket/status support when `want_feature_websockets: true`.

The host must provide a continuously running JVM, persistent storage for the
SQLite database, inbound TCP reachability, and enough memory for the server.
A free web host, GitHub Pages, a GitHub Actions job, or a static Cloudflare
Page is not sufficient for the game process.

## Best free option: Oracle Cloud Always Free

Oracle's current documentation lists Always Free Ampere A1 capacity of up to
2 OCPUs and 12 GB of memory per tenancy, with a home-region requirement and
account verification requirements. That is a much better fit than a free
static-web host, although capacity is not guaranteed and Oracle may reclaim
resources that exceed the current allowance.

Suggested setup:

1. Create an OCI account and choose the home region carefully.
2. Create an Ubuntu Ampere A1 instance within the Always Free allowance.
3. Install a Java 8 or Java 17 JDK and the repository's server dependencies.
4. Copy the `RuneWake-0.1.0` bundle or clone `develop` to the VM.
5. Open TCP `43594` and `43494` in the OCI security list and the VM firewall.
6. Run the server under a process supervisor such as `systemd`, `screen`, or
   `tmux`, and back up `server/inc/sqlite/`.
7. For the server browser, expose the status endpoint over HTTPS with a
   Cloudflare Tunnel or configure a real domain/certificate.

Oracle's official free-tier terms and limits can change. Check the current
[Oracle Free Tier documentation](https://docs.oracle.com/iaas/Content/FreeTier/freetier.htm)
before depending on it for a public service.

## Lower-cost but not reliably free: Google Cloud

Google's Free Tier documentation includes a small `e2-micro` Compute Engine
allowance, but a 1 GB shared-core VM is a tight fit for a Java game server and
requires a billing account, regional restrictions, disk, network, and logging
budgets. It can be useful for testing or a very small world; it should not be
treated as a guaranteed free production host. Confirm the current
[Google Cloud free features](https://docs.cloud.google.com/free/docs/free-cloud-features)
and set billing alerts before deploying.

## Free ways to expose a home server

If you already own a computer, NAS, Raspberry Pi, or home server, the server
can run locally for free:

- forward TCP `43594` and `43494` from the router;
- allow the ports through the host firewall; and
- use a Cloudflare Tunnel for the HTTPS status endpoint required by the hosted
  server browser.

A tunnel does not replace the public game port. Players still need a reachable
TCP route for `43594`; the tunnel is primarily useful for the JSON status page
when a direct HTTPS status URL is not available.

## What RuneWake can provide next

The repository can add a systemd unit, an Oracle/GCP provisioning guide, a
Docker image, or a one-command cloud bootstrap. Those should be separate,
provider-specific deployment files rather than hidden assumptions in the game
server. They should also include backups, restart policy, resource limits, and
a clear warning that public server operation is the operator's responsibility.
