#!/usr/bin/env python3
"""Fail the build unless a rendered track matches its checks.

Every track masters to about -12.0 LUFS integrated, so switching tracks keeps
the level. Each must also have its true peak at or below -1.0 dBTP, 0 clipped
samples, no dropouts, a seam identical to the continuous render, and the loop
length in its approved tracks/<id>/checks.json (Lanternlight: 80.000 s).
"""
import argparse
import json
import sys
from pathlib import Path

SR = 48000


def fail(message):
    print("home music check failed: " + message, file=sys.stderr)
    sys.exit(1)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--track", required=True)
    args = parser.parse_args()
    checks = Path("tracks") / args.track / "out" / "a" / "checks.json"
    if not checks.is_file():
        fail("missing " + str(checks) + ". process.py did not write checks.")
    res = json.loads(checks.read_text())
    approved = Path("tracks") / args.track / "checks.json"
    if not approved.is_file():
        fail("missing " + str(approved) + ". Commit the approved measurement for this track.")
    approved_length = float(json.loads(approved.read_text())["length_s"])
    length_samples = round(approved_length * SR)
    errors = []

    def need(condition, message):
        if not condition:
            errors.append(message)

    need(res.get("variant") == "a", "variant is %r, expected a" % res.get("variant"))
    length = float(res.get("length_s", -1))
    need(abs(length - approved_length) < 1e-3, "length_s %s, expected %.3f" % (length, approved_length))
    samples = int(res.get("ogg_decoded_samples", -1))
    need(samples == length_samples, "ogg_decoded_samples %s, expected %s" % (samples, length_samples))
    lufs = float(res.get("integrated_lufs", 0))
    need(abs(lufs - (-12.0)) <= 0.5, "integrated_lufs %.3f, expected -12.0 within 0.5" % lufs)
    peak = float(res.get("sample_peak_dbfs", 0))
    need(peak <= -1.0, "sample_peak_dbfs %.3f is above -1 dBFS" % peak)
    true_peak = float(res.get("true_peak_dbfs_est", 0))
    need(true_peak <= -1.0, "true_peak_dbfs_est %.3f is above -1 dBTP" % true_peak)
    ogg_true_peak = float(res.get("ogg_true_peak_dbtp", 0))
    need(ogg_true_peak <= -1.0, "ogg_true_peak_dbtp %.3f is above -1 dBTP" % ogg_true_peak)
    clipped = int(res.get("clipped_samples", -1))
    need(clipped == 0, "clipped_samples %s, expected 0" % clipped)
    dropouts = int(res.get("windows_below_minus50db", -1))
    need(dropouts == 0, "windows_below_minus50db %s, expected 0" % dropouts)
    seam = float(res.get("seam_vs_continuous_max_abs_diff", 1))
    need(seam < 1e-4, "seam_vs_continuous_max_abs_diff %s is not identical to the continuous render" % seam)
    residual = float(res.get("next_copy_residual_db_rel", 0))
    need(residual < -60.0, "next_copy_residual_db_rel %.2f, expected a matching pair of copies" % residual)

    if errors:
        for message in errors:
            print("home music check failed: " + message, file=sys.stderr)
        sys.exit(1)
    print(
        "home music checks passed: %s, %.3f LUFS, peak %.3f dBFS, true peak %.3f dBTP, %d samples"
        % (args.track, lufs, peak, true_peak, samples)
    )


if __name__ == "__main__":
    main()
