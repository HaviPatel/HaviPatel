# REA TV

An Apple TV–style home screen for Amazon Fire TV Sticks. It has a Liquid Glass look, a Top Shelf,
a Control Center, focus parallax, and nothing extra. It also comes with ADB scripts that remove
Amazon bloat and tune RAM and storage.

- **Small APK, no internet permission.** Compose ui/foundation/animation only, no Material, shrunk
  by R8. No analytics, accounts, or icon packs. Wallpapers are drawn in code, and the icons are
  Lucide outlines compiled to vector paths. Text is set in Inter, the closest open typeface to
  SF Pro (Latin subset, four weights).
- **Built for 1 GB sticks.** Artwork is decoded at tile size, cached by byte budget, and handed
  back to the system as soon as you open an app. Only rows near the focus load artwork.
- **Smooth on weak GPUs.** Focus, scrolling, and glass all run in the draw/graphics-layer phase,
  with no recomposition per frame and no continuous animation while idle.

## What's inside

| | |
|---|---|
| **Home** | tvOS-style grid with nothing else on screen: like tvOS, there's no clock or button bar (the time is in Control Center). Every tile is the app's own banner, so the logos are the real ones. The top row owns the edge-to-edge **Top Shelf**: the focused app's banner blurred across the whole shelf (or a wash of its colors when the banner is mostly white), fading into the black behind the rows, with the app's logo lifted off its banner as the title, the way tvOS shows a show's title treatment. Dark wordmarks turn light for the dark shelf, as brands do on dark backgrounds. Banners that are a photo, or whose logo runs into the edge, show the name and the banner as a card instead. Deeper rows scroll up and pin near the top. |
| **Focus** | Tiles lift toward you with a deep shadow, tilt from the direction you came, settle with a spring, and catch a specular glint. Moving focus ticks and selecting clicks, using Fire TV's own navigation sounds, so its Navigation Sounds setting turns them off. |
| **Liquid Glass** | Every panel refracts what's behind it (magnified, saturated, blurred backdrop). One shared light glides toward whatever has focus, so rim highlights and glows sweep across all the glass on screen. |
| **Control Center** (☰ Menu button) | A glass panel from the right with the time in large type and round glass controls: Network, Bluetooth, **Memory** (free it up), Storage, Screensaver, Fire TV settings, REA TV settings, Amazon Home, plus live RAM and storage meters. |
| **Hold OK on an app** | Open, Move (jiggle mode, then use the arrows), Move to / remove from Top Shelf, Hide, App info & cache, Uninstall. |
| **Settings** (Control Center › REA TV) | Wallpapers (Aurora, Ocean, Sunset, Forest, Graphite, Midnight, or Top Shelf colors), glass level (Clear, Regular, Frosted), icon size, labels, 24-hour clock, Reduce motion / transparency, hidden apps, per-app storage, memory booster, device info. Rows are plain text until focus turns them into bright glass, as in tvOS, and the category list has a liquid lens that stretches as it slides. |
| **Home button** | On Fire TV, Home brings you back to REA TV (see below). On Android TV / Google TV, pick REA TV as the Home app. |

## Compatibility

| Device | Status |
|---|---|
| Fire TV Stick 4K / 4K Max (both gens), Lite, 3rd gen, Cube: Fire OS 7, 8 | Supported target |
| Fire TV Stick 4K 1st gen, Cube 1st gen: Fire OS 6 (Android 7.1) | Supported target. Home uses the log watcher (below). Overlays use the wallpaper as their glass backdrop and Settings › Storage shows totals only, since both need Android 8. |
| Fire TV Stick 2nd gen: Fire OS 5 | Installs (minSdk 22), same Android 8 limits as Fire OS 6. |
| Android TV / Google TV boxes | Supported (a real Home app there) |
| Fire TV devices on **Vega OS** (e.g. Fire TV Stick 4K Select, 2025) | ❌ No Android apps or sideloading |

> Not yet verified on physical hardware: please report what works on your stick.

## Install (about 5 minutes)

1. **On the Fire TV**, open Settings › My Fire TV › About and click the device name 7 times. Then
   open Settings › My Fire TV › Developer options and turn on **ADB debugging** and **Apps from
   Unknown Sources**. Note the IP address under About › Network.
