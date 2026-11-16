# KHN AdBlock — team contract

Coordination rules for the four agents (Android Developer, UI/UX Designer,
Security Expert, QA Tester). Read this before touching any file.

## Product

KHN AdBlock is a system-wide ad blocker for Android. It runs a **local**
`VpnService` that only intercepts DNS: queries for ad/tracker domains get a
blocked answer, everything else is forwarded to the chosen upstream resolver.
No traffic leaves the device except normal DNS forwarding and filter-list
downloads. No account, no analytics, no remote server of our own.

## Fixed technical choices

| Item | Value |
|---|---|
| Root | `projects/khn-adblock/` (Gradle project, Kotlin DSL) |
| Package / applicationId | `com.khn.adblock` |
| Language | Kotlin only |
| UI | Jetpack Compose + Material 3 (no XML layouts) |
| minSdk | 26 |
| Modules | `:core` = pure Kotlin/JVM (no Android imports), `:app` = Android app |
| UI contract | `app/src/main/java/com/khn/adblock/ui/state/UiContract.kt` (coordinator-owned) |

`:core` holds all logic that can be tested on a plain JVM: IPv4/IPv6 + UDP
packet parsing/building, DNS message parsing/building, hosts/domain-list
parsing, domain matching (blocklist + allowlist), statistics aggregation.
`./gradlew :core:test` **must work without an Android SDK installed** (the
container this is built in has no Android SDK; see "Build environment").

## File ownership (only the owner edits; others report needed changes)

| Path (under `app/src/main/`) | Owner |
|---|---|
| everything in `:core`, all Gradle files, `AndroidManifest.xml` | Android Developer |
| `java/com/khn/adblock/` except the UI paths below | Android Developer |
| `java/com/khn/adblock/ui/state/UiContract.kt` | Coordinator |
| `java/com/khn/adblock/ui/theme/`, `ui/components/`, `ui/screens/`, `ui/KhnAdBlockApp.kt` | UI/UX Designer |
| `res/values/strings.xml`, `res/values-fr/strings.xml` (UI strings) | UI/UX Designer |
| `res/values/strings_service.xml`, `res/values-fr/strings_service.xml` (notification, errors) | Android Developer |
| `res/values/themes.xml`, `res/values/colors.xml`, `res/drawable*/`, `res/mipmap*/` | UI/UX Designer |
| `design/` (design spec, HTML mockup, screenshots) | UI/UX Designer |
| `docs/SECURITY_AUDIT.md` | Security Expert |
| unit tests written while building `:core` / `:app` logic | Android Developer |
| `docs/QA_REPORT.md`, test files added in the QA phase (named `Qa*Test.kt`) | QA Tester |

Security Expert and QA Tester run after the first two; their rules are in
their own briefs.

## Names both sides rely on

- Theme style referenced by the manifest: `@style/Theme.KhnAdBlock`
  (Material3 DayNight NoActionBar parent; Compose draws everything).
- Launcher icon: `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`
  (adaptive icon, vector foreground).
- Notification small icon: `@drawable/ic_notification_shield` (24dp, white,
  single-colour vector).
- Root composable the Activity calls:
  ```kotlin
  @Composable
  fun KhnAdBlockApp(
      dashboard: DashboardUiState,
      stats: StatsUiState,
      settings: SettingsUiState,
      actions: AdBlockActions,
  )
  ```
  It applies `KhnAdBlockTheme { ... }` itself and contains navigation
  (Dashboard / Statistics / Settings).

## Libraries available to `:app` (Developer declares them, Designer may use them)

Compose BOM, `material3`, `material-icons-extended`, `ui-tooling-preview`
(+ `ui-tooling` debug), `activity-compose`, `navigation-compose`,
`lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, `core-ktx`,
`kotlinx-coroutines-android`, `datastore-preferences`. No other UI library
(no chart library: charts are drawn with Compose `Canvas`). Need another one?
Report it instead of adding it.

## Build environment (honest status)

- Available: JDK 21, Gradle 8.14.3 (`/opt/gradle/bin/gradle`), Maven Central,
  Google Maven (`maven.google.com`), Gradle Plugin Portal,
  `raw.githubusercontent.com`.
- **Not available:** the Android SDK (platforms/build-tools come from
  `dl.google.com`, whose download is currently refused in this session), no
  emulator, no device (`/dev/kvm` absent).
- Consequence: `:app` cannot be compiled here. `:core` can and must be.
  Do not try to download the Android SDK or work around that refusal.
  `settings.gradle.kts` includes `:app` only when an SDK is found
  (`ANDROID_HOME` / `ANDROID_SDK_ROOT` / `local.properties sdk.dir`).

## Git

Agents do **not** commit or push. The coordinator commits after each phase.
