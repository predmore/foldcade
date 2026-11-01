#!/usr/bin/env bash
# Physical Thor, API 33. Never installs over the user's app.foldcade.
set -euo pipefail
cd "$(dirname "$0")/../.."
adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
serial="${ANDROID_SERIAL:-c013eb4e}"
export ADB_MDNS=0
out="${FOLDCADE_PROFILE_OUTPUT:-/tmp/foldcade-profile-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$out"
./gradlew :app:assembleBenchmark :baselineprofile:assembleBenchmark --configuration-cache "$@"

announced=false
until mkdir /tmp/foldcade-thor.lock 2>/dev/null; do
    if [ "$announced" = false ]; then
        echo 'Waiting for the shared Thor lock; never removing another owner.'
        cat /tmp/foldcade-thor.lock/owner 2>/dev/null || true
        announced=true
    fi
    sleep 1
done
installed=false
remote=""
cleanup() {
    if [ "$installed" = true ]; then
        "$adb" -s "$serial" uninstall app.foldcade.benchmark > "$out/uninstall-app.txt" 2>&1 || true
        "$adb" -s "$serial" uninstall app.foldcade.baselineprofile > "$out/uninstall-harness.txt" 2>&1 || true
        "$adb" -s "$serial" shell am start --display 0 -n app.foldcade/.PrimaryHomeActivity > "$out/restore-home.txt" 2>&1 || true
        "$adb" -s "$serial" shell am start --display 4 -n app.foldcade/.CompanionHomeActivity >> "$out/restore-home.txt" 2>&1 || true
        "$adb" -s "$serial" shell dumpsys activity activities | grep topResumedActivity >> "$out/restore-home.txt" || true
    fi
    if [ -n "$remote" ]; then "$adb" -s "$serial" shell rm -rf "$remote" || true; fi
    "$adb" -s "$serial" shell svc power stayon false || true
    rm -f /tmp/foldcade-thor.lock/owner
    rmdir /tmp/foldcade-thor.lock
}
# Armed as soon as the lock is ours, so no failure can leave it held.
trap cleanup EXIT
printf 'Foldcade baseline profile generator, pid %s, %s\n' "$$" "$(date -Iseconds)" > /tmp/foldcade-thor.lock/owner
if "$adb" -s "$serial" shell pm path app.foldcade.benchmark | grep -q '^package:'; then
    echo 'app.foldcade.benchmark is already installed. Archive its data and remove it first; generation reinstalls this disposable package.' >&2
    exit 1
fi
"$adb" -s "$serial" shell svc power stayon true
"$adb" -s "$serial" shell input keyevent WAKEUP
installed=true
"$adb" -s "$serial" install app/build/outputs/apk/benchmark/app-benchmark.apk
"$adb" -s "$serial" install -r baselineprofile/build/outputs/apk/benchmark/baselineprofile-benchmark.apk
remote="/sdcard/Download/foldcade-profile-$(date +%Y%m%d-%H%M%S)"
"$adb" -s "$serial" shell am instrument -w -r \
    -e class app.foldcade.baselineprofile.HomeProfile \
    -e foldcade.thorShellWorkaround true \
    -e additionalTestOutputDir "$remote" \
    app.foldcade.baselineprofile/androidx.test.runner.AndroidJUnitRunner | tee "$out/generator.txt"
grep -q 'OK (1 test)' "$out/generator.txt"
"$adb" -s "$serial" pull "$remote/." "$out/"
profile="$out/HomeProfile_firstMinute-baseline-prof.txt"
test -s "$profile"
grep -q 'Lapp/foldcade/FoldcadeApp;' "$profile"
cp "$profile" app/src/main/baseline-prof.txt
echo "Profile copied to app/src/main/baseline-prof.txt. Evidence: $out"
