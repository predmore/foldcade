#!/usr/bin/env bash
# Thor-sized emulator, not a Thor pass.
# One API 33 phone AVD. The primary panel is the Thor top screen.
# The added display is the Thor bottom screen. This is not a Thor skin:
# no hinge, refresh rate, OLED mode, or keycode is set on the AVD.
#
# Top: 1920×1080 at 367 dpi. Published 16:9 grid; 367 PPI matches the 6" diagonal.
# Bottom: 1240×1080. 419 dpi is derived from the 3.92" diagonal (about 419.5).
# comparehandhelds also publishes 335 PPI for the bottom panel. That figure does
# not match the diagonal, so it is not used.
#
# Run 37879382191 sat in `adb wait-for-device` until the job timeout.
# An offline emulator never reaches state device, so that call has no end.
# Run 37885246512 booted, then the console returned
# "KO: setMultiDisplay not supported". This script does not call that console.
# It still tries hw.display1, -qt-hide-window, then `cmd display` so a
# presentation display can appear. Success is not which of those ran.
# Success is a FLAG_PRESENTATION display whose real size is 1240×1080.
# screencap needs the SurfaceFlinger id of that display. cmd display id 2
# on the last run was the panel; --display-id only lists the physical screen.
# A crash-consent dialog and a downloadable snapshot both block the guest
# before adbd. Cold-boot with the adb server already up.
set -euo pipefail

: "${ANDROID_HOME:?}"
: "${ANDROID_AVD_HOME:?}"

export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

out="${1:-screenshots}"
mkdir -p "$out"
rm -f "$out"/*.png "$out"/display-ids.txt "$out"/ui-last.xml "$out"/emulator.log \
  "$out"/logcat.txt "$out"/failure.txt "$out"/bottom-display.txt \
  "$out"/cmd-display-get.txt "$out"/surfaceflinger-displays.txt

# Top panel plus bottom panel, side by side, with margin. 1920+1240 wide, 1080 tall.
xvfb_geometry="3360x1280x24"
top_width=1920
top_height=1080
top_density=367
bottom_width=1240
bottom_height=1080
bottom_density=419

copy_logs() {
  cp /tmp/emulator.log "$out/emulator.log" 2>/dev/null || true
  cp /tmp/xvfb.log "$out/xvfb.log" 2>/dev/null || true
}

# Every adb invocation is capped. An offline emulator never reaches state
# device, and an uncapped wait-for-device sits until the job timeout.
adb_do() {
  timeout 30 adb "$@"
}

fail() {
  local step="$1"
  echo "::error::Stalled at: ${step}"
  printf 'step=%s\n' "$step" >"$out/failure.txt"
  echo "----- emulator.log (tail) -----"
  tail -n 150 /tmp/emulator.log 2>/dev/null || true
  echo "----- logcat (tail) -----"
  timeout 20 adb logcat -d -t 200 >"$out/logcat.txt" 2>/dev/null || true
  tail -n 80 "$out/logcat.txt" 2>/dev/null || true
  copy_logs
  exit 1
}

cleanup() {
  local status=$?
  if [ "$status" -ne 0 ] && [ ! -s "$out/failure.txt" ]; then
    echo "step=exit ${status}" >"$out/failure.txt"
    echo "----- emulator.log (tail) -----"
    tail -n 150 /tmp/emulator.log 2>/dev/null || true
    timeout 20 adb logcat -d -t 200 >"$out/logcat.txt" 2>/dev/null || true
  fi
  timeout 15 adb emu kill >/dev/null 2>&1 || true
  if [ -n "${emu_pid:-}" ]; then
    kill "$emu_pid" >/dev/null 2>&1 || true
    wait "$emu_pid" >/dev/null 2>&1 || true
  fi
  if [ -n "${log_tail_pid:-}" ]; then
    kill "$log_tail_pid" >/dev/null 2>&1 || true
  fi
  if [ -n "${xvfb_pid:-}" ]; then
    kill "$xvfb_pid" >/dev/null 2>&1 || true
  fi
  copy_logs
}
trap cleanup EXIT

if [ ! -e /dev/kvm ]; then
  echo "::error::KVM is required to boot the API 33 emulator."
  exit 1
fi
sudo chmod 666 /dev/kvm || true

apk="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -s "$apk" ]; then
  echo "::error::Debug APK not found at $apk"
  exit 1
fi
aapt="${ANDROID_HOME}/build-tools/37.0.0/aapt"
app_id="$("$aapt" dump badging "$apk" | sed -n "s/package: name='\([^']*\)'.*/\1/p" | head -1)"
if [ -z "$app_id" ]; then
  echo "::error::Debug APK has no package name."
  exit 1
