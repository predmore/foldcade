#!/usr/bin/env python3
"""Validate references and reproducibly package a Foldcade resource theme. Python standard library only."""
import argparse
import json
from pathlib import Path, PurePosixPath
import zipfile

APPLICATION_CODE_EXTENSIONS = {".java", ".kt", ".kts", ".class", ".dex", ".jar", ".so", ".apk", ".aar", ".dll", ".dylib", ".wasm", ".js", ".mjs", ".cjs", ".py"}


def assets(root: Path):
    manifest = json.loads((root / "theme.json").read_text())
    if not manifest.get("name") or manifest.get("version", 1) not in (1, 2):
        raise ValueError("theme.json: expected a named theme with version 1 or 2")
    files = {"theme.json"}

    def add(name):
        path = PurePosixPath(name)
        if path.suffix.lower() in APPLICATION_CODE_EXTENSIONS:
            raise ValueError(f"Executable application code is not a theme asset: {name}")
        if path.is_absolute() or ".." in path.parts or not (root / path).is_file():
            raise ValueError(f"Missing or invalid asset: {name}")
        if (root / path).resolve().is_relative_to(root.resolve()) is False:
            raise ValueError(f"Asset leaves the pack: {name}")
        files.add(name)

    for icon in manifest.get("icons", {}).values():
        add(icon["file"])
    for name in manifest.get("scenes", []):
        add(name)
        scene = json.loads((root / name).read_text())
        if scene.get("version") != 1 or not scene.get("id") or not scene.get("name"):
            raise ValueError(f"{name}: expected scene version 1, id and name")
        for layer in scene.get("layers", []):
            if layer.get("type") not in ("shader", "disc", "line", "image"):
                raise ValueError(f"{name}: unknown layer type {layer.get('type')!r}")
            if layer.get("type") in ("shader", "image"):
                add(layer["file"])
            for texture in layer.get("inputs", {}).values():
                add(texture)
    # Preserve v1 convention assets and their license notices.
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(root).as_posix()
        if path.suffix.lower() in APPLICATION_CODE_EXTENSIONS and path.name != "generate.py":
            raise ValueError(f"Executable application code is not a theme asset: {relative}")
        if path.suffix.lower() in (".png", ".webp", ".ogg", ".ttf", ".otf", ".agsl") or path.name.startswith(("LICENSE", "OFL-")):
            add(relative)
    return manifest, sorted(files)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        manifest, files = assets(args.directory)
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(args.output, "w") as archive:
                for name in files:
                    info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    archive.writestr(info, (args.directory / name).read_bytes())
        print(f"{manifest['name']}: {len(manifest.get('icons', {}))} icons, {len(manifest.get('scenes', []))} scenes, {len(files)} assets")
        print("Native expression and shader validation: use ThemePreviewActivity on the target device.")
    except (ValueError, KeyError, OSError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
