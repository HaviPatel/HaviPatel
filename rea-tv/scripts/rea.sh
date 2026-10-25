#!/usr/bin/env bash
# REA TV helper for Amazon Fire TV over ADB: install, link the Home button, debloat, optimize.
# Works from Linux, macOS and Windows (Git Bash / WSL). Needs `adb` (Android platform-tools).
#
#   scripts/rea.sh connect 192.168.1.50      pair with the Fire TV over the network
#   scripts/rea.sh install [REA-TV.apk]      install or update REA TV
#   scripts/rea.sh setup                     link the Home button + permissions + AOT compile
#   scripts/rea.sh debloat [--tier N] [--apply]   preview (default) or disable Amazon bloat
#   scripts/rea.sh restore                   re-enable everything debloat turned off
#   scripts/rea.sh optimize [--animations X] [--cached N]
#   scripts/rea.sh clean                     trim all app caches and stop background apps
#   scripts/rea.sh status                    device, memory, storage, Home button state
#   scripts/rea.sh unlink                    stop REA TV from taking over the Home button
#   scripts/rea.sh tmdb KEY                  Top Shelf series & movies from TMDB ("tmdb off" removes it)
set -euo pipefail

PKG="com.reatv.launcher"
SERVICE="$PKG/$PKG.system.HomeButtonService"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIST="$HERE/debloat-packages.txt"
ADB="${ADB:-adb}"

