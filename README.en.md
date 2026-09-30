[Русский](README.md) | **English**

# Headache Diary (Android)

A headache and migraine diary for Android. One tap records the exact start time of an attack. The app has widgets,
a calendar and statistics, optional weather and air pressure, optional watch data through Health Connect, and a PDF
report for your doctor. All data stays on your phone.

## Download
A ready-to-install APK (Android 8.0 or newer) is available in [Releases](../../releases/latest). Open the file on your
phone and allow installing from this source. The app is signed with the author's key; its SHA-256 checksum is given in
the release description.

Built with Kotlin, Jetpack Compose and Room.

## Building
1. Install Android Studio (it includes the SDK and a JDK).
2. File → Open → choose `settings.gradle.kts` in the project root and wait for the Gradle sync.
3. Connect a phone (USB debugging) or create an emulator and press Run ▶.
4. Tests: `Run → Run 'All Tests'` in `app/src/test` (local unit tests, no phone needed).
5. Signed release build: see [RELEASE.md](RELEASE.md) (in Russian).

## Features
- The "My head hurts" button saves the current time as the start of an attack immediately; "Pain is over" closes the attack.
- Two widgets: a pain button (start and end of an attack in one tap) and a "Week" widget (the last 7 days in color plus a
  "No pain today" button).
- Type: migraine or regular headache; intensity 1–10; symptoms; triggers; medication and its effect; notes.
- A monthly calendar colored by intensity, statistics, and a chart of pain intensity for the last 30 days.
- Pain-free days can be marked (on the main screen, in the calendar, in the evening reminder and in the "Week" widget).
- Factors of the day (stress, caffeine, alcohol, water, meals) for any day; the statistics compare days with and without pain.
- Weather and pressure (optional): the temperature, the air pressure and its change over 3 and 24 hours for every attack,
  compared with the usual weather in the same place. The source is Open-Meteo with MET Norway as a backup; only coordinates
  rounded to about 1 km leave the phone.
- A pressure outlook for the next 24 hours on the main screen and a morning notification about an expected swing (it stays
  quiet if your entries show that swings rarely precede your attacks).
- Watch data from Health Connect (optional; for example Nothing X / CMF Watch): the sleep before an attack, the steps over the
  previous day and the resting heart rate, plus (with a separate switch) the dates of menstruation to check the window
  around the start of a period. Read-only; the data stays on the phone.
- An evening reminder, "Did you have a headache today?".
- A PDF report for your doctor for the chosen period: a summary, a chart of pain intensity and pressure, and a list of attacks
  in plain language.
- A JSON backup with restore (no duplicates) and an automatic nightly backup into a folder you choose.
- An app lock with your fingerprint or the screen lock code (it hides the content from screenshots and the recent apps list).
- Theme (light, dark or system) and language: Russian, Ukrainian, English, German, or the system language.

## Icon
The source artwork is `design/app-icon.png` (transparent corners). The adaptive icon is built from it: the artwork layer
`res/drawable-*dpi/ic_launcher_foreground.png` (the art fills 85% of the safe zone and its edge fades into the background)
and the gradient background `res/drawable/ic_launcher_background.xml`, matched to the colors of the picture's corners. To
replace the icon, add a new picture and regenerate the layers (in Android Studio: right-click `res` → New → Image Asset).

## Localization
Strings live in `app/src/main/res/values/strings.xml` (English, the default) and in `values-ru`, `values-uk` and `values-de`.
To add a language, create `values-<code>/strings.xml` with the same keys, add the language to `AppLanguage`
(`settings/AppSettings.kt`) and a chip to `SettingsScreen`.

## Notes
- The app is not a substitute for medical advice; the conclusions in the statistics are only hints based on your own entries.
- `androidx.health.connect:connect-client` is pinned to `1.1.0-beta01`: newer versions require compileSdk 36 and AGP 8.9.1 or later.

## License
[MIT](LICENSE). The app uses data from Open-Meteo.com (CC BY 4.0), MET Norway and OpenStreetMap Nominatim: if you reuse
it, please follow the terms of these services.