fi

system_image="system-images;android-33;google_apis;x86_64"
avd="foldcade_api33"
device="pixel_6"
if ! avdmanager list device | grep -q "\"${device}\""; then
  device="medium_phone"
fi
case "$device" in
  *fold*|*Fold*|*resizable*|*Resizable*)
    echo "::error::Refusing fold or resizable profile '$device'."
    exit 1
    ;;
esac

echo no | avdmanager create avd --name "$avd" --package "$system_image" --device "$device" --force
cfg="${ANDROID_AVD_HOME}/${avd}.avd/config.ini"
set_cfg() {
  local key="$1" value="$2"
  if grep -q "^${key}[[:space:]]*=" "$cfg"; then
    sed -i "s|^${key}[[:space:]]*=.*|${key}=${value}|" "$cfg"
  else
    printf '%s=%s\n' "$key" "$value" >>"$cfg"
  fi
}
# Custom lcd size for the top panel. skin.dynamic follows those values.
# This is not a Thor skin. avdmanager writes "key = value"; match the spaces
# or the default 1080×2400 skin wins.
set_cfg hw.lcd.width "$top_width"
set_cfg hw.lcd.height "$top_height"
set_cfg hw.lcd.density "$top_density"
set_cfg hw.keyboard yes
set_cfg hw.gpu.enabled yes
set_cfg hw.gpu.mode swiftshader_indirect
set_cfg skin.name "${top_width}x${top_height}"
set_cfg skin.dynamic yes
set_cfg fastboot.forceColdBoot yes
set_cfg fastboot.forceFastBoot no
set_cfg firstboot.bootFromDownloadableSnapshot no
set_cfg firstboot.bootFromLocalSnapshot no
set_cfg firstboot.saveToLocalSnapshot no
# Bottom panel. 419 dpi is derived, not the conflicting 335 PPI.
# config.ini is the first attempt. The console command is not used.
set_cfg hw.display1.width "$bottom_width"
set_cfg hw.display1.height "$bottom_height"
set_cfg hw.display1.density "$bottom_density"
set_cfg hw.display1.xOffset "$top_width"
set_cfg hw.display1.yOffset 0
set_cfg hw.display1.flag 0

echo "step: framebuffer"
Xvfb :99 -screen 0 "$xvfb_geometry" >/tmp/xvfb.log 2>&1 &
xvfb_pid=$!
export DISPLAY=:99
# Run 37901128566 started the windowed emulator about 35ms after Xvfb was
# launched. Qt then aborted: could not connect to display :99. An X11
# handshake is the signal that the server accepts clients. Boot waits for
# that. This stays in the script so the workflow file is left alone.
if ! command -v python3 >/dev/null 2>&1; then
  fail "framebuffer: python3 is not installed"
