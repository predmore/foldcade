# foldcade
An open-source gaming frontend built for the AYN Thor.

## Install with Obtainium

Add `https://github.com/predmore/foldcade`. Set the APK filter to `^foldcade\.apk$`. Every release asset is named `foldcade.apk`.

`versionName` in `app/build.gradle.kts` is the stable release name. The `versionCode` in that file is the local fallback. A merge to `main` computes `versionCode` with `git rev-list --count HEAD` and publishes a pre-release. A merge that changes `versionName` also creates tag `vX.Y.Z` and a stable GitHub release. That stable build uses the same computed code, so it is higher than every earlier pre-release. An existing tag is left in place.

A debug install is `app.foldcade.debug`. The signed release stays `app.foldcade`, so the two do not replace each other.

### Stable releases only

Leave **Include pre-releases** off. Obtainium follows the non-prerelease tagged `vX.Y.Z`. The tag is the version, so leave **Use release date as version string** off.

Every main build uses a higher computed `versionCode`, so Android installs it over the previous one. A stable release uses the code computed for that same push.

### Include pre-releases

Turn these on:

- **Include pre-releases**
- **Use latest asset upload as release date**
- **Use release date as version string (pseudo-version)**

A push to `main` recreates one pre-release, tag `pre-release`, on the commit that was built, so the source archive matches the APK. The tag name stays `pre-release`, so it is not a version. Obtainium versions a GitHub release by its tag unless the release date is used as the version string. That is why the pseudo-version switch has to be on. See Obtainium discussions [2111](https://github.com/ImranR98/Obtainium/discussions/2111) and [2843](https://github.com/ImranR98/Obtainium/discussions/2843).

The pseudo-version is a date string. It does not compare with the version Android already installed. After adding the app, use **Mark updated** once so Obtainium treats that install as current. The computed `versionCode` increases on every main build, so each pre-release APK installs over the previous one.

### Release signing, once

The Release workflow reads four GitHub Actions secrets. It fails before it uploads an APK if any secret is missing. Do not commit the keystore. Creating or rotating the secrets is a repository admin action.

`keytool` defaults to a PKCS12 keystore. PKCS12 uses one password, so `RELEASE_KEY_PASSWORD` is the same value as `RELEASE_KEYSTORE_PASSWORD`.

Back up `foldcade-release.jks` outside this repository. If that file is lost, the next APK is a different signing key. Android will not install it over the app that is already there, and Obtainium cannot update that install.

```sh
keytool -genkeypair -v \
  -keystore foldcade-release.jks \
  -alias foldcade \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Foldcade, O=Foldcade"
```

`keytool` asks for that one password. Use it for both secrets below.

```sh
base64 -w 0 foldcade-release.jks
```

On macOS: `base64 -i foldcade-release.jks | tr -d '\n'`

In the repository: Settings → Secrets and variables → Actions → New repository secret.

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | the base64 line |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password |
| `RELEASE_KEY_ALIAS` | `foldcade` |
| `RELEASE_KEY_PASSWORD` | the same keystore password |

## Plugins

Minify is off for debug and release. Bundled plugins are found by `ServiceLoader` from `META-INF/services/app.foldcade.api.plugin.PluginEntry`. If minify is enabled later, keep rules are required for that service file and for `PluginEntry`. Without them, R8 drops the file and the entry classes, and the host loads no plugins.
