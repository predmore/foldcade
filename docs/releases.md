# Releases

`versionName` in `app/build.gradle.kts` is the stable release name. The `versionCode` in that file is the local fallback. A merge to `main` computes `versionCode` with `git rev-list --count HEAD` and publishes a pre-release. A merge that changes `versionName` also creates tag `vX.Y.Z` and a stable GitHub release. That stable build uses the same computed code, so it is higher than every earlier pre-release. An existing tag is left in place.

`versionName` must be `X.Y.Z`, so the tag is `vX.Y.Z`. A `versionCode`-only edit is not a stable bump. Pull requests run `.github/release/check-version-bump.sh` to enforce that. Publishing runs `.github/release/publish.sh` from the Release workflow on a push to `main`, and on a push of a `v0.x.y` tag that matches `versionName`.

A debug install is `app.foldcade.debug`. The signed release stays `app.foldcade`, so the two do not replace each other.

## Install with Obtainium

Add `https://github.com/predmore/foldcade`. Set the APK filter to `^foldcade\.apk$`. Every release file is named `foldcade.apk`.

The badge on the [README](../README.md) does that for you. It adds the GitHub source, sets the APK filter to `^foldcade\.apk$`, and leaves pre-releases off, so it follows stable releases. The README itself only shows the install steps.

### Stable releases only

Leave **Include pre-releases** off. Obtainium follows the non-prerelease tagged `vX.Y.Z`. The tag is the version, so leave **Use release date as version string** off.

Every main build uses a higher computed `versionCode`, so Android installs it over the previous one. A stable release uses the code computed for that same push.

### Include pre-releases

Turn these on:

- **Include pre-releases**
- **Use latest asset upload as release date**
- **Use release date as version string (pseudo-version)**

A push to `main` updates one pre-release, tag `pre-release`, on the commit that was built, so the source archive matches the APK. The tag name stays `pre-release`, so it is not a version. Obtainium versions a GitHub release by its tag unless the release date is used as the version string. That is why the pseudo-version switch has to be on. See Obtainium discussions [2111](https://github.com/ImranR98/Obtainium/discussions/2111) and [2843](https://github.com/ImranR98/Obtainium/discussions/2843).

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

`.github/workflows/release.yml` runs on a push to `main` and on a `v0.x.y` tag. It does not run on pull requests. `cancel-in-progress` stays false. A cancelled run must not strip the APK off a release and leave nothing. Signing secrets are referenced only in the signed release job.

On a push to `main`, the workflow runs unit tests, then a signed release. It refuses to build or upload if any of the four secrets is missing. It decodes the keystore in the runner temp directory, checks the alias, assembles with `-PfoldcadeVersionCode` from `git rev-list --count HEAD`, and refuses to upload unless `apksigner` reports that same key. The uploaded asset is copied to `foldcade.apk`. The keystore file is removed at the end of the job. Home music is rendered in the job before the keystore is decoded, and that step does not receive the signing secrets.

`publish.sh` moves the `pre-release` tag onto the built commit before the APK upload, so GitHub's source archive matches the binary. It does not delete that tag. A tag delete stays queued on GitHub after the command returns, and that event can force the replacement release back into an untagged draft. It does not delete the release object either. GitHub can return HTTP 500 for that delete and leave the job with no release to upload, and a delete that succeeds leaves a gap before the new release exists. The script updates the release already on the tag: title, notes, target commit, the `foldcade.apk` asset, pre-release, and not latest. It creates a draft only when that release is missing, then publishes it once the APK is uploaded. The script reads the public release for tag `pre-release` and publishes again if that release is missing or still a draft. The job fails unless that release is public, not a draft, on this commit, and its only asset is `foldcade.apk`. If `versionName` changed, the script also creates tag `vX.Y.Z` and a non-prerelease release. An existing tag that points somewhere else is not recreated. A tag that already points at this commit is left in place, and a missing `foldcade.apk` is filled in.

A push of tag `v0.x.y` whose name matches `versionName` signs the same way and publishes a full GitHub release. That release is not a pre-release. The asset name stays `foldcade.apk`, which is what Obtainium's `^foldcade\.apk$` filter matches. Notes committed at `.github/release/notes/v0.x.y.md` are the release body. This tag run does not delete or move the rolling `pre-release` tag. `versionCode` stays `git rev-list --count HEAD`, so it matches the pre-release built from that same commit and is higher than a pre-release on any older commit.

Pull requests and `workflow_dispatch` run `.github/workflows/unsigned-release.yml`. That workflow assembles an unsigned release with `-PfoldcadeAllowUnsigned=true` to prove that `versionCode`. It does not publish, and it has no signing secrets. Neither workflow boots an emulator. Signing runs on a push to `main` and on a `v0.x.y` tag.

The other workflows are in [architecture.md](architecture.md).