fi
display_accepts_clients() {
  python3 - <<'PY'
import socket, struct, sys
s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
s.settimeout(0.5)
try:
    s.connect("/tmp/.X11-unix/X99")
    s.sendall(struct.pack("<BxHHHHxx", ord("l"), 11, 0, 0, 0))
    hdr = s.recv(8)
except OSError:
    sys.exit(1)
sys.exit(0 if hdr[:1] == b"\x01" else 1)
PY
}
framebuffer_deadline=$((SECONDS + 20))
while true; do
  if display_accepts_clients; then
    echo "step: framebuffer ready"
    break
  fi
  if ! kill -0 "$xvfb_pid" 2>/dev/null; then
    echo "----- xvfb.log -----"
    cat /tmp/xvfb.log 2>/dev/null || true
    fail "framebuffer: Xvfb exited before display :99 was up"
  fi
  if [ "$SECONDS" -ge "$framebuffer_deadline" ]; then
    echo "----- xvfb.log -----"
    cat /tmp/xvfb.log 2>/dev/null || true
    fail "framebuffer: display :99 did not accept connections"
  fi
  sleep 0.2
done
: >/tmp/emulator.log
tail -n +1 -F /tmp/emulator.log &
log_tail_pid=$!
timeout 30 adb start-server >/dev/null

stop_emulator() {
  timeout 15 adb emu kill >/dev/null 2>&1 || true
  if [ -n "${emu_pid:-}" ] && kill -0 "$emu_pid" 2>/dev/null; then
    kill "$emu_pid" >/dev/null 2>&1 || true
    wait "$emu_pid" >/dev/null 2>&1 || true
  fi
  emu_pid=""
  sleep 1
}

start_emulator() {
  local extra="${1:-}"
  echo "step: boot ${extra:-windowed}"
  # shellcheck disable=SC2086
  emulator -avd "$avd" \
    -no-snapshot \
    -no-audio -no-boot-anim -no-metrics \
    -accel on \
    -gpu swiftshader_indirect \
    -crash-report-mode never \
    -feature -DownloadableSnapshot \
    -memory 3072 \
    -port 5554 \
    ${extra} \
    </dev/null >>/tmp/emulator.log 2>&1 &
  emu_pid=$!
}

wait_for_boot() {
  local boot="" state="" deadline=$((SECONDS + 240))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if ! kill -0 "$emu_pid" 2>/dev/null; then
      fail "boot: emulator process exited"
    fi
    state="$(timeout 15 adb devices 2>/dev/null | awk 'NR>1 && $1 ~ /^emulator-/ { print $2; exit }' || true)"
    if [ "$state" = "device" ]; then
      boot="$(timeout 30 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
      if [ "$boot" = "1" ]; then
        echo "step: boot complete"
        return 0
      fi
    fi
    echo "step: boot adb=${state:-none} boot_completed=${boot:-0} elapsed=${SECONDS}s"
    copy_logs
    sleep 2
  done
  fail "boot: emulator did not finish booting (adb=${state:-none})"
}

presentation_logical=""
presentation_name=""

