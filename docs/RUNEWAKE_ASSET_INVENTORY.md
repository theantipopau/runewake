# RuneWake Asset Inventory

Scope: player-facing and launcher artwork, from `assets/`, client bundled
resources, Android resources, and the static GitHub landing page. Format/size
read directly from file headers this pass (2026-09-24). Provenance: items in
`assets/` were supplied by the project owner (2026-07-06, ROADMAP 7b) —
original RuneWake artwork, licence same as repo (AGPLv3) unless stated
otherwise in future additions. Gameplay frames in `web/site/assets/` were
extracted from the project owner's local gameplay recording for documentation
purposes; they are not new game assets and are not evidence of current visual
verification.

## Owned RuneWake artwork

| Path | Format | Dimensions | Used for | Filtering | Stretched? | Priority |
|---|---|---|---|---|---|---|
| `assets/runewakelogo.png` | PNG | 1536×1024 | README header, source of derived assets | n/a (source) | n/a | — |
| `assets/login.png` | PNG | 1717×916 | source of client login background | n/a (source) | n/a | — |
| `assets/icon.png` | PNG | 1536×1024 | source of window icon | n/a (source) | n/a | — |
| `Client_Base/src/res/login.png` | PNG | 1717×916 | login-screen backdrop (desktop; Android falls back to 3D scene) | bilinear via Java2D scale at load | yes, to window | low (works) |
| `Client_Base/src/res/icon.png` | PNG | 128×128 | window/taskbar icon (`Window.setIconImage`) | OS-level | to 16–48px | low (128² is adequate) |
| `web/site/assets/logo.webp` | WebP | 900×600 | static landing-page logo source derivative | smooth LANCZOS resize | no | low |
| `web/site/assets/login.webp` | WebP | 1200×640 | reserved landing-page login showcase derivative | smooth LANCZOS resize | no | low |
| `web/site/assets/favicon-512.png` | PNG | 512×512 | static landing-page favicon | smooth LANCZOS crop/resize | square crop | low |

Note: `assets/icon.png` (1536×1024, landscape) is the *source*; the shipped
`res/icon.png` is a square 128×128 crop made on 2026-07-06. Known cosmetic
flag from that session: the logo's solid orange background shows as a square
backdrop at small taskbar sizes. The web favicon is a larger square crop for
browser display and is not a replacement for the runtime client icon.

## Landing-page captures

| Path | Format | Dimensions | Used for | Filtering | Provenance/status |
|---|---|---|---|---|---|
| `web/site/assets/frame-0120.jpg` | progressive JPEG | 1200×713 | landing-page hero gameplay image | LANCZOS reduction | extracted from the owner's 2026-09-24 gameplay recording; pre-fix capture |
| `web/site/assets/frame-0221.jpg` | progressive JPEG | 1200×713 | landing-page custom-interface showcase | LANCZOS reduction | extracted from the owner's 2026-09-24 gameplay recording; pre-fix capture |

The selected captures are labelled as a build-in-motion/documentation view on
the landing page. The recording predates the latest side-panel backdrop fix;
the landing-page image is therefore not a current visual test result. Human
verification remains tracked in `docs/RUNEWAKE_VISUAL_TEST_MATRIX.md`.

## Inherited (game-cache / sprite) artwork

- `Client_Base/Cache/audio/*.wav` (38 files) — 8000Hz mono (one 8-bit),
  batch-normalised/resampled to 22050Hz 2026-07-06. Era-appropriate SFX;
  replacement needs new recordings (asset work).
- 3D world textures — 64×64/128×128 palette-indexed sprites from the game
  cache; NOT RuneWake-owned original art (inherited RSC-derived assets,
  provenance = upstream project). Improvement is code-side only
  (`boxBlurTexture` pre-pass); replacement art not available.
- Item/NPC/UI sprite sets — same provenance. Intentional pixel art:
  nearest-neighbour must stay (rule in the continuation brief).
- `PC_Launcher` artwork — inherited launcher frames/labels; RuneWake
  identity so far only via window title + update host (ROADMAP 7j/7i).
  Launcher visual identity = open priority.
- Android resources — icons/labels inherited; app label rebranded this
  pass. Launcher icon artwork still inherited = open priority.

## Rules in force (from the continuation brief, enforced)

- Pixel-art assets: nearest-neighbour only. Modern/smooth art (fonts,
  RuneWake logo derivatives, website art): smooth filtering.
- No scraping/copying commercial RuneScape assets. New art must be original
  or properly licensed, with provenance recorded here.
- Keep source-quality artwork (this repo's `assets/`) separate from
  runtime-optimised copies (`Client_Base/src/res/`) and static-web copies
  (`web/site/assets/`).
- When no new art exists, implement the correct asset slot + graceful
  fallback (existing precedent: `ClientPort.loadScaledImageSprite` returning
  null → 3D-scene login fallback) rather than a poor placeholder.
- Do not use a documentation capture as proof that a current UI change is
  visually correct; record captures as pre-fix/unverified until a human
  re-test is completed.

## Slots with fallback behaviour (verified in code)

| Slot | Loader | Fallback |
|---|---|---|
| Login background | `ClientPort.loadScaledImageSprite("login.png", …)` | rotating 3D-scene viewport (`renderLoginScreenViewports`) |
| Window icon | `setIconImage(Toolkit…)` | platform default icon |
| Fonts | `ClientPort.regenerateFonts()` (modern, flag-gated) | original bitmap `Fonts.fontData` |
| Landing-page screenshots | static `<img>` element | alt text and surrounding copy; no build-time image processing required |