# Never touched, whatever the list says: these keep the device bootable and usable.
NEVER=(
  android com.android.systemui com.android.settings com.android.providers.settings
  com.amazon.tv.launcher com.amazon.firehomestarter com.amazon.tv.settings.v2 com.amazon.tv.oobe
  com.amazon.dcp com.amazon.tv.inputpreference.service
  com.amazon.tv.keypolicymanager com.amazon.tv.ime "$PKG"
)

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
ok() { printf '  \033[32m✓\033[0m %s\n' "$*"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$*"; }
fail() { printf '  \033[31m✗\033[0m %s\n' "$*"; }
die() { printf '\033[31m%s\033[0m\n' "$*" >&2; exit 1; }

shell() { "$ADB" shell "$@" | tr -d '\r'; }

need_device() {
  command -v "$ADB" >/dev/null 2>&1 || die "adb not found. Install Android platform-tools and add it to PATH."
  local state
  state="$("$ADB" get-state 2>/dev/null || true)"
  [[ "$state" == "device" ]] || die "No device. Enable ADB debugging on the Fire TV, then: $0 connect <fire-tv-ip>"
}

is_never() {
  local p="$1" n
  for n in "${NEVER[@]}"; do [[ "$p" == "$n" ]] && return 0; done
  return 1
}

# Capture first, then grep: `grep -q` closing a pipe early would trip pipefail.
has_line() { grep -qx "package:$2" <<<"$1"; }
installed() { has_line "$(shell pm list packages "$1")" "$1"; }
disabled() { has_line "$(shell pm list packages -d "$1")" "$1"; }
known() { has_line "$(shell pm list packages -u "$1")" "$1"; }

cmd_connect() {
  local ip="${1:-}"
  [[ -n "$ip" ]] || die "Usage: $0 connect <fire-tv-ip>   (Settings > My Fire TV > About > Network)"
  [[ "$ip" == *:* ]] || ip="$ip:5555"
  bold "Connecting to $ip (accept the prompt on your TV)…"
  "$ADB" connect "$ip"
}

cmd_install() {
  need_device
  local apk="${1:-}"
  if [[ -z "$apk" ]]; then
    apk="$(ls -t ./REA-TV*.apk "$HERE"/../app/build/outputs/apk/release/*.apk 2>/dev/null | head -n1 || true)"
  fi
  [[ -n "$apk" && -f "$apk" ]] || die "APK not found. Pass the path: $0 install REA-TV.apk"
  bold "Installing $apk…"
  "$ADB" install -r "$apk"
  ok "Installed. Next: $0 setup"
}

cmd_setup() {
  need_device
  installed "$PKG" || die "REA TV isn't installed. Run: $0 install"
  bold "Linking REA TV to your Fire TV…"

  if shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1; then
    ok "Self-repair permission granted (REA TV can re-link itself after updates)"
  else
    warn "This Fire OS build refuses WRITE_SECURE_SETTINGS; linking manually instead"
  fi

  # Fire OS 6 never runs third-party accessibility services; there the Home watcher follows the
  # system log instead, which needs READ_LOGS. Verified from dumpsys: old adb exits 0 regardless.
  shell pm grant "$PKG" android.permission.READ_LOGS >/dev/null 2>&1 || true
  if grep -q "android.permission.READ_LOGS: granted=true" <<<"$(shell dumpsys package "$PKG")"; then
    ok "Home watcher allowed (READ_LOGS): works on every Fire OS version"
  else
    warn "READ_LOGS was refused; only the accessibility route is left (Fire OS 7/8)"
  fi

  if shell appops set "$PKG" GET_USAGE_STATS allow >/dev/null 2>&1; then
    ok "Usage access granted (per-app storage sizes)"
  else
    warn "Couldn't grant usage access; Storage will show totals only"
  fi

  local current updated
  current="$(shell settings get secure enabled_accessibility_services)"
  if [[ "$current" == "null" || -z "$current" ]]; then
    updated="$SERVICE"
  elif [[ ":$current:" == *":$SERVICE:"* ]]; then
    updated="$current"
  else
    updated="$current:$SERVICE"
  fi
  shell settings put secure enabled_accessibility_services "$updated" >/dev/null
  shell settings put secure accessibility_enabled 1 >/dev/null
  if [[ ":$(shell settings get secure enabled_accessibility_services):" == *":$SERVICE:"* ]]; then
    ok "Accessibility route enabled (used on Fire OS 7/8; Fire OS 6 ignores it)"
  else
    warn "Fire OS rejected the accessibility route"
  fi

  bold "Compiling REA TV ahead-of-time for instant startup…"
  shell cmd package compile -m speed -f "$PKG" >/dev/null 2>&1 && ok "Compiled (speed)" || warn "AOT compile not supported on this build"

  # Restart REA TV so it picks up the new permissions and starts the Home watcher.
  shell am force-stop "$PKG" >/dev/null 2>&1 || true
  shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1 || true
  sleep "${REA_SETTLE:-2}"
  if grep -q "HomeWatchService" <<<"$(shell dumpsys activity services "$PKG")"; then
    ok "Home watcher running: Home now returns to REA TV"
  elif [[ ":$(shell settings get secure enabled_accessibility_services):" == *":$SERVICE:"* ]]; then
    warn "Home watcher not running; relying on the accessibility route"
  else
    fail "Nothing can take over the Home button on this build. Open REA TV from Your Apps."
  fi
  # REA TV turns on its Top Shelf recommendations listener itself once it may write secure settings.
  if [[ "$(shell settings get secure enabled_notification_listeners)" == *"$PKG/"* ]]; then
    ok "Top Shelf shows the series and movies your apps recommend"
  else
    warn "Top Shelf can't read app recommendations on this build"
  fi
  [[ -n "${REA_TMDB_KEY:-}" ]] && cmd_tmdb "$REA_TMDB_KEY"
  ok "Done. Press Home on your remote to try it."
}

# A TMDB key (free at themoviedb.org → Settings → API) lets the Top Shelf show what's popular on
# Netflix, Prime Video, Disney+ and others. Sent over ADB: typing one with a remote is no fun.
cmd_tmdb() {
  need_device
  installed "$PKG" || die "REA TV isn't installed. Run: $0 install"
  local key="${1:-}"
  [[ -n "$key" ]] || die "Usage: $0 tmdb YOUR_KEY   (free at https://www.themoviedb.org/settings/api; \"off\" removes it)"
  [[ "$key" == "off" ]] && key=""
  # Only key characters: the value passes through the device's shell.
  key="$(printf '%s' "$key" | tr -cd 'A-Za-z0-9._-')"
  local out
  out="$(shell am broadcast -n "$PKG/.system.ConfigReceiver" -a "$PKG.SET_TMDB_KEY" --es key "${key:-none}")"
  if grep -q "TMDB key" <<<"$out"; then
    ok "$(grep -o 'TMDB key [a-z]*' <<<"$out")"
  else
    fail "REA TV didn't take the key: $out"
  fi
}

cmd_unlink() {
  need_device
  local current updated
  current="$(shell settings get secure enabled_accessibility_services)"
  updated="$(printf '%s' "$current" | tr ':' '\n' | grep -vx "$SERVICE" | grep -vx "$PKG/.system.HomeButtonService" | paste -sd: - || true)"
  if [[ -z "$updated" ]]; then
    shell settings delete secure enabled_accessibility_services >/dev/null
  else
    shell settings put secure enabled_accessibility_services "$updated" >/dev/null
  fi
  shell pm revoke "$PKG" android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1 || true
  shell pm revoke "$PKG" android.permission.READ_LOGS >/dev/null 2>&1 || true
  shell am force-stop "$PKG" >/dev/null 2>&1 || true
  ok "Home button returned to Amazon's home screen"
}

read_list() {
  # prints "tier package description" for tiers <= $1
  local max="$1"
  grep -v '^[[:space:]]*#' "$LIST" | grep -v '^[[:space:]]*$' | while IFS='|' read -r tier pkg desc; do
    [[ "$tier" -le "$max" ]] && printf '%s|%s|%s\n' "$tier" "$pkg" "$desc"
  done
}

cmd_debloat() {
  need_device
  local tier=1 apply=0
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --tier) tier="${2:?}"; shift 2 ;;
      --apply) apply=1; shift ;;
      *) die "Unknown option $1" ;;
    esac
  done
  [[ "$tier" =~ ^[123]$ ]] || die "--tier must be 1, 2 or 3"
  local log="$HERE/.rea-debloat-$("$ADB" get-serialno | tr -c 'A-Za-z0-9._-' '_').log"

  if [[ "$apply" -eq 1 ]]; then
    bold "Disabling tier ≤ $tier packages (reversible with: $0 restore)"
  else
    bold "Preview of tier ≤ $tier (nothing changes; add --apply to disable)"
  fi

  local count=0
  while IFS='|' read -r t pkg desc; do
    if is_never "$pkg"; then fail "$pkg is protected by REA TV, skipping"; continue; fi
    if ! installed "$pkg"; then continue; fi
    if disabled "$pkg"; then ok "[$t] $pkg already off · $desc"; continue; fi
    if [[ "$apply" -eq 0 ]]; then
      printf '  • [%s] %-48s %s\n' "$t" "$pkg" "$desc"
      count=$((count + 1))
      continue
    fi
    local out
    out="$(shell pm disable-user --user 0 "$pkg" 2>&1 || true)"
    if [[ "$out" == *"disabled-user"* ]]; then
      ok "[$t] $pkg · $desc"
      printf '%s\n' "$pkg" >>"$log"
      count=$((count + 1))
    else
      warn "[$t] $pkg refused by Fire OS (${out#*Exception: })"
    fi
  done < <(read_list "$tier")

  if [[ "$apply" -eq 1 ]]; then
    bold "$count package(s) disabled. Reboot recommended: adb reboot"
  else
    bold "$count package(s) would be disabled. Run again with --apply to do it."
  fi
}

