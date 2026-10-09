# Releases

`versionName` in `app/build.gradle.kts` is the stable release name. The `versionCode` in that file is the local fallback. A merge to `main` computes `versionCode` with `git rev-list --count HEAD` and publishes a pre-release. A merge that changes `versionName` also creates tag `vX.Y.Z` and a stable GitHub release. That stable build uses the same computed code, so it is higher than every earlier pre-release. An existing tag is left in place.

`versionName` must be `X.Y.Z`, so the tag is `vX.Y.Z`. A `versionCode`-only edit is not a stable bump. Pull requests run `.github/release/check-version-bump.sh` to enforce that. Publishing runs `.github/release/publish.sh` from the Release workflow, and only on a push to `main`.

A debug install is `app.foldcade.debug`. The signed release stays `app.foldcade`, so the two do not replace each other.

## Install with Obtainium

Add `https://github.com/predmore/foldcade`. Set the APK filter to `^foldcade\.apk$`. Every release asset is named `foldcade.apk`.

The badge on the [README](../README.md) adds that GitHub source with the filter, and leaves pre-releases off, so it follows stable releases.

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

## Release signing, once

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

Local release builds read the same keystore through `FOLDCADE_RELEASE_KEYSTORE`, `FOLDCADE_RELEASE_STORE_PASSWORD`, `FOLDCADE_RELEASE_KEY_ALIAS`, and `FOLDCADE_RELEASE_KEY_PASSWORD`. See [building.md](building.md).

## What CI publishes

`.github/workflows/release.yml` runs on a push to `main`, on pull requests, and on `workflow_dispatch`. `cancel-in-progress` stays false. A cancelled run must not strip the APK off a release and leave nothing.

On a push to `main`, the workflow runs unit tests, then a signed release. It refuses to build or upload if any of the four secrets is missing. It decodes the keystore in the runner temp directory, checks the alias, assembles with `-PfoldcadeVersionCode` from `git rev-list --count HEAD`, and refuses to upload unless `apksigner` reports that same key. The uploaded asset is copied to `foldcade.apk`. The keystore file is removed at the end of the job. Home music is rendered in the job before the keystore is decoded, and that step does not receive the signing secrets.

`publish.sh` drops the old `pre-release` tag and creates it again on the built commit before the APK upload, so GitHub's source archive matches the binary. The new pre-release stays a draft until the APK is uploaded, then it is marked a pre-release and not latest. If `versionName` changed, the script also creates tag `vX.Y.Z` and a non-prerelease release. An existing tag that points somewhere else is not recreated. A tag that already points at this commit is left in place, and a missing `foldcade.apk` is filled in.

Pull requests and `workflow_dispatch` assemble an unsigned release with `-PfoldcadeAllowUnsigned=true` to prove that `versionCode`. They do not publish. The workflow does not boot an emulator, and it does not run on tag pushes. Stable releases are cut by the merge to `main`.

The other workflows are in [architecture.md](architecture.md).
