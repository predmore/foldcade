# Building

Local debug and release builds. Version codes, Obtainium, and the release keystore are in [releases.md](releases.md). The plugin keep rules are in [plugins.md](plugins.md).

## What you need

- JDK 21. CI uses Temurin 21.
- Android SDK packages: `platforms;android-33`, `platforms;android-37.0`, `build-tools;37.0.0`, and `platform-tools`. Do not install the legacy `tools` package. Current cmdline-tools do not ship it.
- `compileSdk` is `release(37)`, so compilation needs `platforms;android-37.0`. `platforms;android-33` is the min and target platform. `minSdk` and `targetSdk` are 33. App bytecode is Java 17.
- The Gradle wrapper is 9.6.0. Use `./gradlew`.
- Theme packaging needs `python3` (standard library only). Builds and language tests validate and generate the bundled Afterglow ZIP from `themes/afterglow/`; generated archives are not committed.
- Home music rendering needs `fluidsynth`, `ffmpeg`, `python3`, `python3-venv`, and `libsndfile1`. The build downloads MuseScore General. That SoundFont is not committed.

On Debian or Ubuntu:

```sh
sudo apt-get install fluidsynth ffmpeg libsndfile1 python3-venv
```

Set `ANDROID_HOME` to the SDK root before you build.

## Debug APK

```sh
./gradlew :app:assembleDebug
```

The debug application id is `app.foldcade.debug`. The signed release stays `app.foldcade`, so the two do not replace each other.

`assembleDebug` and `assembleRelease` both render home music into the APK. To copy an already-rendered tree instead of running fluidsynth, pass a directory that contains `music/manifest.json` and an `.ogg` file:

```sh
./gradlew :app:assembleDebug -PfoldcadeHomeMusicAssets=/path/to/rendered
```

The render itself, and what must not be committed, is in [music/README.md](../music/README.md).

## Release APK

The release build fails before it can write an unsigned APK. No debug key stands in. Set the four environment variables, or pass `-PfoldcadeAllowUnsigned=true` for a dry-run that must not be published.

| Variable | Value |
| --- | --- |
| `FOLDCADE_RELEASE_KEYSTORE` | path to `foldcade-release.jks` |
| `FOLDCADE_RELEASE_STORE_PASSWORD` | the keystore password |
| `FOLDCADE_RELEASE_KEY_ALIAS` | `foldcade` |
| `FOLDCADE_RELEASE_KEY_PASSWORD` | the same keystore password |

`keytool` defaults to a PKCS12 keystore. PKCS12 uses one password, so the key password is the same value as the store password. Creating the keystore, backing it up, and the GitHub Actions secrets are in [releases.md](releases.md). Do not commit the keystore.

```sh
./gradlew :app:assembleRelease
```

The unsigned dry-run is what pull requests and `workflow_dispatch` run. It does not publish:

```sh
./gradlew :app:assembleRelease -PfoldcadeAllowUnsigned=true
```

CI passes `-PfoldcadeVersionCode` from `git rev-list --count HEAD`. Locally, `versionCode` in `app/build.gradle.kts` is the fallback (`1`) unless you pass that property. `versionName` in that file is the stable release name.

```sh
./gradlew :app:printFoldcadeIdentity
```

That prints `versionCode`, `versionName`, and the debug and release application ids.

## Tests

```sh
./gradlew testDebugUnitTest
```

`:api`, `:host`, `:romm`, and every `:plugins:*` module apply `foldcade.jvm-api33`. Animal Sniffer fails the build when those modules call an Android API above 33. `testDebugUnitTest` depends on that check.

## Lint

```sh
./gradlew lintDebug
```

That runs `:app:lintDebug` and `:language:lintDebug`. The build fails on lint errors. Warnings stay warnings. CI runs the same task.

## Theme contract

`themes/contract.json` and `themes/theme.schema.json` are written from the engine. When a test says either file is stale:

```sh
./gradlew updateThemeContract
```

That rewrites both files. Commit the result.

## Icons

```sh
python3 tools/art/icons.py
```

The script uses the Python standard library only. It rewrites `themes/afterglow/icons/`, the SVG sources under `art/svg/`, and the icon slots in `themes/afterglow/theme.json`. CI runs it and fails if that output differs. `ThemeSceneTest` counts those slots, so there is no icon count to bump by hand.

`tools/art/build.py` draws its previews with Afterglow's Nunito at `themes/afterglow/font.ttf`.

## Baseline profile

The release includes an app profile. See [baseline-profile.md](baseline-profile.md) to regenerate it on a Thor and measure startup and controller frames.

## Minify

Minify is off for debug and release. `app/build.gradle.kts` does not enable it. If it is enabled later, the keep rules in [plugins.md](plugins.md) are required.
