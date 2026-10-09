# Contributing

Foldcade is licensed under the [GNU GPLv3](LICENSE). A plugin loaded in-process is part of that work when it is distributed.

- [Building](docs/building.md)
- [Plugins](docs/plugins.md)
- [Releases, versioning, and signing](docs/releases.md)
- [Architecture and CI](docs/architecture.md)
- [Credits](licenses/CREDITS.md)

## Tests

```sh
./gradlew testDebugUnitTest
```

Pull requests run that task, and `.github/release/check-version-bump.sh`. The Thor-sized emulator workflow boots only when the change touches the paths listed in [architecture.md](docs/architecture.md). A docs-only change skips that boot. A green emulator run is not a pass on Thor hardware.

## Versioning

`versionName` in `app/build.gradle.kts` is `X.Y.Z`. Change it only when the merge should cut a stable tag `vX.Y.Z`. The `versionCode` in that file is the local fallback. CI replaces it with `git rev-list --count HEAD`. A `versionCode`-only edit is not a stable bump. The full rule is in [releases.md](docs/releases.md).

## Signing

Do not commit a keystore. Release signing uses four GitHub Actions secrets. The steps, and what happens if the keystore is lost, are in [releases.md](docs/releases.md).

## Music assets

Do not commit the SoundFont, WAVs, or encoded audio. The render and the license notes are in [music/README.md](music/README.md).

## Minify

Leave minify off for debug and release. If you enable it, the `ServiceLoader` keep rules in [plugins.md](docs/plugins.md) are required. Without them, R8 drops the service file and the entry classes, and the host loads no plugins.
