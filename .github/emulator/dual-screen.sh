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
# It tries, in order: hw.display1 in config.ini, -qt-hide-window, then
# `cmd display` (or the API 33 overlay display, confirmed with cmd display).
# A crash-consent dialog and a downloadable snapshot both block the guest
# before adbd. Cold-boot with the adb server already up.
set -euo pipefail

: "${ANDROID_HOME:?}"
: "${ANDROID_AVD_HOME:?}"

export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

out="${1:-screenshots}"
mkdir -p "$out"
rm -f "$out"/*.png "$out"/display-ids.txt "$out"/ui-last.xml "$out"/emulator.log \
  "$out"/logcat.txt "$out"/failure.txt

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

display_ids() {
  adb_do shell dumpsys SurfaceFlinger --display-id 2>/dev/null | tr -d '\r' \
    | sed -n 's/^Display[[:space:]]\+\([0-9][0-9]*\).*/\1/p' || true
}

bottom_ready() {
  local dump
  mapfile -t ids < <(display_ids | awk '!seen[$0]++')
  [ "${#ids[@]}" -ge 2 ] || return 1
  dump="$(adb_do shell dumpsys display 2>/dev/null | tr -d '\r' || true)"
  printf '%s\n' "$dump" | grep -q "${bottom_width}"
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

second_method=""
start_emulator ""
wait_for_boot
echo "step: second display config.ini"
start_guest_service
if poll_bottom; then
  second_method="config.ini hw.display1"
fi
if [ -z "$second_method" ]; then
  echo "step: second display qt-hide-window"
  stop_emulator
  start_emulator "-qt-hide-window"
  wait_for_boot
  start_guest_service
  if poll_bottom; then
    second_method="qt-hide-window"
  fi
fi
if [ -z "$second_method" ]; then
  echo "step: second display cmd display"
  help_text="$(adb_do shell cmd display help 2>&1 || true)"
  printf '%s\n' "$help_text" >"$out/cmd-display-help.txt"
  if printf '%s\n' "$help_text" | grep -q 'create-virtual-display'; then
    adb_do shell cmd display create-virtual-display \
      --width "$bottom_width" --height "$bottom_height" --density "$bottom_density" || true
    second_method="cmd display create-virtual-display"
  else
    echo "cmd display has no create-virtual-display on this image. Using overlay_display_devices."
    adb_do shell settings put global overlay_display_devices \
      "${bottom_width}x${bottom_height}/${bottom_density}"
    second_method="overlay_display_devices confirmed with cmd display get-displays"
  fi
  adb_do shell cmd display get-displays >"$out/cmd-display-get.txt" || true
  if ! poll_bottom; then
    echo "----- SurfaceFlinger displays -----"
    adb_do shell dumpsys SurfaceFlinger --display-id 2>/dev/null || true
    echo "----- cmd display get-displays -----"
    cat "$out/cmd-display-get.txt" 2>/dev/null || true
    fail "second display: none of config.ini, qt-hide-window, or cmd display created the bottom panel"
  fi
fi
printf '%s\n' "$second_method" | tee "$out/second-display-method.txt"
echo "second display created by: ${second_method}"

echo "step: launch"
adb_do shell input keyevent KEYCODE_WAKEUP
adb_do shell wm dismiss-keyguard || true
timeout 60 adb install -r "$apk"
adb_do shell am start -W -n "${app_id}/app.foldcade.PrimaryHomeActivity"

wait_for "companion home" 20 \
  bash -c 'timeout 30 adb shell dumpsys activity activities | tr -d "\r" | grep -q CompanionHomeActivity'
wait_for "primary home" 10 \
  bash -c 'timeout 30 adb shell dumpsys activity activities | tr -d "\r" | grep -q PrimaryHomeActivity'

# Home prompt opens on Not now. Wait until that button is in the hierarchy,
# then Activate. Do not press Center before the prompt is showing.
echo "step: dismiss home prompt"
wait_for "Not now" 30 ui_has "Not now"
adb_do shell input keyevent KEYCODE_DPAD_CENTER
wait_for "home prompt to close" 20 ui_lacks "Not now"

# First stand-in, on the focused display. The shelf opens on One.
echo "step: first stand-in"
wait_for "shelf title One" 20 ui_has "One"
adb_do shell input keyevent KEYCODE_DPAD_CENTER
wait_for "first stand-in" 20 bash -c 'test "$(timeout 30 adb shell dumpsys activity activities | tr -d "\r" | grep -c StandInActivity || true)" -ge 1'

# Second stand-in. The picker is on the other display, so keys have to go there.
# Use a display id only when input -h documents it.
echo "step: second stand-in"
input_help="$(adb_do shell input -h 2>&1 || true)"
if ! printf '%s\n' "$input_help" | grep -Eq '(^|[[:space:]])--?d([[:space:]]|$)|DISPLAY_ID|display ID|displayId'; then
  echo "::error::input -h does not document a display id. The second stand-in needs that flag, and this script will not invent one."
  printf '%s\n' "$input_help" | head -n 40
  fail "second stand-in: input -h has no display id"
fi

home_displays() {
  activity_dump | awk '
    /^[[:space:]]*Display #[0-9]/ {
      disp = ""
      if (match($0, /#[0-9]+/)) {
        disp = substr($0, RSTART + 1, RLENGTH - 1)
      }
      top = 1
      next
    }
    top && /Hist / {
      top = 0
      if ($0 ~ /PrimaryHomeActivity|CompanionHomeActivity/) print disp
    }
  '
}

picker_ready() {
  [ -n "$(home_displays | head -1)" ]
}

wait_for "picker still on a home display" 15 picker_ready
picker_display="$(home_displays | head -1)"
if [ -z "$picker_display" ]; then
  echo "----- activity dump -----"
  activity_dump | head -n 120 || true
  fail "second stand-in: no Foldcade home remained"
fi

send_key() {
  local display="$1"
  local key="$2"
  adb_do shell input -d "$display" keyevent "$key"
}

# Focus is restored on One. Right moves to Two. Four columns, so Down does not.
send_key "$picker_display" KEYCODE_DPAD_RIGHT
wait_for "focus on Two" 20 label_focused "Two"
send_key "$picker_display" KEYCODE_DPAD_CENTER
wait_for "second stand-in" 20 bash -c 'test "$(timeout 30 adb shell dumpsys activity activities | tr -d "\r" | grep -c StandInActivity || true)" -ge 2'

is_png() {
  local file="$1"
  [ -s "$file" ] || return 1
  local size magic
  size="$(wc -c <"$file")"
  [ "$size" -gt 5000 ] || return 1
  magic="$(od -An -t x1 -N 4 "$file" | tr -d ' \n')"
  [ "$magic" = "89504e47" ]
}

capture() {
  local id="$1"
  local dest="$2"
  local attempt
  for attempt in 1 2 3 4 5; do
    if adb_do exec-out screencap -p -d "$id" >"$dest" && is_png "$dest"; then
      return 0
    fi
    sleep 2
  done
  fail "screencap failed for display $id"
}

echo "step: capture both displays"
{
  echo "Thor-sized emulator, not a Thor pass."
  echo "primary=${primary} ${top_width}x${top_height} density ${top_density}"
  echo "secondary=${secondary} ${bottom_width}x${bottom_height} density ${bottom_density}"
  echo "bottom density ${bottom_density} is derived from the 3.92 inch diagonal."
  echo "published bottom 335 PPI conflicts with that diagonal and is not used."
  echo "device=${device}"
  echo "picker_display=${picker_display}"
  adb_do shell dumpsys SurfaceFlinger --display-id | tr -d '\r' || true
} >"$out/display-ids.txt"

capture "$primary" "$out/primary.png"
capture "$secondary" "$out/secondary.png"
cp "$out/primary.png" "$out/display-${primary}.png"
cp "$out/secondary.png" "$out/display-${secondary}.png"

# Home on the display that just received input. Captures fail if screencap fails.
echo "step: home"
before_focus="$(current_focus)"
send_key "$picker_display" KEYCODE_HOME
focus_left() {
  [ "$(current_focus)" != "$before_focus" ]
}
wait_for "focus to leave the stand-in" 20 focus_left
capture "$primary" "$out/home-primary.png"
capture "$secondary" "$out/home-secondary.png"

echo "Captured displays $primary and $secondary. Thor-sized emulator, not a Thor pass."
