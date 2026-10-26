# REA for Roku TV (exploring)

An Apple TV-style home for Roku TVs, the way REA TV is one for Fire TV. Roku OS is not Android:
there is no ADB, no way to turn apps off, and no way to replace Roku Home. So it can only be:

1. **A developer app** (sideloaded in Roku's Developer Mode) that draws the home screen: the
   dock, the Top Shelf, the app grid and your HDMI inputs.
2. **A small helper** on an always-on device on the same Wi-Fi (a computer, a Raspberry Pi, a
   NAS) that talks to the TV over Roku's External Control Protocol (ECP): it lists your apps,
   opens them, and brings the home screen back when Home lands on Roku Home.

Roku's ECP documentation says apps may no longer send ECP themselves. If that is enforced on your
TV, the developer app can't open your apps on its own, and the helper does all of it. The probe
below finds out before anything bigger is built.

## The probe

`probe/` is a one-screen developer app. It asks the TV, from inside the app, for its details, its
list of apps and what's on screen, then (on OK) tries to open Netflix. It shows OK or BLOCKED for
each and changes nothing.

1. **Allow control over Wi-Fi:** Settings › System › Advanced system settings › Control by
   mobile apps › Network access: **Enabled** (or **Permissive**).
2. **Quick check from your phone** on the same Wi-Fi: open `http://TV-IP:8060/query/apps` in the
   browser. A list of your apps means a helper on your network can control the TV.
3. **Developer Mode:** on the Roku remote press Home ×3, Up ×2, Right, Left, Right, Left, Right.
   Choose *Enable installer and restart*, accept, and set a password.
4. **Install the probe:** on a computer on the same Wi-Fi open `http://TV-IP`, sign in as
   `rokudev` with that password, upload `rea-probe.zip` and press *Install*. Or from this folder:
   `scripts/sideload.sh TV-IP PASSWORD probe`.
5. Photograph the screen, press OK, and photograph it again.

To undo: repeat the Developer Mode button sequence and choose *Disable installer and restart*;
that removes the probe. Nothing else on the TV is touched.

`probe/` is checked with [BrighterScript](https://github.com/rokucommunity/brighterscript)
(`bsc --rootDir probe`), which also builds the zip.
