#!/usr/bin/env python3
"""Fail the build unless variant A's checks.json matches the reference render.

The reference is music/out/a/checks.json from the variant A sources:
-16.0 LUFS, peak about -3.45 dBFS, 0 clipped samples, an 80.000 s loop,
3,840,000 decoded samples, no dropouts, and a seam identical to the
continuous render. CI renders only variant A.
"""
import json
import sys
from pathlib import Path

CHECKS = Path("out/a/checks.json")
LENGTH_SAMPLES = 80 * 48000


def fail(message):
    print("home music check failed: " + message, file=sys.stderr)
    sys.exit(1)


def main():
    if not CHECKS.is_file():
        fail("missing " + str(CHECKS) + ". process.py did not write checks.")
    res = json.loads(CHECKS.read_text())
    errors = []

    def need(condition, message):
        if not condition:
            errors.append(message)

    need(res.get("variant") == "a", "variant is %r, expected a" % res.get("variant"))
    length = float(res.get("length_s", -1))
    need(abs(length - 80.0) < 1e-3, "length_s %s, expected 80.000" % length)
    samples = int(res.get("ogg_decoded_samples", -1))
    need(samples == LENGTH_SAMPLES, "ogg_decoded_samples %s, expected %s" % (samples, LENGTH_SAMPLES))
    lufs = float(res.get("integrated_lufs", 0))
    need(abs(lufs - (-16.0)) <= 0.5, "integrated_lufs %.3f, expected -16.0 within 0.5" % lufs)
    peak = float(res.get("sample_peak_dbfs", 0))
    need(peak <= -3.0, "sample_peak_dbfs %.3f is above -3 dBFS" % peak)
    need(abs(peak - (-3.4545)) <= 0.5, "sample_peak_dbfs %.3f, expected around -3.4 dBFS" % peak)
    clipped = int(res.get("clipped_samples", -1))
    need(clipped == 0, "clipped_samples %s, expected 0" % clipped)
    dropouts = int(res.get("windows_below_minus50db", -1))
    need(dropouts == 0, "windows_below_minus50db %s, expected 0" % dropouts)
    seam = float(res.get("seam_vs_continuous_max_abs_diff", 1))
    need(seam < 1e-4, "seam_vs_continuous_max_abs_diff %s is not identical to the continuous render" % seam)
    residual = float(res.get("copy2_vs_copy3_residual_db_rel", 0))
    need(residual < -60.0, "copy2_vs_copy3_residual_db_rel %.2f, expected a matching pair of copies" % residual)

    if errors:
        for message in errors:
            print("home music check failed: " + message, file=sys.stderr)
        sys.exit(1)
    print(
        "home music checks passed: variant a, %.3f LUFS, peak %.3f dBFS, %d samples"
        % (lufs, peak, samples)
    )


if __name__ == "__main__":
    main()
