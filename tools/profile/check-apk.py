#!/usr/bin/env python3
"""Check the release artifact and AGP's merged rules, after assembleRelease."""
import pathlib
import sys
import zipfile

root = pathlib.Path(__file__).resolve().parents[2]
apk = pathlib.Path(sys.argv[1])
merged = root / "app/build/intermediates/merged_art_profile/release/mergeReleaseArtProfile/baseline-prof.txt"
rules = merged.read_text().splitlines()
for name in ("FoldcadeApp", "PrimaryHomeActivity", "CompanionHomeActivity", "ui/PanelScreenKt"):
    assert any(f"Lapp/foldcade/{name};" in rule for rule in rules), f"Missing app profile: {name}"
with zipfile.ZipFile(apk) as archive:
    profile = archive.read("assets/dexopt/baseline.prof")
    metadata = archive.read("assets/dexopt/baseline.profm")
# The previous library-only profile was 11,553 bytes. Check the human-readable app
# rules too: size alone could be satisfied by a future library dependency update.
assert profile.startswith(b"pro\x00") and len(profile) > 16_000, "App profile missing/truncated"
assert metadata.startswith(b"prm\x00"), "Profile metadata missing/truncated"
print(f"App baseline profile packaged: {len(profile):,} bytes; {len(rules):,} merged rules")
