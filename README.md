# In Real Life Hero

An Android app that turns time away from your phone into a game. Every minute your phone stays
locked is a minute in real life: the app tracks it, shows charts and records, and lets you share
your daily, weekly and monthly stats as text or as an image.

## How IRL time is measured

- A session starts when the phone is locked (screen off) and ends at the next unlock.
- Looking at the lock screen (time, notifications, the optional stats card) is allowed and does
  not end the session. Calls placed or answered without unlocking count as real life too.
- Sessions shorter than the minimum length (default 2 min) are discarded.
- Time inside the sleep window (default 23:00–07:00) is not counted.

The rules live in [`SessionBuilder`](app/src/main/java/com/clobrano/irlhero/domain/SessionBuilder.kt)
and [`StatsCalculator`](app/src/main/java/com/clobrano/irlhero/domain/Stats.kt), with unit tests in
`app/src/test`.

## What's in the MVP

- **Today**: IRL time, goal ring, IRL ratio, last session, streak, 24 h timeline, sessions list.
- **Stats**: week bar chart with the goal line; month calendar heatmap.
- **Records**: longest session, best day/week/month, fewest unlocks; current week and month.
- **Hero**: Hero Points, levels (Rookie → Myth) and streak with a weekly shield.
- **Sharing**: text or PNG card (post 1080×1350 or story 1080×1920, light or dark) through the
  Android share sheet, for today, the week, the month or a record.
- **Celebrations** for new records and goal days appear when you open the app. There are no push
  notifications.
- **Optional lock-screen card** (off by default): a live IRL timer and today's stats while the
  phone is locked.
- **Settings**: goal, sleep hours, minimum session, theme, tracking health, CSV export, delete all.

## Privacy

Data never leaves the phone and the app has no internet permission. It reads only screen and
lock event types from the system usage log (Usage Access), never which apps you use.

## Technical notes

- Kotlin, Jetpack Compose, Material 3, Room, DataStore, WorkManager. Min SDK 28.
- Events come from `UsageStatsManager` (`SCREEN_INTERACTIVE`, `SCREEN_NON_INTERACTIVE`,
  `KEYGUARD_SHOWN`, `KEYGUARD_HIDDEN`, device startup/shutdown). The app syncs when it is opened;
  a daily WorkManager job only copies events before Android purges them.
- The lock-screen card is a `specialUse` foreground service that runs only while the card is on.

## Build

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
