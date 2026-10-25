# REA TV helper for Amazon Fire TV over ADB (Windows PowerShell 5+ / PowerShell 7).
#
#   .\rea.ps1 connect 192.168.1.50
#   .\rea.ps1 install [REA-TV.apk]
#   .\rea.ps1 setup
#   .\rea.ps1 debloat [-Tier 1|2|3] [-Apply]
#   .\rea.ps1 restore
#   .\rea.ps1 optimize [-Animations 0.5] [-Cached N]
#   .\rea.ps1 clean
#   .\rea.ps1 status
#   .\rea.ps1 unlink
#   .\rea.ps1 tmdb KEY            (Top Shelf series & movies from TMDB; "tmdb off" removes it)
param(
    [Parameter(Position = 0)][string]$Command = "help",
    [Parameter(Position = 1)][string]$Target = "",
    [ValidateSet(1, 2, 3)][int]$Tier = 1,
    [switch]$Apply,
    [string]$Animations = "0.5",
    [string]$Cached = ""
)

$ErrorActionPreference = "Stop"
$Pkg = "com.reatv.launcher"
$Service = "$Pkg/$Pkg.system.HomeButtonService"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$List = Join-Path $Here "debloat-packages.txt"
$Adb = if ($env:ADB) { $env:ADB } else { "adb" }
$Never = @(
    "android", "com.android.systemui", "com.android.settings", "com.android.providers.settings",
    "com.amazon.tv.launcher", "com.amazon.firehomestarter", "com.amazon.tv.settings.v2", "com.amazon.tv.oobe",
    "com.amazon.dcp", "com.amazon.tv.inputpreference.service",
    "com.amazon.tv.keypolicymanager", "com.amazon.tv.ime", $Pkg
)

function Ok($m) { Write-Host "  [ok] $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  [!] $m" -ForegroundColor Yellow }
function Fail($m) { Write-Host "  [x] $m" -ForegroundColor Red }
function Title($m) { Write-Host $m -ForegroundColor White }

function Shell([string[]]$Cmd) {
    # Native stderr must not become a terminating error under "Stop" (Windows PowerShell 5.1).
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $out = & $Adb shell @Cmd 2>&1 | ForEach-Object { "$_" }
    } finally {
        $ErrorActionPreference = $previous
    }
    return (($out -join "`n") -replace "`r", "").Trim()
}

function Need-Device {
    if (-not (Get-Command $Adb -ErrorAction SilentlyContinue)) { throw "adb not found. Install Android platform-tools and add it to PATH." }
    $state = (& $Adb get-state 2>$null)
    if ($state -ne "device") { throw "No device. Enable ADB debugging on the Fire TV, then: .\rea.ps1 connect <fire-tv-ip>" }
}

function Has-Package([string]$Output, [string]$Name) { return ($Output -split "`n" | ForEach-Object { $_.Trim() }) -contains "package:$Name" }
function Installed($p) { Has-Package (Shell @("pm", "list", "packages", $p)) $p }
function Disabled($p) { Has-Package (Shell @("pm", "list", "packages", "-d", $p)) $p }
function Known($p) { Has-Package (Shell @("pm", "list", "packages", "-u", $p)) $p }

function Read-List([int]$Max) {
    Get-Content $List | Where-Object { $_ -and -not $_.TrimStart().StartsWith("#") } | ForEach-Object {
        $parts = $_.Split("|", 3)
        if ([int]$parts[0] -le $Max) { [pscustomobject]@{ Tier = $parts[0]; Package = $parts[1]; Description = $parts[2] } }
    }
}

function Cmd-Connect {
    if (-not $Target) { throw "Usage: .\rea.ps1 connect <fire-tv-ip>" }
    $ip = if ($Target.Contains(":")) { $Target } else { "$($Target):5555" }
    Title "Connecting to $ip (accept the prompt on your TV)..."
    & $Adb connect $ip
}

function Cmd-Install {
    Need-Device
    $apk = $Target
    if (-not $apk) {
        $apk = Get-ChildItem -Path ".\REA-TV*.apk", (Join-Path $Here "..\app\build\outputs\apk\release\*.apk") -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $apk -or -not (Test-Path $apk)) { throw "APK not found. Pass the path: .\rea.ps1 install REA-TV.apk" }
    Title "Installing $apk..."
    & $Adb install -r $apk
    Ok "Installed. Next: .\rea.ps1 setup"
}

