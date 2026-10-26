#!/usr/bin/env bash
# Installs a REA developer app on a Roku TV in Developer Mode (replacing any developer app there).
#   scripts/sideload.sh TV-IP DEV-PASSWORD [probe]
set -euo pipefail
tv="${1:?TV IP address}"
password="${2:?Developer Mode password}"
app="${3:-probe}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
zip="$(mktemp -d)/rea-$app.zip"
(cd "$here/$app" && zip -qr "$zip" manifest source components images)
out="$(curl -sS --digest -u "rokudev:$password" -F mysubmit=Install -F "archive=@$zip" "http://$tv/plugin_install")"
if grep -qiE "Install Success|Identical to previous version" <<<"$out"; then
  echo "Installed $app on $tv; it's open on the TV now."
else
  echo "The TV didn't accept it. Is Developer Mode on and the password right?" >&2
  grep -oE "<font color=\"red\">[^<]*" <<<"$out" | sed 's/.*>//' >&2 || true
  exit 1
fi
