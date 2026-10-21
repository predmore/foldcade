# Sample theme packs

These packs show what a third-party theme looks like. They are also a compatibility canary: `SampleThemesTest` loads each one on every build and fails if Foldcade reports anything about it. A token or slot renamed without an alias would break them, so it breaks the build first.

- `daylight/` is a light pack: paper colours, its own ring and highlight colours, `base` scales for corners, glow and timing, one icon, and a scene that reads `hour` and the focused item's accent.
- `daylight-square/` extends Daylight and squares every corner. It sets two tokens and inherits the rest of Daylight, which inherits from Afterglow.

Package one with the theme tool, then add it on the device from Settings › Look › Themes › Add a theme:

```sh
python3 tools/themes/pack.py samples/themes/daylight --strict --output /tmp/daylight.zip
```

The guide is [docs/themes.md](../../docs/themes.md). The samples are MIT licensed, like the rest of Foldcade's original work.
