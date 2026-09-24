# RuneWake Server Browser

A static, backend-free webpage that lists known RuneWake servers, their
live player counts and ping, and a **Play** button per server — similar in
spirit to the modern OSRS site's world list.

## How it works

- `servers.json` is a plain list of servers. Each entry needs a `statusUrl`
  pointing at that server's status endpoint.
- `index.html` fetches `servers.json`, then fetches each server's
  `statusUrl` directly from the visitor's browser — there is no backend for
  this page to run; static hosting (GitHub Pages, Cloudflare Pages, or just
  opening the file locally) is enough.
- **Ping** is measured client-side as the round-trip time of that same
  status fetch. It's a reasonable reachability/latency proxy, but note it's
  measured against `ws_server_port`, not the exact port/protocol the game
  client's TCP connection uses (`server_port`) — treat it as approximate.
- **Play** button: a browser page can't launch a native desktop client or
  write into its folder directly (sandboxing), so there's no true single-
  click connect. Instead, clicking **Play** downloads a small
  `connect-<server>.bat` that writes the server's host/port into
  `Client_Base/Cache/ip.txt` and `Cache/port.txt` (the same override files
  the client already reads — see `Config.java`'s `SERVER_IP`/`SERVER_PORT`)
  and then runs `run-client.bat`. The user drops the downloaded file next to
  `run-client.bat` and runs it — effectively "download once, click to play"
  from then on for that server.

## HTTPS requirement (read this before adding your server)

This page is served over HTTPS on GitHub Pages, and browsers block an HTTPS
page from fetching a plain `http://` URL ("mixed content") — so a
`statusUrl` of `http://your-ip:43494/status` will silently fail to load for
every visitor, even though it works fine with `curl` or a plain HTTP page.
Your server's status endpoint needs to be reachable over **HTTPS** for it to
actually show up here. The easiest free way to get that without a domain or
certificate to manage is a Cloudflare Tunnel — see
`server/SIMPLE_HOSTING.md`'s "Free HTTPS via Cloudflare Tunnel" section for
the exact steps. Once you have an `https://` URL for your status endpoint,
use that as `statusUrl` below (`connectHost`/`connectPort` stay as your real
server IP/port — those aren't fetched by the browser, just displayed/used
for the downloaded connect script).

## Server-side requirement

Each listed server needs to be running with `want_feature_websockets: true`
(the default — see `server/default.conf`). The status endpoint is served on
the **websocket port** (`ws_server_port` in the server's `.conf` file), not
the main game port (`server_port`) — the main game port speaks the raw RSC
protocol only. For the default config that's:

```
http://<server-ip>:<ws_server_port>/status
```

which returns JSON like:

```json
{"serverName": "RuneWake", "players": 12, "uptimeMillis": 3600000}
```

The endpoint sends `Access-Control-Allow-Origin: *` so it can be fetched
from a page hosted anywhere.

## Adding your server

Add an entry to `servers.json`:

```json
{
  "name": "My RuneWake Server",
  "statusUrl": "http://your-server-ip:43494/status",
  "connectHost": "your-server-ip",
  "connectPort": 43594
}
```

Then open a pull request. `connectHost`/`connectPort` are just displayed —
they're what a player would enter in `Client_Base/Cache/port.txt`
(or the client's server-select config) to actually connect.

## Known limitation

If your server sits behind a firewall/NAT/reverse proxy, the status port
needs to be reachable from the public internet for this to work — same
requirement as the main game port already has today.