cmd_restore() {
  need_device
  bold "Re-enabling every package REA TV's debloat can touch…"
  while IFS='|' read -r _ pkg _; do
    if known "$pkg"; then
      if ! installed "$pkg"; then
        shell cmd package install-existing "$pkg" >/dev/null 2>&1 && ok "$pkg reinstalled for user 0"
      fi
      if disabled "$pkg"; then
        shell pm enable "$pkg" >/dev/null 2>&1 && ok "$pkg enabled" || warn "$pkg couldn't be enabled"
      fi
    fi
  done < <(read_list 3)
  ok "Restore finished. Reboot recommended: adb reboot"
}

cmd_optimize() {
  need_device
  local anim="0.5" cached=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --animations) anim="${2:?}"; shift 2 ;;
      --cached) cached="${2:?}"; shift 2 ;;
      *) die "Unknown option $1" ;;
    esac
  done
  bold "Optimizing your Fire TV…"
  shell settings put global window_animation_scale "$anim" >/dev/null
  shell settings put global transition_animation_scale "$anim" >/dev/null
  ok "System window/transition animations: ${anim}x (REA TV's own motion is unaffected)"

  if [[ -n "$cached" ]]; then
    shell settings put global activity_manager_constants "max_cached_processes=$cached" >/dev/null
    ok "Cached background apps limited to $cached (Fire OS 8 / Android 10+ only)"
  fi

  shell cmd package compile -m speed -f "$PKG" >/dev/null 2>&1 && ok "REA TV compiled ahead-of-time" || true
  shell cmd package bg-dexopt-job >/dev/null 2>&1 && ok "Optimized every app's code (background dexopt)" || warn "bg-dexopt not available"
  cmd_clean
}

