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
  # A dump that misses the idle window keeps running on the guest after this
  # timeout. The next shell then sits behind it, and a later am start -W
  # exits 124. Kill the guest dump before treating the dialog as gone.
  if ! timeout 10 adb shell uiautomator dump /sdcard/foldcade-ui.xml >/dev/null 2>&1; then
    timeout 5 adb shell 'for pid in $(pidof uiautomator); do kill "$pid"; done' >/dev/null 2>&1 || true
    return 1
  fi
  timeout 10 adb shell cat /sdcard/foldcade-ui.xml 2>/dev/null | tr -d '\r' >"$out/ui-last.xml" || return 1
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
  # Shorter than adb_do. A stuck dumpsys is not proof the activity is gone,
  # and burning 30s here is what made the recovery launch look necessary.
  resumed="$(timeout 8 adb shell dumpsys activity activities 2>/dev/null | tr -d '\r' | grep -E 'ResumedActivity' || true)"
  [[ "$resumed" == *"$name"* ]]
}

wait_resumed() {
  local name="$1"
  local attempt
  for attempt in 1 2 3 4 5 6 7 8; do
    if resumed_has "$name"; then
      return 0
    fi
    sleep 0.5
  done
  return 1
}

# The primary launch starts the companion. If that display is showing the
# stock launcher instead, put the companion back on the presentation display.
# Recovery starts do not use -W. Run 38002269310 printed Status: ok for the
# cold start, then this second PrimaryHomeActivity launch sat in -W until
# timeout exited 124. A singleTask activity that is already resumed does not
# complete another -W wait.
show_foldcade() {
  if ! resumed_has "PrimaryHomeActivity" && ! wait_resumed "PrimaryHomeActivity"; then
    echo "step: launch ${component}"
    timeout 15 adb shell am start -n "$component" --display 0 || true
    wait_resumed "PrimaryHomeActivity" || true
  fi
  if ! resumed_has "CompanionHomeActivity" && ! wait_resumed "CompanionHomeActivity"; then
    echo "step: companion on display ${presentation_logical}"
    timeout 15 adb shell am start \
      -n "${app_id}/app.foldcade.CompanionHomeActivity" \
      --display "$presentation_logical" || true
    wait_resumed "CompanionHomeActivity" || true
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

# Azahar launch path. Thor-sized emulator, not a Thor pass.
# Azahar is not installed here. The capture is the missing-player state.
# input -d is used only when this image's help text documents it.
echo "step: azahar launch path"
input_help="$(adb_do shell input -h 2>&1 | tr -d '\r' || true)"
printf '%s\n' "$input_help" >"$out/input-help.txt"
if printf '%s\n' "$input_help" | grep -q -- '-d' && printf '%s\n' "$input_help" | grep -qi 'display'; then
  show_foldcade
  dismiss_leftover_dialog
  echo "step: focus the 3DS tile"
  # Home grid: All, then the 3DS folder. One right lands on that folder.
  # The first confirm opens it.
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_DPAD_RIGHT
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_DPAD_CENTER
  sleep 1
  capture "$primary" "$out/folder-open-primary.png"
  capture "$secondary" "$out/folder-open-secondary.png"
  expect_png "$out/folder-open-primary.png" "${top_width}x${top_height}"
  expect_png "$out/folder-open-secondary.png" "${bottom_width}x${bottom_height}"
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_DPAD_CENTER
  sleep 1
  capture "$primary" "$out/launch-primary.png"
  capture "$secondary" "$out/launch-secondary.png"
  expect_png "$out/launch-primary.png" "${top_width}x${top_height}"
  expect_png "$out/launch-secondary.png" "${bottom_width}x${bottom_height}"
  timeout 10 adb shell uiautomator dump /sdcard/foldcade-ui.xml >/dev/null 2>&1 || true
  timeout 10 adb shell cat /sdcard/foldcade-ui.xml 2>/dev/null | tr -d '\r' >"$out/launch-ui.xml" || true
  seen="no"
  if grep -q 'not installed' "$out/launch-ui.xml"; then
    seen="yes"
  fi
  {
    echo "Thor-sized emulator, not a Thor pass."
    echo "Azahar is not installed on this image. The launch captures are the missing-player path."
    echo "This is not a measurement of Azahar on the Thor bottom panel."
    echo "missing_player_in_ui_dump=${seen}"
  } >"$out/launch-path.txt"
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_BACK || true
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_BACK || true

  # melonDS launch path. Thor-sized emulator, not a Thor pass.
  # melonDS is not installed here. The capture is the missing-player state.
  # The DS folder is the next cell to the right of the 3DS folder.
  echo "step: melonDS launch path"
  echo "step: focus the DS tile"
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_DPAD_RIGHT
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_DPAD_CENTER
  sleep 1
  capture "$primary" "$out/ds-focus-primary.png"
  capture "$secondary" "$out/ds-focus-secondary.png"
  expect_png "$out/ds-focus-primary.png" "${top_width}x${top_height}"
  expect_png "$out/ds-focus-secondary.png" "${bottom_width}x${bottom_height}"
  echo "step: open the DS tile"
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_DPAD_CENTER
  sleep 1
  capture "$primary" "$out/ds-launch-primary.png"
  capture "$secondary" "$out/ds-launch-secondary.png"
  expect_png "$out/ds-launch-primary.png" "${top_width}x${top_height}"
  expect_png "$out/ds-launch-secondary.png" "${bottom_width}x${bottom_height}"
  timeout 10 adb shell uiautomator dump /sdcard/foldcade-ui.xml >/dev/null 2>&1 || true
  timeout 10 adb shell cat /sdcard/foldcade-ui.xml 2>/dev/null | tr -d '\r' >"$out/ds-launch-ui.xml" || true
  seen="no"
  if grep -q 'not installed' "$out/ds-launch-ui.xml"; then
    seen="yes"
  fi
  {
    echo "Thor-sized emulator, not a Thor pass."
    echo "melonDS is not installed on this image. The launch captures are the missing-player path."
    echo "Top is ${top_width}x${top_height}. Bottom is ${bottom_width}x${bottom_height}."
    echo "This is not a measurement of the DS touch screen on the Thor bottom panel."
    echo "missing_player_in_ui_dump=${seen}"
  } >"$out/ds-launch-path.txt"
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_BACK || true
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_BACK || true

  # GameNative launch path. Thor-sized emulator, not a Thor pass.
  # GameNative is not installed here. The capture is the missing-player state.
  # The GameNative folder is the cell to the right of the DS folder.
  echo "step: GameNative launch path"
  echo "step: focus the PC tile"
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_DPAD_RIGHT
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_DPAD_CENTER
  sleep 1
  capture "$primary" "$out/pc-focus-primary.png"
  capture "$secondary" "$out/pc-focus-secondary.png"
  expect_png "$out/pc-focus-primary.png" "${top_width}x${top_height}"
  expect_png "$out/pc-focus-secondary.png" "${bottom_width}x${bottom_height}"
  timeout 10 adb shell uiautomator dump /sdcard/foldcade-ui.xml >/dev/null 2>&1 || true
  timeout 10 adb shell cat /sdcard/foldcade-ui.xml | tr -d '\r' >"$out/pc-focus-ui.xml" || true
  echo "step: open the PC tile"
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_DPAD_CENTER
  sleep 1
  capture "$primary" "$out/pc-launch-primary.png"
  capture "$secondary" "$out/pc-launch-secondary.png"
  expect_png "$out/pc-launch-primary.png" "${top_width}x${top_height}"
  expect_png "$out/pc-launch-secondary.png" "${bottom_width}x${bottom_height}"
  timeout 10 adb shell uiautomator dump /sdcard/foldcade-ui.xml >/dev/null 2>&1 || true
  timeout 10 adb shell cat /sdcard/foldcade-ui.xml | tr -d '\r' >"$out/pc-launch-ui.xml" || true
  seen="no"
  if grep -q 'not installed' "$out/pc-launch-ui.xml"; then
    seen="yes"
  fi
  progress="no"
  if grep -q 'Progress lives in GameNative' "$out/pc-focus-ui.xml" \
    || grep -q 'Progress lives in GameNative' "$out/pc-launch-ui.xml"; then
    progress="yes"
  fi
  {
    echo "Thor-sized emulator, not a Thor pass."
    echo "GameNative is not installed on this image. The launch captures are the missing-player path."
    echo "Top is ${top_width}x${top_height}. Bottom is ${bottom_width}x${bottom_height}."
    echo "This is not a measurement of GameNative's Presentation mode on the Thor bottom panel."
    echo "missing_player_in_ui_dump=${seen}"
    echo "progress_sentence_in_ui_dump=${progress}"
  } >"$out/pc-launch-path.txt"
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_BACK || true
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_BACK || true

  # Moonlight launch path. Thor-sized emulator, not a Thor pass.
  # Moonlight is not installed here. The capture is the missing-player state.
  # The Moonlight folder is one cell right of GameNative.
  # It does not take both screens, so the picker stays on the other panel.
  echo "step: moonlight launch path"
  echo "step: focus the Moonlight tile"
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_DPAD_RIGHT
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_DPAD_CENTER
  sleep 1
  capture "$primary" "$out/moonlight-focus-primary.png"
  capture "$secondary" "$out/moonlight-focus-secondary.png"
  expect_png "$out/moonlight-focus-primary.png" "${top_width}x${top_height}"
  expect_png "$out/moonlight-focus-secondary.png" "${bottom_width}x${bottom_height}"
  timeout 10 adb shell uiautomator dump /sdcard/foldcade-ui.xml >/dev/null 2>&1 || true
  timeout 10 adb shell cat /sdcard/foldcade-ui.xml | tr -d '\r' >"$out/moonlight-focus-ui.xml" || true
  launch_on_top="no"
  if grep -q 'Launch on top' "$out/moonlight-focus-ui.xml"; then
    launch_on_top="yes"
  fi
  echo "step: open the Moonlight tile"
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_DPAD_CENTER
  sleep 1
  capture "$primary" "$out/moonlight-launch-primary.png"
  capture "$secondary" "$out/moonlight-launch-secondary.png"
  expect_png "$out/moonlight-launch-primary.png" "${top_width}x${top_height}"
  expect_png "$out/moonlight-launch-secondary.png" "${bottom_width}x${bottom_height}"
  timeout 10 adb shell uiautomator dump /sdcard/foldcade-ui.xml >/dev/null 2>&1 || true
  timeout 10 adb shell cat /sdcard/foldcade-ui.xml | tr -d '\r' >"$out/moonlight-launch-ui.xml" || true
  seen="no"
  if grep -q 'not installed' "$out/moonlight-launch-ui.xml"; then
    seen="yes"
  fi
  {
    echo "Thor-sized emulator, not a Thor pass."
    echo "Moonlight is not installed on this image. The launch captures are the missing-player path."
    echo "Top is ${top_width}x${top_height}. Bottom is ${bottom_width}x${bottom_height}."
    echo "This is not a stream. The picker stays on the other screen."
    echo "Physical Thor checks stay on issue 43."
    echo "launch_on_top_in_focus_dump=${launch_on_top}"
    echo "missing_player_in_ui_dump=${seen}"
  } >"$out/moonlight-launch-path.txt"
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_BACK || true
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_BACK || true
else
  {
    echo "Thor-sized emulator, not a Thor pass."
    echo "input help does not document a display id. The launch path was not driven."
  } >"$out/launch-path.txt"
fi

# Home-grid proof frames are captured after the fixture library is seeded,
# so the grid shows system folders rather than the demo shelf. The 3DS
# folder-open frames above stay. Scroll does not launch a game.

# Shoulder panels on the Thor-sized emulator. This is not a Thor pass.
# A setup cancelled before the first script step is not a capture result.
# The home role is held, so Set as Home is hidden. From Library the
# rows are Theme, Primary, Arrange, Order, Music, Track, Volume,
# Background, Motion, Play time, Azahar saves, melonDS saves, Edit home,
# All library, Add new games, Android Games, Apps, Hidden, then Android settings:
# nineteen downs. One down from the
# launch-target row lands on the Wi-Fi tile while a stand-in is focused.
# Key events default to display -1, and that call does not return once a
# second display exists. The help text writes the flag as "[-d DISPLAY_ID]"
# and "-d: specify the display ID." Home was started on display 0.
echo "step: shoulder panels"
if printf '%s\n' "$input_help" | grep -q -- '-d' && printf '%s\n' "$input_help" | grep -qi 'display'; then
  # A missing-player dialog swallows L1 and R1, so close it on both displays first.
  echo "step: close leftover dialog"
  for _ in 1 2 3; do
    adb_do shell input -d 0 keyevent KEYCODE_BACK || true
    adb_do shell input -d "$presentation_logical" keyevent KEYCODE_BACK || true
    sleep 0.4
  done

  # Debug builds snap the morph. Display 0 is the main panel.
  # The first start commits the hold. This emulator composites that frame on the
  # next start, so the second start paints the same hold before the screenshot.
  show_island() {
    timeout 20 adb shell am start -n "$component" \
      --es foldcade.island "$1" \
      --display 0 || fail "island $1"
  }
  capture_island() {
    local hold="$1"
    local name="$2"
    echo "step: island ${hold} on display 0"
    show_island "$hold"
    sleep 1
    show_island "$hold"
    sleep 1
    capture "$primary" "$out/${name}-top.png"
    capture "$secondary" "$out/${name}-bottom.png"
    expect_png "$out/${name}-top.png" "${top_width}x${top_height}"
    expect_png "$out/${name}-bottom.png" "${bottom_width}x${bottom_height}"
  }

  capture_island left-mid settings-l1-mid
  capture_island right-open settings-r1
  capture_island left-open settings-l1
  echo "step: close island"
  timeout 20 adb shell am start -n "$component" \
    --es foldcade.island closed \
    --display 0 || true
  sleep 1
  {
    echo "Thor-sized emulator, not a Thor pass."
    echo "L1 and R1 open on the main display. settings-l1-mid is the mid-morph frame."
    echo "Top is ${top_width}x${top_height}. Bottom is ${bottom_width}x${bottom_height}."
    echo "These screenshots are an emulator result. They are not a Thor pass."
  } >"$out/settings-captures.txt"
else
  {
    echo "Thor-sized emulator, not a Thor pass."
    echo "input help does not document a display id. Shoulder keys were not sent."
  } >"$out/settings-captures.txt"
fi

{
  echo "Thor-sized emulator, not a Thor pass."
  echo "Top panel captures are ${top_width}x${top_height}. Bottom panel captures are ${bottom_width}x${bottom_height}."
  echo "input_display_flag=${input_display_flag:-none}"
  echo "These screenshots are an emulator result. They are not a Thor pass."
} >>"$out/settings-captures.txt"

open_android_shelf() {
  local which="$1"
  echo "step: android shelf ${which}"
  timeout 20 adb shell am start -W -n "$component" \
    --es app.foldcade.extra.SHELF "$which" \
    --display 0 || fail "android shelf ${which}"
  sleep 1
  show_foldcade
  dismiss_leftover_dialog
}

# Emulator only, not a Thor pass. Top 1920×1080, bottom 1240×1080.
# The extra opens the shelf the controller also opens from the left panel.
echo "step: android shelves"
open_android_shelf apps
capture "$primary" "$out/apps-top.png"
capture "$secondary" "$out/apps-bottom.png"
expect_png "$out/apps-top.png" "${top_width}x${top_height}"
expect_png "$out/apps-bottom.png" "${bottom_width}x${bottom_height}"
open_android_shelf games
capture "$primary" "$out/games-top.png"
capture "$secondary" "$out/games-bottom.png"
expect_png "$out/games-top.png" "${top_width}x${top_height}"
expect_png "$out/games-bottom.png" "${bottom_width}x${bottom_height}"
{
  echo "Thor-sized emulator, not a Thor pass."
  echo "Android Games and Apps shelves. Emulator only, not a Thor pass."
  echo "Top ${top_width}x${top_height} at ${top_density} dpi."
  echo "Bottom ${bottom_width}x${bottom_height} at ${bottom_density} dpi."
} >"$out/android-shelves.txt"

# The Afterglow focus fill. A solid pill of this color is the focused button.
# Ribbons and tile glows on the idle captures are not this color.
focus_mint_min=1500

# Count pixels of the accent fill. uiautomator dump on this image writes an
# empty hierarchy while the ribbons keep moving, so it cannot see the title.
focus_mint_count() {
  python3 - "$1" <<'PY'
import struct, sys, zlib
data = open(sys.argv[1], "rb").read()
if data[:8] != b"\x89PNG\r\n\x1a\n":
    sys.exit("not a png")
pos = 8
width = height = color = None
idat = []
while pos < len(data):
    ln = struct.unpack(">I", data[pos:pos + 4])[0]
    typ = data[pos + 4:pos + 8]
    chunk = data[pos + 8:pos + 8 + ln]
    pos += 12 + ln
    if typ == b"IHDR":
        width, height, bit, color, comp, filt, inter = struct.unpack(">IIBBBBB", chunk)
        if bit != 8 or inter != 0 or color not in (2, 6):
            sys.exit("unsupported png")
    elif typ == b"IDAT":
        idat.append(chunk)
    elif typ == b"IEND":
        break
raw = zlib.decompress(b"".join(idat))
bpp = 3 if color == 2 else 4
stride = width * bpp
i = 0
prev = bytearray(stride)
mint = 0
for y in range(height):
    filt = raw[i]
    i += 1
    row = bytearray(raw[i:i + stride])
    i += stride
    if filt == 1:
        for x in range(stride):
            left = row[x - bpp] if x >= bpp else 0
            row[x] = (row[x] + left) & 255
    elif filt == 2:
        for x in range(stride):
            row[x] = (row[x] + prev[x]) & 255
    elif filt == 3:
        for x in range(stride):
            left = row[x - bpp] if x >= bpp else 0
            row[x] = (row[x] + ((left + prev[x]) // 2)) & 255
    elif filt == 4:
        for x in range(stride):
            a = row[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            p = a + b - c
            pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
            pred = a if pa <= pb and pa <= pc else b if pb <= pc else c
            row[x] = (row[x] + pred) & 255
    elif filt != 0:
        sys.exit("bad filter")
    prev = row
    for x in range(0, stride, bpp):
        r, g, b = row[x], row[x + 1], row[x + 2]
        if 188 <= r <= 212 and g >= 247 and 216 <= b <= 240:
            mint += 1
print(mint)
PY
}

# Debug builds only. Each file is a Thor-sized emulator frame, not a Thor pass.
capture_dialog() {
  local kind="$1" index="$2" screen="$3" name="$4" needle="$5"
  local mark="preview-dialog kind=${kind} index=${index} screen=${screen}"
  echo "step: dialog ${name}"
  adb_do logcat -c || fail "dialog ${name}: logcat clear"
  timeout 20 adb shell am start -W -n "$component" \
    --es foldcade.dialog "$kind" \
    --ei foldcade.dialogIndex "$index" \
    --es foldcade.dialogScreen "$screen" \
    --display 0
  local attempt shot count
  for attempt in 1 2 3 4 5 6 7 8 9 10 11 12; do
    sleep 1
    timeout 15 adb logcat -d -s Foldcade:I | tr -d '\r' >"$out/${name}.log" || true
    if grep -q "$mark" "$out/${name}.log" && grep -q "$needle" "$out/${name}.log"; then
      break
    fi
  done
  if ! grep -q "$mark" "$out/${name}.log" 2>/dev/null || ! grep -q "$needle" "$out/${name}.log" 2>/dev/null; then
    fail "dialog ${name} did not show ${needle}"
  fi
  # The log is published before the next Compose frame. This image skips frames.
  sleep 2
  for shot in 1 2 3 4 5; do
    capture "$primary" "$out/${name}-top.png"
    capture "$secondary" "$out/${name}-bottom.png"
    expect_png "$out/${name}-top.png" "${top_width}x${top_height}"
    expect_png "$out/${name}-bottom.png" "${bottom_width}x${bottom_height}"
    count="$(focus_mint_count "$out/${name}-${screen}.png")"
    printf 'focus-pill %s mint=%s\n' "${name}-${screen}.png" "$count" | tee -a "$out/dialog-focus-pills.txt"
    if [ "$count" -ge "$focus_mint_min" ]; then
      return 0
    fi
    sleep 2
  done
  fail "dialog ${name} focus pill was not on the ${screen} panel"
}

# Emulator only, not a Thor pass. The extra sets which button is focused.
capture_dialog home 0 bottom home-use-as-home "Use Foldcade as Home"
capture_dialog home 1 bottom home-not-now "Use Foldcade as Home"
capture_dialog folder 0 bottom folder-continue "Choose a folder"
capture_dialog folder 1 bottom folder-not-now "Choose a folder"
capture_dialog save-folder 0 bottom save-folder-continue "Save folder"
capture_dialog save-folder 1 bottom save-folder-not-now "Save folder"
capture_dialog close-player 0 bottom close-player-close "Close the other game"
capture_dialog close-player 1 bottom close-player-not-now "Close the other game"
capture_dialog missing-player 0 top missing-player-ok "Azahar is not installed"
capture_dialog relogin 0 top relogin-ok "Sign in again"
{
  echo "Thor-sized emulator, not a Thor pass."
  echo "Dialog focus frames. Emulator only, not a Thor pass."
  echo "Top ${top_width}x${top_height}. Bottom ${bottom_width}x${bottom_height}."
} >"$out/dialog-focus.txt"


# Empty library, then a scanned folder. Thor-sized emulator, not a Thor pass.
# "No library yet" is on the bottom panel, so the script waits for the shell log
# rather than the default-display accessibility dump.
wait_library_log() {
  local phrase="$1" attempt line=""
  for attempt in $(seq 1 30); do
    line="$(timeout 10 adb logcat -d -s Foldcade:I 2>/dev/null | tr -d '\r' || true)"
    if printf '%s\n' "$line" | grep -q "$phrase"; then
      return 0
    fi
    sleep 1
  done
  echo "----- logcat Foldcade -----"
  printf '%s\n' "$line" | tail -n 40
  return 1
}

# uiautomator dump reads the active window. While the presentation display is
# focused, that root is null, and --windows writes an empty <displays /> before
# the interactive-window flag applies. A fixed grep of that file misses a title
# that is on the bottom panel. Wake the presentation display and dump again.
focus_library_display() {
  find_presentation_display || return 1
  adb_do shell input -d "$presentation_logical" keyevent KEYCODE_WAKEUP || true
}

dump_active_window() {
  adb_do shell rm -f /sdcard/foldcade-ui.xml >/dev/null 2>&1 || true
  timeout 15 adb shell uiautomator dump /sdcard/foldcade-ui.xml >/dev/null 2>&1 || true
  timeout 15 adb shell cat /sdcard/foldcade-ui.xml 2>/dev/null | tr -d '\r' >"$out/ui-library.xml" || true
  grep -q '<hierarchy' "$out/ui-library.xml"
}

library_window_is_top() {
  grep -q "bounds=\"\\[0,0\\]\\[${top_width},${top_height}\\]\"" "$out/ui-library.xml"
}

dump_library_ui() {
  local saved
  : >"$out/ui-library.xml"
  if ! dump_active_window; then
    focus_library_display || return 1
    dump_active_window
    return
  fi
  # Grid titles live on the bottom panel. A top-panel dump is the hero.
  if library_window_is_top; then
    saved="$(cat "$out/ui-library.xml")"
    focus_library_display || return 0
    if ! dump_active_window; then
      printf '%s\n' "$saved" >"$out/ui-library.xml"
    fi
  fi
  return 0
}

# Exit 0 when [phrase] is fully inside the dumped window.
# Exit 3 and print next, prev, up, or down when the node is outside that window.
library_text_placement() {
  python3 - "$out/ui-library.xml" "$1" <<'PY'
import re
import sys

path, phrase = sys.argv[1], sys.argv[2]
try:
    xml = open(path, errors="replace").read()
except OSError:
    sys.exit(1)
if "<hierarchy" not in xml:
    sys.exit(1)
nodes = re.findall(r"<node\b([^>]*)/?>", xml)

def fields(blob):
    text = re.search(r'\btext="([^"]*)"', blob)
    bounds = re.search(r'\bbounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', blob)
    label = text.group(1) if text else ""
    box = tuple(int(part) for part in bounds.groups()) if bounds else None
    return label, box

window = None
for blob in nodes:
    _, box = fields(blob)
    if box:
        window = box
        break
if window is None:
    sys.exit(1)
_, _, width, height = window
slop = 4
off = None
for blob in nodes:
    label, box = fields(blob)
    if box is None or phrase not in label:
        continue
    left, top, right, bottom = box
    if (
        left >= -slop
        and top >= -slop
        and right <= width + slop
        and bottom <= height + slop
        and right > left
        and bottom > top
    ):
        sys.exit(0)
    off = box
if off is None:
    sys.exit(2)
left, top, right, bottom = off
if left < -slop:
    print("prev")
elif right > width + slop:
    print("next")
elif top < -slop:
    print("up")
else:
    print("down")
sys.exit(3)
PY
}

# The library grid pages horizontally on the presentation display.
page_library() {
  local toward="$1" y x_from x_to
  find_presentation_display || return 1
  y=$((bottom_height / 2))
  if [ "$toward" = "prev" ]; then
    x_from=$((bottom_width / 4))
    x_to=$((bottom_width * 3 / 4))
  else
    x_from=$((bottom_width * 3 / 4))
    x_to=$((bottom_width / 4))
  fi
  adb_do shell input -d "$presentation_logical" swipe "$x_from" "$y" "$x_to" "$y" 250
}

# The app logs "library-ui page <title> | <title> ..." for the grid page it draws.
# uiautomator dumps only the focused display and comes back empty while the
# presentation display has focus, so a title on screen could go unseen. The
# newest page line is what the bottom panel shows.
library_page_has() {
  local line
  line="$(timeout 10 adb logcat -d -s Foldcade:I 2>/dev/null | tr -d '\r' | grep -F "library-ui page " | tail -n 1 || true)"
  [ -n "$line" ] || return 1
  printf '%s\n' "${line#*library-ui page }" | grep -F -q -- "$1"
}

wait_library_text() {
  local phrase="$1" limit="${2:-20}" attempt status direction pages=0
  for attempt in $(seq 1 "$limit"); do
    if library_page_has "$phrase"; then
      return 0
    fi
    if dump_library_ui; then
      if direction="$(library_text_placement "$phrase")"; then
        return 0
      else
        status=$?
      fi
      if [ "$status" -eq 3 ] && [ "$pages" -lt 3 ]; then
        case "$direction" in
          prev) page_library prev || true ;;
          down) key_bottom KEYCODE_DPAD_DOWN || true ;;
          up) key_bottom KEYCODE_DPAD_UP || true ;;
          *) page_library next || true ;;
        esac
        pages=$((pages + 1))
        sleep 1
        continue
      fi
    fi
    sleep 1
  done
  echo "----- ui-library.xml -----"
  head -c 2000 "$out/ui-library.xml" 2>/dev/null || true
  echo
  echo "----- library-ui page lines -----"
  timeout 10 adb logcat -d -s Foldcade:I 2>/dev/null | tr -d '\r' | grep -F "library-ui page " | tail -n 5 || true
  return 1
}

