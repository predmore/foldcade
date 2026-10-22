#!/usr/bin/env python3
"""Validate references and reproducibly package a Foldcade resource theme. Python standard library only."""
import argparse
import json
import math
import re
import sys
from pathlib import Path, PurePosixPath
import zipfile

# Written by the engine's tests. A copy beside the theme, or --contract, works outside the repository.
DEFAULT_CONTRACT = Path(__file__).resolve().parents[2] / "themes" / "contract.json"
COLOR = re.compile(r"#[0-9A-Fa-f]{6}")
# A pack id, and the id `extends` names. ThemeZip.kt reads both this way.
PACK_ID = re.compile(r"[A-Za-z0-9._-]+")

APPLICATION_CODE_EXTENSIONS = {".java", ".kt", ".kts", ".class", ".dex", ".jar", ".so", ".apk", ".aar", ".dll", ".dylib", ".wasm", ".js", ".mjs", ".cjs", ".py"}


def normal_path(name):
    """True when Foldcade reads the archive path as written, as themeZipEntryName does. It drops an icon at any other."""
    parts = name.split("/")
    return bool(name) and "\\" not in name and "\0" not in name and all(part not in ("", ".", "..") for part in parts)


def assets(root: Path):
    manifest = json.loads((root / "theme.json").read_text())
    name, version = manifest.get("name"), manifest.get("version", 1)
    if not isinstance(name, str) or not name.strip() or isinstance(version, bool) or version not in (1, 2):
        raise ValueError("theme.json: expected a named theme with version 1 or 2")
    # Version 2 reads `id`, or the name without one. Foldcade can't read a manifest whose id it can't use.
    pack_id = manifest.get("id", name)
    if version == 2 and not (isinstance(pack_id, str) and PACK_ID.fullmatch(pack_id) and pack_id != "builtin"):
        raise ValueError("theme.json: id must be letters, digits, '.', '_' or '-', and a name with other characters needs an id")
    if "release" in manifest and not (isinstance(manifest["release"], str) and re.fullmatch(r"\d{1,6}(\.\d{1,6}){0,2}", manifest["release"])):
        raise ValueError("theme.json: release must be a version such as 1.2.0")
    host = manifest.get("host", 1)
    if isinstance(host, bool) or not isinstance(host, int) or host < 1:
        raise ValueError("theme.json: host must be a whole number from 1")
    if "source" in manifest:
        source = manifest["source"]
        github = source.get("github") if isinstance(source, dict) and set(source) == {"github"} else None
        if not (isinstance(github, str) and re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?/[A-Za-z0-9._-]{1,100}", github)) or github.split("/")[1] in (".", ".."):
            raise ValueError('theme.json: source must be {"github": "owner/repo"}')
    files = {"theme.json"}

    def add(name):
        if not isinstance(name, str) or not normal_path(name):
            raise ValueError(f"Asset path must be relative, with no '.', '..', '\\' or empty parts: {name!r}")
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
    for face, path in manifest.get("fonts", {}).items():
        if isinstance(path, str):
            add(path)
    if isinstance(manifest.get("preview"), str):
        add(manifest["preview"])

    def add_scene(name, chain=()):
        if name in chain:
            raise ValueError(f"{name}: scene inheritance loops")
        add(name)
        scene = json.loads((root / name).read_text())
        if scene.get("version") != 1 or not scene.get("id") or not scene.get("name"):
            raise ValueError(f"{name}: expected scene version 1, id and name")
        if "extends" in scene:
            add_scene(scene["extends"], chain + (name,))
        for layer in scene.get("layers", []):
            if isinstance(layer, str):
                if "extends" not in scene:
                    raise ValueError(f"{name}: layer {layer!r} reuses a base layer, but the scene extends nothing")
                continue
            if layer.get("type") not in ("shader", "disc", "line", "image"):
                raise ValueError(f"{name}: unknown layer type {layer.get('type')!r}")
            if layer.get("type") in ("shader", "image"):
                add(layer["file"])
            for texture in layer.get("inputs", {}).values():
                add(texture)

    for name in manifest.get("scenes", []):
        add_scene(name)
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


def install_problems(manifest):
    """What Add a theme refuses, and a bad `extends`, which the engine reports and ignores."""
    problems = []
    version, pack_id = manifest.get("version", 1), manifest.get("id", manifest.get("name"))
    if version != 2:
        problems.append("version: Add a theme installs version 2 packs only, with an id")
    elif "release" not in manifest:
        problems.append("release: Add a theme needs a release such as 1.0.0, so updates can be compared")
    if "extends" in manifest:
        parent = manifest["extends"]
        if version != 2 or not (isinstance(parent, str) and PACK_ID.fullmatch(parent)) or parent in (pack_id, "builtin"):
            problems.append("extends: must name another pack's id")
    return problems


def number_problem(value, token):
    """What is wrong with a number for this token's contract entry, or None."""
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        return "expects a number"
    if "min" in token and value < token["min"]:
        return f"must be at least {token['min']}"
    if "above" in token and value <= token["above"]:
        return f"must be above {token['above']}"
    if "max" in token and value > token["max"]:
        return f"must be at most {token['max']}"
    if "below" in token and value >= token["below"]:
        return f"must be below {token['below']}"
    if token.get("whole") and value != int(value):
        return "must be a whole number"
    return None


def value_problem(value, token):
    kind = token["kind"]
    if kind == "color":
        if value is None:
            return None if token.get("nullable") else "expects a #RRGGBB colour"
        return None if isinstance(value, str) and COLOR.fullmatch(value) else "expects a #RRGGBB colour"
    if kind == "curve":
        if not (isinstance(value, list) and len(value) == 4 and all(isinstance(v, (int, float)) and not isinstance(v, bool) for v in value)):
            return "expects [x1, y1, x2, y2]"
        return None if 0 <= value[0] <= 1 and 0 <= value[2] <= 1 else "x1 and x2 must be 0 to 1"
    return number_problem(value, token)


def check_contract(manifest, contract):
    """Problems the engine would report while loading this manifest, by manifest path."""
    problems = []
    tokens, renamed, retired = contract["tokens"], contract["renamedTokens"], set(contract["retiredTokens"])
    style = manifest.get("style", {})
    if not isinstance(style, dict):
        return ["style: expects an object"]
    groups = {name.split(".")[0] for name in list(tokens) + list(renamed) + list(retired)}
    for group, values in style.items():
        if group not in groups or not isinstance(values, dict):
            problems.append(f"style.{group}: unknown token group")
            continue
        for name, value in values.items():
            token = f"{group}.{name}"
            if token in renamed:
                problems.append(f"style.{token}: renamed to {renamed[token]}; the old name still works")
                token = renamed[token]
            elif token in retired:
                problems.append(f"style.{token}: retired, and does nothing")
                continue
            if token not in tokens:
                problems.append(f"style.{token}: unknown token")
                continue
            problem = value_problem(value, tokens[token])
            if problem:
                problems.append(f"style.{token}: {problem}; Afterglow's value is used")
    slots, closed = set(contract["slots"]), set(contract["closedSlotFamilies"])
    renamed_slots, retired_slots = contract["renamedSlots"], set(contract["retiredSlots"])
    for slot in manifest.get("icons", {}):
        if slot in renamed_slots:
            problems.append(f"icons.{slot}: renamed to {renamed_slots[slot]}; the old name still works")
        elif slot in retired_slots:
            problems.append(f"icons.{slot}: retired, and the host no longer draws it")
        elif slot.split(".")[0] in closed and slot not in slots:
            problems.append(f"icons.{slot}: unknown icon slot")
    for face in manifest.get("fonts", {}):
        if face not in contract["fonts"]:
            problems.append(f"fonts.{face}: unknown font face; use one of {', '.join(contract['fonts'])}")
    for key in manifest:
        if key not in contract["manifest"] and key != "$schema":
            problems.append(f"{key}: not a manifest field this Foldcade reads")
    host = manifest.get("host", 1)
    if isinstance(host, int) and host > contract["host"]:
        problems.append(f"host: {host} needs a newer Foldcade than this contract ({contract['host']})")
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--contract", type=Path, help="themes/contract.json from the Foldcade the pack is for")
    parser.add_argument("--strict", action="store_true", help="fail on anything the engine would report, or Add a theme would refuse")
    parser.add_argument("--bundled", action="store_true", help="a pack Foldcade ships, which is never added from Settings")
    args = parser.parse_args()
    try:
        manifest, files = assets(args.directory)
        problems = [] if args.bundled else install_problems(manifest)
        contract_path = args.contract or (args.directory / "contract.json" if (args.directory / "contract.json").is_file() else DEFAULT_CONTRACT)
        if contract_path.is_file():
            problems += check_contract(manifest, json.loads(contract_path.read_text()))
        for problem in problems:
            print(f"theme.json:{problem}", file=sys.stderr)
        if problems and args.strict:
            raise ValueError(f"{len(problems)} problem(s)" + (f" against {contract_path}" if contract_path.is_file() else ""))
        if not contract_path.is_file():
            if args.strict:
                raise ValueError(f"No contract at {contract_path}; pass --contract")
            print(f"No contract at {contract_path}; style tokens and icon slots were not checked.", file=sys.stderr)
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