# Success is this display, not which creation attempt ran.
find_presentation_display() {
  local dump line
  presentation_logical=""
  presentation_name=""
  dump="$(adb_do shell cmd display get-displays 2>/dev/null | tr -d '\r' || true)"
  printf '%s\n' "$dump" >"$out/cmd-display-get.txt"
  while IFS= read -r line; do
    [[ "$line" == *"FLAG_PRESENTATION"* ]] || continue
    [[ "$line" =~ real[[:space:]]${bottom_width}[[:space:]]x[[:space:]]${bottom_height}([^0-9]|$) ]] || continue
    if [[ "$line" =~ Display\ id\ ([0-9]+) ]]; then
      presentation_logical="${BASH_REMATCH[1]}"
    else
      continue
    fi
    if [[ "$line" =~ DisplayInfo\{\"([^\"]+)\" ]]; then
      presentation_name="${BASH_REMATCH[1]}"
    fi
    return 0
  done <<<"$dump"
  return 1
}

bottom_ready() {
  find_presentation_display
}

poll_bottom() {
  local i
  for i in $(seq 1 15); do
    if bottom_ready; then
      return 0
    fi
    sleep 2
  done
  return 1
}

start_guest_service() {
  timeout 30 adb shell am broadcast \
    -a com.android.emulator.multidisplay.START \
    -n com.android.emulator.multidisplay/.MultiDisplayServiceReceiver \
    --user 0 || true
}

start_emulator ""
wait_for_boot
echo "step: presentation display"
start_guest_service
if ! poll_bottom; then
  echo "step: qt-hide-window"
  stop_emulator
  start_emulator "-qt-hide-window"
  wait_for_boot
  start_guest_service
  poll_bottom || true
fi
if [ -z "$presentation_logical" ]; then
  echo "step: cmd display"
  help_text="$(adb_do shell cmd display help 2>&1 || true)"
  printf '%s\n' "$help_text" >"$out/cmd-display-help.txt"
  if printf '%s\n' "$help_text" | grep -q 'create-virtual-display'; then
    adb_do shell cmd display create-virtual-display \
      --width "$bottom_width" --height "$bottom_height" --density "$bottom_density" || true
  else
    echo "cmd display has no create-virtual-display on this image. Using overlay_display_devices."
    adb_do shell settings put global overlay_display_devices \
      "${bottom_width}x${bottom_height}/${bottom_density}"
  fi
  poll_bottom || true
fi
if [ -z "$presentation_logical" ]; then
  echo "----- cmd display get-displays -----"
  cat "$out/cmd-display-get.txt" 2>/dev/null || true
  fail "second display: no FLAG_PRESENTATION display with real ${bottom_width}x${bottom_height}"
fi
{
  echo "logical_display_id=${presentation_logical}"
  echo "name=${presentation_name}"
  echo "flags=FLAG_PRESENTATION"
  echo "real=${bottom_width}x${bottom_height}"
} | tee "$out/bottom-display.txt"

# Setup and the home chooser are not tapped. Each of these steps is short.
# "Viewing full screen" is the system confirmation. Back does not clear it,
# and Got it is not tapped. The secure setting is what suppresses it, and it
# is set before launch, the same way setup is skipped.
# One Back if some other dialog is still up. A home key is not sent: while
# this presentation display is focused, that key opens the stock launcher.
adb_step() {
  timeout 10 adb "$@"
}

echo "step: skip setup"
adb_step shell settings put secure user_setup_complete 1
adb_step shell settings put global device_provisioned 1
adb_step shell input keyevent KEYCODE_WAKEUP || true
adb_step shell wm dismiss-keyguard || true
timeout 60 adb install -r "$apk"

component="${app_id}/app.foldcade.PrimaryHomeActivity"
# Run 37904476852: add-role-holder threw TimeoutException while the guest
# was still settling. Each wait is bounded. The command is tried three
# times with backoff, then the step fails.
wait_for_boot_completed() {
  local deadline=$((SECONDS + 30)) boot
  echo "step: boot_completed"
  while [ "$SECONDS" -lt "$deadline" ]; do
    boot="$(timeout 10 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [ "$boot" = "1" ]; then
      return 0
    fi
    sleep 2
  done
  fail "home role: sys.boot_completed did not become 1"
}

wait_for_service() {
  local name="$1"
  local deadline=$((SECONDS + 30)) status
  echo "step: service ${name}"
  while [ "$SECONDS" -lt "$deadline" ]; do
    status="$(timeout 10 adb shell service check "$name" 2>/dev/null | tr -d '\r' || true)"
    if [[ "$status" == *": found" ]]; then
      return 0
    fi
    sleep 2
  done
  fail "home role: service ${name} was not ready"
}

add_home_role() {
  local attempt delay
  for attempt in 1 2 3; do
    echo "step: home role attempt ${attempt}"
    if timeout 15 adb shell cmd role add-role-holder android.app.role.HOME "$app_id"; then
      return 0
    fi
    if [ "$attempt" -eq 3 ]; then
      break
    fi
    delay=$((attempt * 2))
    echo "step: home role backoff ${delay}s"
    sleep "$delay"
  done
  fail "home role: add-role-holder failed"
}

wait_for_boot_completed
wait_for_service package
wait_for_service role
add_home_role
# Before the activity hides the system bars. Back does not dismiss this notice.
echo "step: suppress full screen confirmation"
adb_step shell settings put secure immersive_mode_confirmations confirmed

echo "step: launch ${component}"
timeout 15 adb shell am start -W -n "$component"

# One Back when a dialog other than the system confirmation is still up.
# Do not tap its buttons, and do not stall the capture on it.
dialog_remains() {
  timeout 10 adb shell uiautomator dump /sdcard/foldcade-ui.xml >/dev/null 2>&1 || return 1
  timeout 10 adb shell cat /sdcard/foldcade-ui.xml | tr -d '\r' >"$out/ui-last.xml" || return 1
  grep -q -E 'Not now|Use Foldcade as Home' "$out/ui-last.xml"
}

dismiss_leftover_dialog() {
  dialog_remains || return 0
  echo "step: dismiss leftover dialog"
  adb_step shell input keyevent KEYCODE_BACK
  sleep 1
}

resumed_has() {
  local name="$1"
  local resumed
  resumed="$(adb_do shell dumpsys activity activities 2>/dev/null | tr -d '\r' | grep -E 'ResumedActivity' || true)"
  [[ "$resumed" == *"$name"* ]]
}

# The primary launch starts the companion. If that display is showing the
# stock launcher instead, put the companion back on the presentation display.
show_foldcade() {
  if ! resumed_has "PrimaryHomeActivity"; then
    echo "step: launch ${component}"
    timeout 15 adb shell am start -W -n "$component" --display 0
  fi
  if ! resumed_has "CompanionHomeActivity"; then
    echo "step: companion on display ${presentation_logical}"
    timeout 15 adb shell am start -W \
      -n "${app_id}/app.foldcade.CompanionHomeActivity" \
      --display "$presentation_logical"
  fi
}

expect_foldcade() {
  local resumed
  resumed="$(adb_do shell dumpsys activity activities | tr -d '\r' | grep -E 'ResumedActivity' || true)"
  printf '%s\n' "$resumed" >"$out/resumed.txt"
  if ! printf '%s\n' "$resumed" | grep -q 'PrimaryHomeActivity'; then
    printf '%s\n' "$resumed"
    fail "primary display is not Foldcade"
  fi
  if ! printf '%s\n' "$resumed" | grep -q 'CompanionHomeActivity'; then
    printf '%s\n' "$resumed"
    fail "bottom display is not Foldcade"
  fi
}

dismiss_leftover_dialog
show_foldcade
dismiss_leftover_dialog
expect_foldcade

is_png() {
  local file="$1"
  [ -s "$file" ] || return 1
  local size magic
  size="$(wc -c <"$file")"
  [ "$size" -gt 5000 ] || return 1
  magic="$(od -An -t x1 -N 4 "$file" | tr -d ' \n')"
  [ "$magic" = "89504e47" ]
}

# API 33 screencap parses -d with atoll. The bits of the SurfaceFlinger id
# are unchanged; a value above signed 64-bit max is passed as its signed form.
screencap_arg() {
  python3 -c 'import sys
n = int(sys.argv[1])
if n >= 1 << 63:
    n -= 1 << 64
print(n)' "$1"
}

capture() {
  local id="$1"
  local dest="$2"
  local arg attempt
  arg="$(screencap_arg "$id")"
  for attempt in 1 2 3 4 5; do
    if adb_do exec-out screencap -p -d "$arg" >"$dest" 2>"$out/screencap.err" && is_png "$dest"; then
      return 0
    fi
    sleep 2
  done
  echo "----- screencap -d ${arg} (SurfaceFlinger ${id}) -----"
  cat "$out/screencap.err" 2>/dev/null || true
  fail "screencap failed for display $id"
}

# screencap -d takes the SurfaceFlinger id. --display-id hides virtual
# displays, so match the presentation display by name in --displays.
resolve_screencap_ids() {
  local dump
  dump="$(adb_do shell dumpsys SurfaceFlinger --displays 2>/dev/null | tr -d '\r' || true)"
  printf '%s\n' "$dump" >"$out/surfaceflinger-displays.txt"
  primary="$(printf '%s\n' "$dump" | sed -n 's/^[[:space:]]*Display \([0-9][0-9]*\) (internal.*/\1/p' | head -1)"
  secondary=""
  if [ -n "$presentation_name" ]; then
    secondary="$(printf '%s\n' "$dump" | sed -n "s/^[[:space:]]*Display \\([0-9][0-9]*\\) (virtual, \"${presentation_name}\").*/\\1/p" | head -1)"
  fi
  if [ -z "$secondary" ]; then
    local virt=()
    mapfile -t virt < <(printf '%s\n' "$dump" | sed -n 's/^[[:space:]]*Display \([0-9][0-9]*\) (virtual.*/\1/p')
    if [ "${#virt[@]}" -eq 1 ]; then
      secondary="${virt[0]}"
    fi
  fi
  if [ -z "$primary" ] || [ -z "$secondary" ]; then
    echo "----- SurfaceFlinger --displays -----"
    printf '%s\n' "$dump" | head -n 40
    fail "screencap ids: presentation display ${presentation_logical} has no capture id"
  fi
}

png_geometry() {
  python3 -c 'import struct,sys; f=open(sys.argv[1],"rb"); f.seek(16); w,h=struct.unpack(">II", f.read(8)); print("%dx%d" % (w, h))' "$1"
}

expect_png() {
  local file="$1"
  local want="$2"
  local got
  got="$(png_geometry "$file")"
  if [ "$got" != "$want" ]; then
    fail "${file} is ${got}, wanted ${want}"
  fi
}

echo "step: capture both displays"
resolve_screencap_ids
{
  echo "Thor-sized emulator, not a Thor pass."
  echo "presentation_logical=${presentation_logical} name=${presentation_name} FLAG_PRESENTATION real ${bottom_width}x${bottom_height}"
  echo "primary=${primary} screencap=$(screencap_arg "$primary") ${top_width}x${top_height} density ${top_density}"
  echo "secondary=${secondary} screencap=$(screencap_arg "$secondary") ${bottom_width}x${bottom_height} density ${bottom_density}"
  echo "bottom density ${bottom_density} is derived from the 3.92 inch diagonal."
  echo "published bottom 335 PPI conflicts with that diagonal and is not used."
  echo "device=${device}"
  echo "component=${app_id}/app.foldcade.PrimaryHomeActivity"
  adb_do shell dumpsys SurfaceFlinger --display-id | tr -d '\r' || true
} >"$out/display-ids.txt"

capture "$primary" "$out/primary.png"
capture "$secondary" "$out/secondary.png"
expect_png "$out/primary.png" "${top_width}x${top_height}"
expect_png "$out/secondary.png" "${bottom_width}x${bottom_height}"
cp "$out/primary.png" "$out/display-${primary}.png"
cp "$out/secondary.png" "$out/display-${secondary}.png"

echo "step: home"
timeout 15 adb shell am start -W \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME \
  -n "$component" \
  --display 0
dismiss_leftover_dialog
show_foldcade
dismiss_leftover_dialog
expect_foldcade
capture "$primary" "$out/home-primary.png"
capture "$secondary" "$out/home-secondary.png"
expect_png "$out/home-primary.png" "${top_width}x${top_height}"
expect_png "$out/home-secondary.png" "${bottom_width}x${bottom_height}"

echo "Captured displays $primary and $secondary. Thor-sized emulator, not a Thor pass."
