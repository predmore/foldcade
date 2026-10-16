# Contributing

Foldcade's original code, artwork, and music are licensed under [MIT](LICENSE), including the plugin API, bundled plugins, samples, and tools. The plain-language map is [licensing](docs/licensing.md).

Original contributions are accepted under MIT. Preserve the license and attribution notices for third-party material, including the Nunito font and MuseScore General SoundFont.

- [Building](docs/building.md)
- [Plugins](docs/plugins.md)
- [Iconography](docs/iconography.md)
- [Licensing](docs/licensing.md)
- [Releases, versioning, and signing](docs/releases.md)
- [Architecture and CI](docs/architecture.md)
- [Credits](licenses/CREDITS.md)

## Tests

```sh
./gradlew testDebugUnitTest
```

Pull requests run that task, and `.github/release/check-version-bump.sh`.

## Versioning

`versionName` in `app/build.gradle.kts` is `X.Y.Z`. Change it only when the merge should cut a stable tag `vX.Y.Z`. The `versionCode` in that file is the local fallback. CI replaces it with `git rev-list --count HEAD`. A `versionCode`-only edit is not a stable bump. The full rule is in [releases.md](docs/releases.md).

## Signing

Do not commit a keystore. Release signing uses four GitHub Actions secrets. The steps, and what happens if the keystore is lost, are in [releases.md](docs/releases.md).

## Music assets

Do not commit the SoundFont, WAVs, or encoded audio. The render and the license notes are in [music/README.md](music/README.md).

## Minify

Leave minify off for debug and release. If you enable it, the `ServiceLoader` keep rules in [plugins.md](docs/plugins.md) are required. Without them, R8 drops the service file and the entry classes, and the host loads no plugins.