key_bottom() {
  adb_do shell input -d "$presentation_logical" keyevent "$1"
}

become_root() {
  local attempt
  for attempt in 1 2 3 4 5; do
    adb root >/dev/null 2>&1 || true
    if timeout 30 adb wait-for-device && timeout 15 adb shell echo ok >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

# The persistable document-tree grant is read when the system server starts.
# A guest reboot is what makes the seeded folder visible to the shell.
# RIGHT stays on the current row. All closes back to the All library tile,
# then this walk follows home-ui tile logs. A presentation dump is empty
# while that display is focused, so the folder name is not read from it.
focus_system_folder() {
  local row col
  adb_do logcat -c || true
  key_bottom KEYCODE_BUTTON_B || true
  sleep 0.35
  key_bottom KEYCODE_BUTTON_B || true
  sleep 0.35
  key_bottom KEYCODE_BUTTON_B || true
  sleep 0.4
  for row in $(seq 1 8); do
    for col in $(seq 1 5); do
      if timeout 10 adb logcat -d -s Foldcade:I 2>/dev/null | tr -d '\r' | grep -F -q "home-ui tile Game Boy Advance"; then
        return 0
      fi
      key_bottom KEYCODE_DPAD_RIGHT || true
      sleep 0.3
    done
    if timeout 10 adb logcat -d -s Foldcade:I 2>/dev/null | tr -d '\r' | grep -F -q "home-ui tile Game Boy Advance"; then
      return 0
    fi
    for col in $(seq 1 5); do
      key_bottom KEYCODE_DPAD_LEFT || true
      sleep 0.12
    done
    key_bottom KEYCODE_DPAD_DOWN || true
    sleep 0.3
  done
  timeout 10 adb logcat -d -s Foldcade:I 2>/dev/null | tr -d '\r' | grep -F -q "home-ui tile Game Boy Advance"
}

# Curated home frames. Emulator only, not a Thor pass.
# The grid is already on screen. Scroll is a finger drag, not a launch.
# Edit mode lifts a tile. All Games and All Apps are different lists.
capture_curated_home() {
  echo "step: curated home grid"
  sleep 1
  capture "$primary" "$out/home-primary.png"
  capture "$secondary" "$out/home-secondary.png"
  expect_png "$out/home-primary.png" "${top_width}x${top_height}"
  expect_png "$out/home-secondary.png" "${bottom_width}x${bottom_height}"

  echo "step: scroll the home grid"
  adb_do shell input -d "$presentation_logical" swipe 980 540 280 540 900 &
  scroll_pid=$!
  sleep 0.35
  capture "$primary" "$out/scroll-mid-primary.png"
  capture "$secondary" "$out/scroll-mid-secondary.png"
  wait "$scroll_pid" || true
  expect_png "$out/scroll-mid-primary.png" "${top_width}x${top_height}"
  expect_png "$out/scroll-mid-secondary.png" "${bottom_width}x${bottom_height}"

  echo "step: edit home"
  adb_do logcat -c || true
  key_bottom KEYCODE_BUTTON_L1
  sleep 0.6
  local step
  for step in $(seq 1 24); do
    key_bottom KEYCODE_DPAD_DOWN
    sleep 0.25
    if timeout 10 adb logcat -d -s Foldcade:I 2>/dev/null | tr -d '\r' | grep -q "home-ui row Edit home"; then
      break
    fi
  done
  key_bottom KEYCODE_DPAD_CENTER
  wait_library_log "home-ui editing" || fail "edit home did not open"
  key_bottom KEYCODE_DPAD_RIGHT
  key_bottom KEYCODE_DPAD_CENTER
  wait_library_log "home-ui lifted" || fail "edit home did not lift a tile"
  sleep 0.6
  capture "$primary" "$out/edit-mode-primary.png"
  capture "$secondary" "$out/edit-mode-secondary.png"
  expect_png "$out/edit-mode-primary.png" "${top_width}x${top_height}"
  expect_png "$out/edit-mode-secondary.png" "${bottom_width}x${bottom_height}"
  # B only drops the held tile. Back does not leave edit mode on this image.
  # Start does. All Games is the next L1 row, not a grid confirm.
  key_bottom KEYCODE_BUTTON_START || true
  sleep 0.4

  echo "step: all games"
  adb_do logcat -c || true
  key_bottom KEYCODE_BUTTON_L1
  sleep 0.6
  for step in $(seq 1 24); do
    key_bottom KEYCODE_DPAD_DOWN
    sleep 0.25
    if timeout 10 adb logcat -d -s Foldcade:I 2>/dev/null | tr -d '\r' | grep -q "home-ui row All library"; then
      break
    fi
  done
  key_bottom KEYCODE_DPAD_CENTER
  wait_library_log "home-ui all-games" || fail "All Games did not open"
  sleep 0.6
  capture "$primary" "$out/all-games-primary.png"
  capture "$secondary" "$out/all-games-secondary.png"
  expect_png "$out/all-games-primary.png" "${top_width}x${top_height}"
  expect_png "$out/all-games-secondary.png" "${bottom_width}x${bottom_height}"
  key_bottom KEYCODE_DPAD_UP
  key_bottom KEYCODE_DPAD_CENTER
  wait_library_log "home-ui all-apps" || fail "All Apps did not open"
  sleep 0.6
  capture "$primary" "$out/all-apps-primary.png"
  capture "$secondary" "$out/all-apps-secondary.png"
  expect_png "$out/all-apps-primary.png" "${top_width}x${top_height}"
  expect_png "$out/all-apps-secondary.png" "${bottom_width}x${bottom_height}"
  # The first B leaves the All chrome. The second closes All. The third is a
  # no-op on the root grid. Then focus the Game Boy Advance folder.
  focus_system_folder || fail "folder library: system folder was not on screen"
  {
    echo "Thor-sized emulator, not a Thor pass."
    echo "Grid: home-primary.png and home-secondary.png."
    echo "Open folder: folder-open-primary.png and folder-open-secondary.png."
    echo "Edit mode: edit-mode-primary.png and edit-mode-secondary.png."
    echo "Scroll: scroll-mid-primary.png and scroll-mid-secondary.png."
    echo "All Games: all-games-primary.png and all-games-secondary.png."
    echo "All Apps: all-apps-primary.png and all-apps-secondary.png."
    echo "Top is ${top_width}x${top_height}. Bottom is ${bottom_width}x${bottom_height}."
  } >"$out/home-grid-captures.txt"
}

seed_folder_library() {
  local uid gid prefs tree name boot attempt platform_state
  echo "step: seed folder files"
  adb_do shell mkdir -p \
    /sdcard/Library/gb /sdcard/Library/gbc /sdcard/Library/gba \
    /sdcard/Library/nes /sdcard/Library/snes /sdcard/Library/n64 \
    /sdcard/Library/nds /sdcard/Library/3ds \
    /sdcard/Library/psp /sdcard/Library/gg /sdcard/Library/genesis /sdcard/Library/sms
  adb_do shell "printf '%s\n' foldcade > /sdcard/Library/gb/Link.gb"
  adb_do shell "printf '%s\n' foldcade > /sdcard/Library/gbc/Crystal.gbc"
  for name in Cart Drift Puzzle Quest Racer Runner; do
    adb_do shell "printf '%s\n' foldcade > /sdcard/Library/gba/${name}.gba"
  done
  adb_do shell "printf '%s\n' foldcade > /sdcard/Library/nes/Mario.nes"
  adb_do shell "printf '%s\n' foldcade > /sdcard/Library/snes/Zelda.sfc"
  adb_do shell "printf '%s\n' foldcade > /sdcard/Library/n64/Kart.z64"
  adb_do shell "printf '%s\n' foldcade > /sdcard/Library/nds/Drift.nds"
  adb_do shell "printf '%s\n' foldcade > /sdcard/Library/3ds/Puzzle.cci"
  adb_do shell "printf '%s\n' foldcade > /sdcard/Library/psp/Racer.cso"
  adb_do shell "printf '%s\n' foldcade > /sdcard/Library/gg/Sonic.gg"
  adb_do shell "printf '%s\n' foldcade > /sdcard/Library/genesis/Streets.gen"
  adb_do shell "printf '%s\n' foldcade > /sdcard/Library/sms/Alex.sms"
  become_root || fail "folder library: adb root did not return"
  adb_do shell am force-stop "$app_id"
  tree='content://com.android.externalstorage.documents/tree/primary%3ALibrary'
  cat >"$out/urigrants.xml" <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<uri-grants>
<uri-grant sourceUserId="0" targetUserId="0" sourcePkg="com.android.externalstorage" targetPkg="${app_id}" uri="${tree}" modeFlags="193" createdTime="1" prefix="true" />
</uri-grants>
EOF
  adb_do push "$out/urigrants.xml" /data/system/urigrants.xml >/dev/null
  adb_do shell chown system:system /data/system/urigrants.xml
  adb_do shell chmod 600 /data/system/urigrants.xml
  adb_do shell restorecon /data/system/urigrants.xml || true
  prefs="/data/data/${app_id}/shared_prefs/foldcade.xml"
  uid="$(adb_do shell stat -c '%u' "/data/data/${app_id}")"
  gid="$(adb_do shell stat -c '%g' "/data/data/${app_id}")"
  if ! adb_do pull "$prefs" "$out/foldcade-prefs.xml" >/dev/null; then
    printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" '<map>' '</map>' >"$out/foldcade-prefs.xml"
  fi
  python3 - "$out/foldcade-prefs.xml" "$tree" <<'PY'
import sys
from pathlib import Path
path, tree = sys.argv[1], sys.argv[2]
text = Path(path).read_text()
if "</map>" not in text:
    text = text.rstrip() + "\n<map>\n</map>\n"
if 'name="folder_tree"' not in text:
    text = text.replace("</map>", f'    <string name="folder_tree">{tree}</string>\n</map>')
if 'name="folder_explained"' not in text:
    text = text.replace("</map>", '    <boolean name="folder_explained" value="true" />\n</map>')
Path(path).write_text(text)
PY
  adb_do shell mkdir -p "/data/data/${app_id}/shared_prefs"
  adb_do shell chown "${uid}:${gid}" "/data/data/${app_id}/shared_prefs"
  adb_do push "$out/foldcade-prefs.xml" "$prefs" >/dev/null
  adb_do shell chown "${uid}:${gid}" "$prefs"
  adb_do shell chmod 660 "$prefs"
  adb_do shell restorecon "$prefs" || true
  adb_do shell rm -f "${prefs}.bak" || true
  echo "step: reboot for the folder grant"
  adb reboot || true
  for attempt in $(seq 1 60); do
    boot="$(timeout 10 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [ "$boot" != "1" ]; then
      break
    fi
    sleep 1
  done
  wait_for_boot
  start_guest_service
  if ! poll_bottom; then
    adb_do shell settings put global overlay_display_devices \
      "${bottom_width}x${bottom_height}/${bottom_density}" || true
    poll_bottom || fail "folder library: bottom display did not return"
  fi
  echo "step: launch seeded library"
  timeout 30 adb shell am start -W -n "$component" --display 0
  dismiss_leftover_dialog
  show_foldcade
  dismiss_leftover_dialog
  expect_foldcade
  find_presentation_display || fail "folder library: bottom display was not found"
  wait_library_log "library-ui home" || fail "folder library: home grid did not load"
  resolve_screencap_ids
  capture_curated_home
  wait_library_log "home-ui tile Game Boy Advance" \
    || fail "folder library: system folder was not on screen"
  key_bottom KEYCODE_DPAD_CENTER
  wait_library_log "library-ui games" || fail "folder library: game grid did not load"
  if ! wait_library_text "Cart" 6; then
    # Confirm can land on the launch chrome and leave the platform grid up.
    # Step onto the tile and open it. A games grid does not contain this title.
    platform_state=0
    library_text_placement "Game Boy" >/dev/null || platform_state=$?
    # An empty dump says nothing. A fresh page line with the platform title says it is still up.
    if [ "$platform_state" -eq 1 ] && library_page_has "Game Boy"; then
      platform_state=0
    fi
    if [ "$platform_state" -eq 0 ] || [ "$platform_state" -eq 3 ]; then
      find_presentation_display || true
      key_bottom KEYCODE_DPAD_DOWN || true
      adb logcat -c >/dev/null 2>&1 || true
      key_bottom KEYCODE_DPAD_CENTER
      wait_library_log "library-ui games" || true
    fi
    wait_library_text "Cart" 14 || fail "folder library: game title was not on screen"
  fi
  sleep 1
  resolve_screencap_ids
  capture "$primary" "$out/library-top.png"
  capture "$secondary" "$out/library-bottom.png"
  expect_png "$out/library-top.png" "${top_width}x${top_height}"
  expect_png "$out/library-bottom.png" "${bottom_width}x${bottom_height}"
}

echo "step: empty library"
# The shelf launch path may still be showing a missing-player dialog.
key_bottom KEYCODE_BACK || true
sleep 1
timeout 30 adb shell am start -W -n "$component" \
  --es app.foldcade.extra.FOLDER_LIBRARY 1 \
  --display 0
dismiss_leftover_dialog
show_foldcade
dismiss_leftover_dialog
expect_foldcade
wait_library_log "library-ui no-library" || fail "empty library: No library yet was not shown"
key_bottom KEYCODE_BACK || true
sleep 1
resolve_screencap_ids
capture "$primary" "$out/empty-top.png"
capture "$secondary" "$out/empty-bottom.png"
expect_png "$out/empty-top.png" "${top_width}x${top_height}"
expect_png "$out/empty-bottom.png" "${bottom_width}x${bottom_height}"

seed_folder_library
{
  echo "Thor-sized emulator, not a Thor pass."
  echo "Empty library: empty-top.png is ${top_width}x${top_height}, empty-bottom.png is ${bottom_width}x${bottom_height}."
  echo "Scanned folder: library-top.png is ${top_width}x${top_height}, library-bottom.png is ${bottom_width}x${bottom_height}."
  echo "These captures are not a pass on Thor hardware."
} >"$out/library-captures.txt"

echo "Captured displays $primary and $secondary. Thor-sized emulator, not a Thor pass."