2. **Get the APK.** Open the latest run of the *REA TV* workflow under this repository's
   **Actions** tab and download `REA-TV-apk`, or grab a release asset. It also contains the
   `scripts/` folder.
3. **From a computer** with [ADB](https://developer.android.com/tools/releases/platform-tools)
   on the same network:

   ```bash
   scripts/rea.sh connect 192.168.1.50   # accept the prompt on the TV
   scripts/rea.sh install REA-TV-v1.0.0.apk
   scripts/rea.sh setup                  # links the Home button, grants permissions, AOT-compiles
   ```

   On Windows use `scripts\rea.ps1` with the same commands (or Git Bash / WSL with `rea.sh`).

Then press **Home** on the remote.

## Remote

| Button | Action |
|---|---|
| D-pad | Move focus |
| OK | Open |
| Hold OK | App options (Move, Top Shelf, Hide, Info, Uninstall) |
| ☰ Menu | Control Center |
| Back | Close the overlay, or jump back to the top row |
| Home | Back to REA TV. Pressed while already home, it returns to the first app |

## How the Home button works on Fire TV

Fire OS has no "default home app" setting and never passes the Home key to third-party apps.
Amazon's launcher is also a protected package that can't be disabled, and `set-home-activity`
reports success but changes nothing. REA TV therefore notices Amazon's home coming up and brings
itself back, two ways (`setup` turns on both; whichever works on your build does the job):

- **Log watcher (Fire OS 6 and up).** Fire OS 6.7.1.1 never binds third-party accessibility
  services (tested on a Fire TV Stick 4K by the
  [tv-vibehack](https://github.com/ljis120301/tv-vibehack/blob/fire-tv-support/FIRETV.md) fork of
  cleanyourtv). So a small foreground service follows two system-log tags with `READ_LOGS`
  (granted over ADB): a HOME start aimed at Amazon's launcher (the Home button) and Amazon's home
  screen resuming (Back out of an app). It reads nothing else and has no network access.
- **Accessibility service (Fire OS 7/8).** It subscribes to *window-state changes only* and
  **cannot read screen content** (`canRetrieveWindowContent=false`).

Both react only to Amazon's home *screen* (`com.amazon.tv.launcher/.ui.HomeActivity_vNext` or any
activity that answers HOME), so other Amazon screens in the same package are left alone. Both reopen
REA TV through an exact alarm: Android holds activity starts from background apps for about 5 seconds
after Home is pressed, and alarm-delivered starts skip that wait. You may see Amazon's home for a
split second. If you grant the optional `WRITE_SECURE_SETTINGS` over ADB (`setup` does this), REA TV
re-links the accessibility service if Fire OS drops it after an update.

**Known limits:**

- Fire OS 6 shows Settings inside Amazon's home, so after opening Settings from REA TV, Amazon's
  home stays until you press Home.
- Some Fire OS 8 builds (users report 8.1.5.3+) revoke third-party accessibility services; the log
  watcher still works there if `READ_LOGS` was granted.
- With an accessibility service on, a few apps (e.g. Prime Video) keep their playback controls
  visible longer.
- With Fire OS parental controls set to "PIN protect app launches", any launcher started over
  Amazon's home triggers the PIN prompt each time.
- `scripts/rea.sh unlink` hands Home back to Amazon at any time.

## Debloat and optimize

```bash
scripts/rea.sh debloat --tier 1            # preview only: shows what would be turned off
scripts/rea.sh debloat --tier 1 --apply    # telemetry, ads, sponsored rows, promos (safe)
scripts/rea.sh debloat --tier 2 --apply    # + Live tab, Kids, Music, Photos, Silk, Alexa extras
scripts/rea.sh debloat --tier 3 --apply    # + Alexa voice search, Prime Video, casting, Appstore
scripts/rea.sh restore                     # undo everything
scripts/rea.sh optimize                    # 0.5x system animations, AOT compile, dexopt, cache trim
scripts/rea.sh optimize --cached 8         # also cap cached background apps (Fire OS 8)
scripts/rea.sh clean                       # trim every app's cache + stop background apps
scripts/rea.sh status                      # device, RAM, storage, Home button, debloat state
```

The list lives in [`scripts/debloat-packages.txt`](scripts/debloat-packages.txt) with a description
for every package. It is built from the list cleanyourtv tested on the Fire TV Stick 4K 1st gen
(Fire OS 6.7.1.1): tiers 1 and 2 ran on two sticks with nothing to re-enable; tier 3 survived
reboots on one.

- **Fully reversible.** Packages are only disabled with `pm disable-user --user 0`. Missing ones
  are skipped, and ones Fire OS protects are reported and left alone.
- **Hard-coded refusals.** The scripts refuse to touch Amazon's launcher, setup, settings, input,
  and core services, whatever the list says.
- **Tier 3 trade-offs.** Voice search, Prime Video, casting and Appstore updates stop. Fire OS
  turns voice search back on at every boot. A factory reset re-enables everything.

Inside the app, Control Center › **Free memory** and Settings › Performance stop cached background
apps. Settings › Storage lists apps by size so you can clear caches.

## Build from source

Every push that touches `rea-tv/` builds a signed release APK in GitHub Actions (see
`.github/workflows/rea-tv.yml`). A `rea-tv-v*` tag also publishes a GitHub release.

Locally, you need JDK 17 and the Android SDK:

```bash
cd rea-tv
./gradlew testDebugUnitTest assembleRelease   # → app/build/outputs/apk/release/app-release.apk
```

**Signing.** Without a key, the release APK is signed with the debug key so it installs, but each
CI run uses a fresh debug key, so updating means uninstalling first. For in-place updates,
create a key once:

```bash
keytool -genkeypair -v -keystore rea-release.jks -alias rea -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 rea-release.jks   # → secret REA_KEYSTORE_BASE64
```

Then add the repository secrets `REA_KEYSTORE_BASE64`, `REA_KEYSTORE_PASSWORD`, `REA_KEY_ALIAS`,
and `REA_KEY_PASSWORD`.

## Under the hood

- **Stack.** Kotlin, Jetpack Compose (ui, foundation, and animation only, with no Material),
  minSdk 22, R8 full mode with resource shrinking, and a baseline profile.
- **Liquid Glass without RenderEffect.** Live blur needs Android 12, and Fire OS 8 is Android 11.
  The wallpaper is rendered once at a fraction of screen size, box-blurred on the CPU at
  160 × 90 px, and sampled by each glass surface through a `BitmapShader`. The shader's matrix
  maps the panel to its spot on screen and adds a lens magnification, under a saturation boost.
  Overlays freeze the screen with `PixelCopy` straight into a 256 px bitmap (scaled on the GPU),
  blurred the same way (Android 8+; older versions use the wallpaper). The rim light, glow, and
  sheen are gradients aimed at the shared light; artwork cards get the same edge light on top.
  See `glass/LiquidGlass.kt`.
- **Memory.** Artwork is cached in an `LruCache` sized from the app's memory class (1/7, or 1/12
  on sticks with 1.5 GB or less) and trimmed on `onTrimMemory`. The Top Shelf keeps its own cache
  of at most 5 shelves (2 on those sticks), and lifts each logo once, on a background thread.
  Overlay snapshots are dropped when another app opens.
