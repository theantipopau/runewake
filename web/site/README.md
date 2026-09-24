# RuneWake GitHub landing page

This directory contains the dark-fantasy RuneWake project landing page. It is
intentionally static: no build step, framework, or server-side code is needed.

## Preview locally

From the repository root, serve the page with any static HTTP server:

```sh
python -m http.server 8000 --directory web/site
```

Then open <http://localhost:8000/>. The `../server-browser/` link is resolved
from the `web/site` directory and works when the whole `web/` tree is served
from the repository root, which is how the GitHub Pages deployment is laid
out.

## GitHub Pages

The `gh-pages` branch is the published site branch. The page can be copied
there when the site layout changes, or served directly from the `web/site`
directory if the repository's Pages configuration is changed to use the
`develop` branch and `/web/site` as its source. Keep the source page in this
directory so the repository remains the single source of truth.

## Assets

- `assets/logo.webp` — optimised copy of the owner-supplied
  `assets/runewakelogo.png` source artwork.
- `assets/login.webp` — reserved optimised copy of the owner-supplied login
  artwork for a future login-showcase section.
- `assets/favicon-512.png` — square, smooth-filtered web icon derived from the
  owner-supplied icon source.
- `assets/frame-0120.jpg` and `assets/frame-0221.jpg` — reduced, progressive
  JPEG copies of frames from the local gameplay recording used as project
  screenshots. They are pre-fix captures and are labelled as build-in-motion
  examples rather than current visual test evidence.

The original PNG sources remain in the repository's top-level `assets/`
directory. No commercial RuneScape assets were copied into the page. See
`docs/RUNEWAKE_ASSET_INVENTORY.md` for provenance and asset rules.

## Design direction

The palette mirrors the game-side premium theme: ink-black backgrounds,
bronze borders, rune-blue accents, parchment text, and a NOP-style green
status signal. The page uses CSS custom properties, responsive layout,
reduced-size modern WebP/JPEG assets, and no remote font or icon dependency so
it remains readable on a slow connection and in a static GitHub Pages deploy.
