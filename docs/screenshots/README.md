# Screenshots

Put the app's screenshots (PNG, JPG or WebP) into `docs/screenshots/raw/`.
They are placed in file-name order, so name them `01-home.png`, `02-player.png`
and so on. Different sizes are fine: every shot is scaled to the same height.

```bash
pip install pillow
python3 tools/make_screens.py
```

Up to five screenshots go in one row, more than that in two rows. The script
writes `w0y-screens.png` and a smaller `w0y-screens-small.webp` for the web
into this folder; the same input always gives the same output.