cmd_clean() {
  need_device
  shell pm trim-caches 999G >/dev/null 2>&1 && ok "All app caches trimmed" || warn "Cache trim not available"
  shell am kill-all >/dev/null 2>&1 && ok "Background apps stopped" || true
  local mem
  mem="$(shell cat /proc/meminfo | awk '/MemAvailable/ {printf "%.0f MB", $2/1024}')"
  ok "Memory available now: $mem"
}

cmd_status() {
  need_device
  bold "Device"
  printf '  %s %s · Android %s (SDK %s)\n' "$(shell getprop ro.product.manufacturer)" "$(shell getprop ro.product.model)" \
    "$(shell getprop ro.build.version.release)" "$(shell getprop ro.build.version.sdk)"
  printf '  Build: %s\n' "$(shell getprop ro.build.version.name)"
  bold "Memory"
  shell cat /proc/meminfo | awk '/MemTotal|MemAvailable/ {printf "  %-14s %6.0f MB\n", $1, $2/1024}'
  bold "Storage (/data)"
  shell df -h /data | tail -n1 | awk '{printf "  %s used of %s, %s free\n", $3, $2, $4}'
  bold "REA TV"
  if installed "$PKG"; then
    ok "Installed ($(shell dumpsys package "$PKG" | awk -F= '/versionName/ {print $2; exit}'))"
    if [[ ":$(shell settings get secure enabled_accessibility_services):" == *":$SERVICE:"* ]]; then
      ok "Home button linked"
    else
      warn "Home button not linked: $0 setup"
    fi
  else
    warn "Not installed: $0 install"
  fi
  bold "Debloat"
  local off=0
  while IFS='|' read -r _ pkg _; do disabled "$pkg" && off=$((off + 1)); done < <(read_list 3)
  printf '  %s package(s) from the list are disabled\n' "$off"
}

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print } /^set /{ exit }' "$0"; }

main() {
  local cmd="${1:-help}"
  shift || true
  case "$cmd" in
    connect) cmd_connect "$@" ;;
    install) cmd_install "$@" ;;
    setup) cmd_setup ;;
    unlink) cmd_unlink ;;
    debloat) cmd_debloat "$@" ;;
    restore) cmd_restore ;;
    optimize) cmd_optimize "$@" ;;
    clean) need_device; cmd_clean ;;
    status) cmd_status ;;
    tmdb) cmd_tmdb "$@" ;;
    help | -h | --help) usage ;;
    *) usage; exit 1 ;;
  esac
}

main "$@"
