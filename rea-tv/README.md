# REA TV

An Apple TV–style home screen for Amazon Fire TV Sticks. It has a Liquid Glass look, a Top Shelf,
a Control Center, focus parallax, and nothing extra. It also comes with ADB scripts that remove
Amazon bloat and tune RAM and storage.

- **~2 MB APK, no internet permission.** No analytics, accounts, or icon packs, and no image
  assets: wallpapers and icons are drawn in code.
- **Built for 1 GB sticks.** Artwork is decoded at tile size, cached by byte budget, and handed
  back to the system as soon as you open an app. Only rows near the focus load artwork.
- **Smooth on weak GPUs.** Focus, scrolling, and glass all run in the draw/graphics-layer phase,
  with no recomposition per frame and no continuous animation while idle.

## What's inside

| | |
|---|---|
| **Home** | tvOS-style grid. The top row owns the **Top Shelf**: the app's name and artwork appear on a glass card, and the wallpaper picks up the app's colors. Deeper rows scroll up and pin near the top. |
| **Focus** | Tiles lift toward you with a deep shadow, tilt from the direction you came, settle with a spring, and catch a specular glint. |
| **Liquid Glass** | Every panel refracts what's behind it (magnified, saturated, blurred backdrop). One shared light glides toward whatever has focus, so rim highlights and glows sweep across all the glass on screen. |
| **Control Center** (☰ Menu button) | Network, Bluetooth, **Free memory**, Storage, Screensaver, Fire TV settings, REA TV settings, Amazon Home, plus live RAM and storage meters. |
| **Hold OK on an app** | Open, Move (jiggle mode, then use the arrows), Move to / remove from Top Shelf, Hide, App info & cache, Uninstall. |
| **Settings** | Wallpapers (Aurora, Ocean, Sunset, Forest, Graphite, Midnight, or Top Shelf colors), glass level (Clear, Regular, Frosted), icon size, labels, 24-hour clock, Reduce motion / transparency, hidden apps, per-app storage, memory booster, device info. The category list has a liquid lens that stretches as it slides. |
| **Home button** | On Fire TV, Home brings you back to REA TV (see below). On Android TV / Google TV, pick REA TV as the Home app. |

## Compatibility

| Device | Works |
|---|---|
| Fire TV Stick 4K / 4K Max (both gens), Lite, 3rd gen, Cube: Fire OS 6, 7, 8 | ✅ |
| Fire TV Stick 2nd gen: Fire OS 5 | ✅ (overlays use the wallpaper as their glass backdrop) |
| Android TV / Google TV boxes | ✅ |
| Fire TV devices on **Vega OS** (e.g. Fire TV Stick 4K Select, 2025) | ❌ No Android apps or sideloading |

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
Amazon's launcher is also a protected package that can't be disabled. So REA TV ships a tiny
accessibility service, **REA TV Home button**, that:

- subscribes to *window-state changes only* and **cannot read screen content**
  (`canRetrieveWindowContent=false`)
- reacts only when Amazon's home screen (`com.amazon.tv.launcher/.ui.HomeActivity_vNext` or any
  activity that answers the HOME intent) comes to the front, so Amazon Settings screens in the
  same package are left alone
- reopens REA TV through an exact alarm. Android holds activity starts from background apps for
  about 5 seconds after Home is pressed, and alarm-delivered starts skip that wait.

You may see Amazon's home for a split second. If you grant the optional `WRITE_SECURE_SETTINGS`
over ADB (`setup` does this), REA TV re-links itself if Fire OS drops the service after an update.

**Known limits:**

- Some Fire OS builds (users report 8.1.5.3+) revoke third-party accessibility services. REA TV
  still works as a normal app; open it from *Your Apps*.
- With any accessibility service on, a few apps (e.g. Prime Video) keep their playback controls
  visible longer.
- `scripts/rea.sh unlink` hands Home back to Amazon at any time.

## Debloat and optimize

```bash
scripts/rea.sh debloat --tier 1            # preview only: shows what would be turned off
scripts/rea.sh debloat --tier 1 --apply    # ads, telemetry, promos (safe)
scripts/rea.sh debloat --tier 2 --apply    # + Amazon Photos, Music, Freevee, Kids, Prime Video…
scripts/rea.sh debloat --tier 3 --apply    # + Alexa voice, system updates, Appstore (advanced)
scripts/rea.sh restore                     # undo everything
scripts/rea.sh optimize                    # 0.5x system animations, AOT compile, dexopt, cache trim
scripts/rea.sh optimize --cached 8         # also cap cached background apps (Fire OS 8)
scripts/rea.sh clean                       # trim every app's cache + stop background apps
scripts/rea.sh status                      # device, RAM, storage, Home button, debloat state
```

The list lives in [`scripts/debloat-packages.txt`](scripts/debloat-packages.txt) with a description
for every package.

- **Fully reversible.** Packages are only disabled with `pm disable-user --user 0`. Missing ones
  are skipped, and ones Fire OS protects are reported and left alone.
- **Hard-coded refusals.** The scripts refuse to touch Amazon's launcher, setup, settings, input,
  and core services, whatever the list says.
- **Tier 3 trade-offs.** It stops security updates, so only use it if you understand that. A
  factory reset re-enables everything.

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
  blurred the same way. The rim light, glow, and sheen are gradients aimed at the shared light.
  See `glass/LiquidGlass.kt`.
- **Memory.** Artwork is cached in an `LruCache` sized from the app's memory class (1/7, or 1/12
  on 1 GB devices) and trimmed on `onTrimMemory`. The top shelf uses a separate cache holding at
  most 2 entries. Overlay snapshots are dropped when another app opens.

```
app/src/main/java/com/reatv/launcher/
  data/     apps, ordering, artwork cache, settings
  glass/    Liquid Glass modifier, backdrop, blur, window snapshot
  system/   Home-button service, memory booster, storage scan, system intents
  ui/       home grid, tiles, Top Shelf, Control Center, app menu, settings
scripts/    rea.sh / rea.ps1 + debloat list
```

---

REA TV is an independent project inspired by the tvOS design language. It isn't affiliated with,
endorsed by, or sponsored by Apple or Amazon, and it contains no Apple or Amazon assets. Apple TV,
tvOS and Liquid Glass are trademarks of Apple Inc. Fire TV is a trademark of Amazon.com, Inc.