function Cmd-Setup {
    Need-Device
    if (-not (Installed $Pkg)) { throw "REA TV isn't installed. Run: .\rea.ps1 install" }
    Title "Linking REA TV to your Fire TV..."
    $grant = Shell @("pm", "grant", $Pkg, "android.permission.WRITE_SECURE_SETTINGS")
    if ($grant) { Warn "This Fire OS build refuses WRITE_SECURE_SETTINGS; linking manually instead" } else { Ok "Self-repair permission granted" }
    # Fire OS 6 never runs third-party accessibility services: the Home watcher reads the system log instead.
    Shell @("pm", "grant", $Pkg, "android.permission.READ_LOGS") | Out-Null
    $perms = Shell @("dumpsys", "package", $Pkg)
    if ($perms -like "*android.permission.READ_LOGS: granted=true*") { Ok "Home watcher allowed (READ_LOGS): works on every Fire OS version" }
    else { Warn "READ_LOGS was refused; only the accessibility route is left (Fire OS 7/8)" }
    $ops = Shell @("appops", "set", $Pkg, "GET_USAGE_STATS", "allow")
    if ($ops) { Warn "Couldn't grant usage access; Storage will show totals only" } else { Ok "Usage access granted" }

    $current = Shell @("settings", "get", "secure", "enabled_accessibility_services")
    if (-not $current -or $current -eq "null") { $updated = $Service }
    elseif ((":$current:").Contains(":$($Service):")) { $updated = $current }
    else { $updated = "$($current):$Service" }
    Shell @("settings", "put", "secure", "enabled_accessibility_services", $updated) | Out-Null
    Shell @("settings", "put", "secure", "accessibility_enabled", "1") | Out-Null
    $after = Shell @("settings", "get", "secure", "enabled_accessibility_services")
    if ((":$after:").Contains(":$($Service):")) { Ok "Accessibility route enabled (used on Fire OS 7/8; Fire OS 6 ignores it)" }
    else { Warn "Fire OS rejected the accessibility route" }

    Title "Compiling REA TV ahead-of-time..."
    Shell @("cmd", "package", "compile", "-m", "speed", "-f", $Pkg) | Out-Null
    Ok "Compiled"
    # Restart REA TV so it picks up the new permissions and starts the Home watcher.
    Shell @("am", "force-stop", $Pkg) | Out-Null
    Shell @("am", "start", "-n", "$Pkg/.MainActivity") | Out-Null
    Start-Sleep -Seconds 2
    $services = Shell @("dumpsys", "activity", "services", $Pkg)
    if ($services -like "*HomeWatchService*") { Ok "Home watcher running: Home now returns to REA TV" }
    elseif ((":$after:").Contains(":$($Service):")) { Warn "Home watcher not running; relying on the accessibility route" }
    else { Fail "Nothing can take over the Home button on this build. Open REA TV from Your Apps." }
    # REA TV turns on its Top Shelf recommendations listener itself once it may write secure settings.
    $listeners = Shell @("settings", "get", "secure", "enabled_notification_listeners")
    if ($listeners -like "*$Pkg/*") { Ok "Top Shelf shows the series and movies your apps recommend" }
    else { Warn "Top Shelf can't read app recommendations on this build" }
    if ($env:REA_TMDB_KEY) { Cmd-Tmdb $env:REA_TMDB_KEY }
    Ok "Done. Press Home on your remote to try it."
}

# A TMDB key (free at themoviedb.org -> Settings -> API) lets the Top Shelf show what's popular on
# Netflix, Prime Video, Disney+ and others. Sent over ADB: typing one with a remote is no fun.
function Cmd-Tmdb([string]$Key) {
    Need-Device
    if (-not (Installed $Pkg)) { throw "REA TV isn't installed. Run: .\rea.ps1 install" }
    if (-not $Key) { throw "Usage: .\rea.ps1 tmdb YOUR_KEY   (free at https://www.themoviedb.org/settings/api; 'off' removes it)" }
    if ($Key -eq "off") { $Key = "none" }
    # Only key characters: the value passes through the device's shell.
    $Key = $Key -replace '[^A-Za-z0-9._-]', ''
    $out = Shell @("am", "broadcast", "-n", "$Pkg/.system.ConfigReceiver", "-a", "$Pkg.SET_TMDB_KEY", "--es", "key", $Key)
    if ($out -match "TMDB key [a-z]+") { Ok $Matches[0] } else { Fail "REA TV didn't take the key: $out" }
}

function Cmd-Unlink {
    Need-Device
    $current = Shell @("settings", "get", "secure", "enabled_accessibility_services")
    $kept = ($current -split ":") | Where-Object { $_ -and $_ -ne $Service -and $_ -ne "$Pkg/.system.HomeButtonService" }
    if ($kept) { Shell @("settings", "put", "secure", "enabled_accessibility_services", ($kept -join ":")) | Out-Null }
    else { Shell @("settings", "delete", "secure", "enabled_accessibility_services") | Out-Null }
    Shell @("pm", "revoke", $Pkg, "android.permission.WRITE_SECURE_SETTINGS") | Out-Null
    Shell @("pm", "revoke", $Pkg, "android.permission.READ_LOGS") | Out-Null
    Shell @("am", "force-stop", $Pkg) | Out-Null
    Ok "Home button returned to Amazon's home screen"
}

