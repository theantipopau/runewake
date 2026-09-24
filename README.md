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

The `gh-pages` branch is the published site branch. From the repository root,
stage and validate the complete published tree without changing branches:

```sh
bash scripts/build_pages.sh build/pages
```

Then copy the generated `build/pages/` contents into a clean checkout of
`gh-pages`, review the diff, commit it, and push that branch normally. The
staging script rewrites the source page's `../server-browser/` links for the
published root and places the server browser under `/server-browser/`. It also
checks every local HTML asset/reference before the files are published.

The staging script never pushes and never switches branches, so it is safe to
run in CI or locally. Keep the source page in `web/site/` so the repository
remains the single source of truth. The current Pages deployment is
<https://theantipopau.github.io/runewake/>.

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
