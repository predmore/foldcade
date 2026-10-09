# Contributing

Foldcade is licensed under the [GNU GPLv3](LICENSE), with the plugin exception in that file. The plugin API in `api/` is [Apache-2.0](api/LICENSE). The plain-language map is [licensing](docs/licensing.md).

Contributions are accepted under these terms. Inbound equals outbound: a contribution is licensed the same way as the file it changes. A change under `api/` is Apache-2.0. A change to the rest of the program is GPLv3, including the plugin exception. A file that already names another license stays under that license.

- [Building](docs/building.md)
- [Plugins](docs/plugins.md)
- [Licensing](docs/licensing.md)
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