function Cmd-Debloat {
    Need-Device
    if ($Apply) { Title "Disabling tier <= $Tier packages (reversible with: .\rea.ps1 restore)" }
    else { Title "Preview of tier <= $Tier (nothing changes; add -Apply to disable)" }
    $count = 0
    foreach ($item in Read-List $Tier) {
        $p = $item.Package
        if ($Never -contains $p) { Fail "$p is protected by REA TV, skipping"; continue }
        if (-not (Installed $p)) { continue }
        if (Disabled $p) { Ok "[$($item.Tier)] $p already off - $($item.Description)"; continue }
        if (-not $Apply) { Write-Host ("  - [{0}] {1,-48} {2}" -f $item.Tier, $p, $item.Description); $count++; continue }
        $out = Shell @("pm", "disable-user", "--user", "0", $p)
        if ($out -like "*disabled-user*") { Ok "[$($item.Tier)] $p - $($item.Description)"; $count++ }
        else { Warn "[$($item.Tier)] $p refused by Fire OS ($out)" }
    }
    if ($Apply) { Title "$count package(s) disabled. Reboot recommended: adb reboot" }
    else { Title "$count package(s) would be disabled. Run again with -Apply to do it." }
}

function Cmd-Restore {
    Need-Device
    Title "Re-enabling every package REA TV's debloat can touch..."
    foreach ($item in Read-List 3) {
        $p = $item.Package
        if (-not (Known $p)) { continue }
        if (-not (Installed $p)) { Shell @("cmd", "package", "install-existing", $p) | Out-Null; Ok "$p reinstalled for user 0" }
        if (Disabled $p) { Shell @("pm", "enable", $p) | Out-Null; Ok "$p enabled" }
    }
    Ok "Restore finished. Reboot recommended: adb reboot"
}

function Cmd-Clean {
    Need-Device
    Shell @("pm", "trim-caches", "999G") | Out-Null
    Ok "All app caches trimmed"
    Shell @("am", "kill-all") | Out-Null
    Ok "Background apps stopped"
    $mem = Shell @("cat", "/proc/meminfo")
    $avail = ($mem -split "`n" | Where-Object { $_ -like "MemAvailable*" }) -replace "[^0-9]", ""
    if ($avail) { Ok ("Memory available now: {0:N0} MB" -f ([int64]$avail / 1024)) }
}

function Cmd-Optimize {
    Need-Device
    Title "Optimizing your Fire TV..."
    Shell @("settings", "put", "global", "window_animation_scale", $Animations) | Out-Null
    Shell @("settings", "put", "global", "transition_animation_scale", $Animations) | Out-Null
    Ok "System window/transition animations: $($Animations)x"
    if ($Cached) {
        Shell @("settings", "put", "global", "activity_manager_constants", "max_cached_processes=$Cached") | Out-Null
        Ok "Cached background apps limited to $Cached (Fire OS 8 only)"
    }
    Shell @("cmd", "package", "compile", "-m", "speed", "-f", $Pkg) | Out-Null
    Ok "REA TV compiled ahead-of-time"
    Shell @("cmd", "package", "bg-dexopt-job") | Out-Null
    Ok "Optimized every app's code (background dexopt)"
    Cmd-Clean
}

function Cmd-Status {
    Need-Device
    Title "Device"
    Write-Host ("  {0} {1} - Android {2} (SDK {3})" -f (Shell @("getprop", "ro.product.manufacturer")), (Shell @("getprop", "ro.product.model")),
        (Shell @("getprop", "ro.build.version.release")), (Shell @("getprop", "ro.build.version.sdk")))
    Write-Host ("  Build: {0}" -f (Shell @("getprop", "ro.build.version.name")))
    Title "Memory"
    (Shell @("cat", "/proc/meminfo")) -split "`n" | Where-Object { $_ -match "^(MemTotal|MemAvailable)" } | ForEach-Object { Write-Host "  $_" }
    Title "REA TV"
    if (Installed $Pkg) {
        Ok "Installed"
        $services = Shell @("settings", "get", "secure", "enabled_accessibility_services")
        if ((":$services:").Contains(":$($Service):")) { Ok "Home button linked" } else { Warn "Home button not linked: .\rea.ps1 setup" }
    } else { Warn "Not installed: .\rea.ps1 install" }
}

switch ($Command.ToLower()) {
    "connect" { Cmd-Connect }
    "install" { Cmd-Install }
    "setup" { Cmd-Setup }
    "unlink" { Cmd-Unlink }
    "debloat" { Cmd-Debloat }
    "restore" { Cmd-Restore }
    "optimize" { Cmd-Optimize }
    "clean" { Cmd-Clean }
    "status" { Cmd-Status }
    "tmdb" { Cmd-Tmdb $Target }
    default { Get-Content $MyInvocation.MyCommand.Path -TotalCount 12 | Select-Object -Skip 1 | ForEach-Object { $_.TrimStart("# ") } }
}
