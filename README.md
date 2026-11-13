# Scrollstop

A free, private Android app that counts the YouTube Shorts and Instagram Reels you watch
and nudges you to watch fewer.

- Counts +1 when you open Shorts/Reels and +1 for every swipe to a new video
- Live "12 / 30" counter at the top of the screen while you're in Shorts/Reels
  (on by default, turns orange at your goal, taps pass through it; switch it off in the app)
- Daily goal (default 30): a reminder at halfway and 3 before the goal
- At the goal, a full-screen "Take a break" card; "Keep watching" unlocks after 5 seconds.
  It returns every 10 videos past the goal
- Every week the goal automatically drops to 85% of last week's daily average (can be turned off)
- 7-day chart and a YouTube vs Instagram split
- No internet permission: nothing leaves the phone

## Get the APK

### Option A: build free on GitHub (no computer needed afterwards)
1. Put this folder in a GitHub repository (keep the `.github` folder).
2. Every push runs the **Build APK** workflow (Actions tab), about 4 minutes.
3. Open the finished run → **Artifacts** → `scrollstop-apk`. Download, unzip, and you have `app-debug.apk`.

### Option B: Android Studio on a laptop
Open the folder in Android Studio → let it sync → **Build → Build APK(s)**.

## Install and turn on
1. Open `app-debug.apk` on the phone. Allow "install unknown apps" for your browser/files app when asked.
   Play Protect may warn about an unknown developer; choose **Install anyway**.
2. Open Scrollstop → **Turn on counting** → Installed apps → Scrollstop → On.
3. If the switch is greyed out ("Restricted setting", Android 13+): Settings → Apps → Scrollstop →
   ⋮ (top right) → **Allow restricted settings**, then repeat step 2.
4. Some phones (Xiaomi, Oppo, Vivo, Realme, Samsung) stop background services to save battery.
   If counting stops after a while: Settings → Apps → Scrollstop → Battery → **Unrestricted / No restrictions**.

## If counting stops working after a YouTube or Instagram update
Those apps don't publish their screen IDs, so an update can rename them. All IDs live in
`app/src/main/java/app/scrollstop/Detect.kt`; updating those lists is the only fix needed.