- **Logo keying.** `data/LogoKey.kt` models the banner's fill from its border (a Coons patch, so
  gradients key cleanly), takes each pixel's alpha from its distance to that fill against the
  strongest logo color nearby, and un-mixes the fill from edge pixels so no halo is left. The shelf
  loads the densest banner the app ships (`getDrawableForDensity`), since it draws it far larger
  than a tile.

```
app/src/main/java/com/reatv/launcher/
  data/     apps, ordering, artwork cache, settings
  glass/    Liquid Glass modifier, backdrop, blur, window snapshot
  system/   Home watchers (log + accessibility), memory booster, storage scan, system intents
  ui/       home grid, tiles, Top Shelf, Control Center, app menu, settings
scripts/    rea.sh / rea.ps1, debloat list, lucide_to_kotlin.py (regenerates ui/LucideIcons.kt)
licenses/   Inter (SIL Open Font License 1.1), Lucide (ISC)
```

---

REA TV is an independent project inspired by the tvOS design language. It isn't affiliated with,
endorsed by, or sponsored by Apple or Amazon, and it contains no Apple or Amazon assets: no SF Pro
(licensed for Apple platforms only), icons, wallpapers or screensavers. Apple TV,
tvOS and Liquid Glass are trademarks of Apple Inc. Fire TV is a trademark of Amazon.com, Inc.
