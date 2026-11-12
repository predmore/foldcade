# APK plugins

Protocol 1. Foldcade owns this MIT contract. APK plugins implement it in their own code. The first is Foldcade Moonlight (`app.foldcade.moonlight`, GPL-3.0). The bundled, in-process API is separate: see [plugins](plugins.md).

## Boundary

Foldcade's in-process plugins (`:api`, loaded by `ServiceLoader`) can't come from another APK. A GPL plugin also can't be linked into MIT Foldcade. An **APK plugin** is a separate installed app. It and Foldcade use only documented Android interfaces: an exported activity, an exported `ContentProvider` `call()`, and package metadata. Nothing is linked across the boundary, and the plugin doesn't depend on `:api`.

## Terms

- **Host**: Foldcade, `app.foldcade` (release) or `app.foldcade.debug` (debug).
- **Plugin**: an installed package that declares the play activity below.
- **Access scope**: a named kind of data or authority the host can lend a plugin. Protocol 1 defines one scope, `gamestream`.

## 1. Declaring a plugin (plugin manifest)

The plugin exports one activity with this intent filter and metadata:

```xml
<activity android:name=".foldcade.FoldcadePlayActivity" android:exported="true" ...>
    <intent-filter>
        <action android:name="app.foldcade.plugin.action.PLAY" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <meta-data android:name="app.foldcade.plugin.PROTOCOL" android:value="1" />
    <meta-data android:name="app.foldcade.plugin.PLATFORM" android:value="moonlight" />
    <meta-data android:name="app.foldcade.plugin.SCREENS" android:value="both" />
    <meta-data android:name="app.foldcade.plugin.ACCESS" android:value="gamestream" />
</activity>
```

| Key | Value |
| --- | --- |
| `PROTOCOL` | Integer. The highest protocol the plugin speaks. The host ignores a plugin with a missing or unparsable value, or one below 1. A plugin above the host's version is used at the host's version. |
| `PLATFORM` | The Foldcade platform id the plugin plays. Protocol 1 knows `moonlight`. The host lists a plugin with an unknown platform as unsupported and never starts it. |
| `SCREENS` | `both` or `one`. `both` maps to `occupiesBothDisplays = true`: the host gives the plugin both panels and starts it on the top display. Anything else means `one`. |
| `ACCESS` | Comma-separated scopes the plugin may ask for. Unknown scopes are ignored. A request doesn't grant anything. |

The host finds plugins with `PackageManager.queryIntentActivities(Intent(ACTION_PLAY), GET_META_DATA)`. Its manifest declares `<queries><intent><action android:name="app.foldcade.plugin.action.PLAY"/></intent></queries>`. It looks again whenever a package is added, replaced, or removed. Each plugin's label and icon are the activity's.

## 2. Identity and consent (host)

A plugin is identified by **package name plus its current signing certificates** (`GET_SIGNING_CERTIFICATES`, SHA-256 of each signer, using `signingInfo.apkContentsSigners`, or the current signer when there's signing history).

- Settings › Plugins lists every discovered plugin: its label, version, package, and a short signer fingerprint (the first 4 bytes of the SHA-256, written as hex pairs).
- For each scope a plugin asks for, the user can turn on a switch with consent copy. For `gamestream` the switch reads roughly: "Share paired computers". Its consent text reads: "Foldcade Moonlight can see your paired computers and stream the games you start in Foldcade. It can also quit games on them. Your pairing key stays in Foldcade."
- A grant is stored as `(package, sorted signer SHA-256 set, scope)`. If the signer set changes, the plugin counts as new and has no grant. Uninstalling drops its grants. Grants are excluded from backup and device transfer.
- Turning a grant off takes effect on the next broker call.

## 3. The broker (host `ContentProvider`)

Authority: `<host applicationId>.plugins`, so `app.foldcade.plugins` or `app.foldcade.debug.plugins`. The provider is exported with no Android permission. Every call checks the caller in code.

The plugin calls `ContentResolver.call(Uri.parse("content://<authority>"), method, arg, extras)` off the main thread. Every reply is a `Bundle` with boolean `ok`. When `ok` is false, it also holds `error` (a code from the table below) and `message` (English text for logs, not for users). The provider doesn't throw for expected failures.

Caller check: `getCallingPackage()` (the `ContentProvider` method, which Android ties to the binder uid) must be a discovered plugin. The plugin's current signer set must match a stored grant for the method's scope. Otherwise the reply is `not_granted`.

| Method | Scope | Arguments | Reply when `ok` |
| --- | --- | --- | --- |
| `hello` | none | none | `protocol` (int, host's), `granted` (String[] of the caller's granted scopes; empty if none), `hostPackage` |
| `appearance` | none | none | the current Foldcade theme's tokens, so a plugin can match it: ARGB ints `background`, `surface`, `onBackground`, `muted`, `focus`, `focusRingStart`, `focusRingEnd`, `attention`; floats `round` (0–0.5, the theme's corner roundness) and `cardCornerDp` (0–64). A plugin uses its own defaults for anything missing, and for an older host that replies `bad_request`. |
| `gamestream.hosts` | `gamestream` | none | `hosts`: `ArrayList<Bundle>`, one for each computer **Foldcade paired with itself**, with `uuid`, `name`, `address` (the host or IP Foldcade last reached it at), `httpPort` (int), `httpsPort` (int, 0 when unknown), and optional `mac` |
| `gamestream.request` | `gamestream` | `arg` = host uuid; extras `path` (String), `query` (String, optional, without a leading `?`), `token` (String, optional on any path, required for `launch` and `resume`) | `status` (int HTTP status), `body` (String, UTF-8 XML) |

`appearance` requires discovery only. Each call reads the selected theme's palette, `style.colors.attention`, `style.shape.round` and `style.metrics.cardCornerDp` (capped at 64). Ring ends use `style.focusRing.start` and `end`, each falling back to `focus`, as Foldcade's focus ring does. Cold calls use the same bounded startup wait as `gamestream.hosts`, then fall back to the bundled Afterglow paint if the selected theme is unavailable. Replies contain only tokens, never theme files or paths.

`gamestream.request` rules:

- `path` must be one of `serverinfo`, `applist`, `launch`, `resume`, or `cancel`. Anything else is `bad_request`. Pairing and unpairing are never brokered.
- The host makes the request over HTTPS to that paired host. It presents its keystore identity and pins the certificate it recorded when pairing, the same as `:gamestream` does today. It drops any `uniqueid` or `uuid` parameter in `query` and adds its own. Every other parameter passes through unchanged: `appid`, `mode`, `additionalStates`, `sops`, `rikey`, `rikeyid`, `localAudioPlayMode`, `surroundAudioInfo`, `remoteControllersBitmap`, `gcmap`, `gcpersist`, `hdrMode`, `clientHdrCap*`, and so on. `query` is limited to 4096 characters and may not contain `#` or a newline.
- `launch` and `resume` need `token`: the `LAUNCH_TOKEN` of a play start the host sent **to this caller package, for this host uuid, within the last 10 minutes**. `query` must name the app by `appid`, every `appid` must equal that start's `APP_ID`, and it may not contain `appuuid`, which Apollo can use to pick a different app. Otherwise the reply is `not_launched`. A token can be used more than once in its window, so retries work. Any `gamestream.request` (any path) may carry `token`; a request whose token is valid for its caller and host moves that token's expiry to 10 minutes after the request, so a plugin keeps a long session's token alive by making a request at least every few minutes (Foldcade Moonlight sends `serverinfo` every 2 minutes while streaming). An invalid token on `serverinfo`, `applist` or `cancel` is ignored, not refused. The host keeps tokens only in memory. `serverinfo`, `applist` and `cancel` need no token. This means only a game Foldcade chose to start can be started. Any app can send the plugin a PLAY intent, but it can't make the plugin start a game on the user's PC.
- Timeouts: `launch` and `resume` allow 60 s to read. The rest use the usual LAN timeouts.
- The reply body is capped at 1 MiB. A larger reply is `host_error`.

Error codes: `not_granted`, `not_launched` (a missing, expired, or mismatched launch token), `unknown_host` (no Foldcade pairing has that uuid), `bad_request`, `unreachable` (connect or TLS failure, or a timeout), `host_error` (HTTP failure, or an oversized or unreadable reply), `internal`.

Host implementation limit: Android Binder has a shared 1 MiB transaction buffer and parcels strings as UTF-16. The host also returns `host_error` for decoded XML over 384 Ki characters, reserving room for the reply parcel. The network body cap remains 1 MiB. This transport limit is stricter than the protocol's byte cap; a larger-body transport would require a later protocol.

Why the broker is the design: the GameStream client key is non-exportable in Android's keystore. Only the five HTTPS calls above need it. RTSP, video, audio, and control streams are encrypted with the session's `rikey`, which the plugin generates and passes in `launch`/`resume`. So the plugin streams directly from the PC while the key stays in Foldcade, and revoking a grant takes effect immediately.

## 4. Starting a game (host → plugin)

The host starts the plugin's play activity explicitly, by its component, with `FLAG_ACTIVITY_NEW_TASK`, on the top display (`ActivityOptions.setLaunchDisplayId`) when `SCREENS=both`, or on the chosen panel when `one`.

Extras, all prefixed `app.foldcade.plugin.extra.`:

| Extra | Type | Meaning |
| --- | --- | --- |
| `PROTOCOL` | int | The protocol in use: min(host, plugin). |
| `BROKER_AUTHORITY` | String | The broker's authority. |
| `TITLE` | String | The game's title as Foldcade shows it. |
| `LAUNCH_TOKEN` | String | An opaque random value, at least 128 bits, new for every play start. See `gamestream.request`. |
| `TOP_DISPLAY` | int | Display id of the top panel. |
| `BOTTOM_DISPLAY` | int | Display id of the bottom panel, or -1 when there isn't one. |

For the `moonlight` platform, the extras are prefixed `app.foldcade.gamestream.extra.`:

| Extra | Type | Meaning |
| --- | --- | --- |
| `HOST_UUID` | String | The paired host's uuid, exactly as `gamestream.hosts` reports it. |
| `HOST_NAME` | String | The computer's name. |
| `APP_ID` | String | The decimal GameStream app id. |
| `APP_NAME` | String | The app's name on the host. |

Fresh starts and resume starts each receive a new token. Tokens expire after ten minutes without a matching broker request, measured with `elapsedRealtime`, and never travel in saved state or backups.

The plugin checks that `BROKER_AUTHORITY` resolves (`resolveContentProvider`) to a package it trusts as a host. Foldcade Moonlight trusts `app.foldcade` and `app.foldcade.debug`. Otherwise it refuses to start.

## 5. Routing (host)

A Moonlight game goes to the plugin when **all** of these hold:

- an installed plugin declares `PLATFORM=moonlight`,
- that plugin holds the `gamestream` grant,
- its "Use for Moonlight games" setting is on (default on once the grant is given). One plugin at a time has it on: granting a plugin, or turning its setting on, turns it off for every other plugin, so the most recent choice wins,
- and the game's host uuid is one Foldcade paired with itself.

Otherwise the game goes to the official Moonlight app exactly as before. That covers pins of computers only official Moonlight paired, and the case where no plugin is installed. Foldcade-paired games launch through the plugin even when official Moonlight isn't installed.

## 6. Play session (plugin)

- With `SCREENS=both`, the plugin owns both panels until it finishes. The stream goes on `TOP_DISPLAY`. The plugin opens its own bottom-screen activity on `BOTTOM_DISPLAY`.
- When the session ends (the user disconnects, the user quits the game, or the host or network ends it), the plugin finishes its activities on both displays. Foldcade, as the home app, returns on both screens through its existing external-play return path.
- The plugin keeps the controller driving the stream no matter which screen was touched last.

## 7. Installing a plugin from Foldcade

Settings › Plugins also lists the plugins in Foldcade's **registry** that aren't installed yet, and offers updates for installed ones. The registry is `PluginRegistry` in `:language`. It names trusted repositories, never versions: a plugin releases on its own schedule, so pinning a version in Foldcade would hold it back or fall out of step. Foldcade asks each repository for its latest release when it checks.

Each entry has these fields:

| Field | Meaning |
| --- | --- |
| Package | The application id the APK must have. |
| Label, detail, mark | The catalog tile's name, the line under its page title, and its icon. |
| Repository | GitHub `owner/name`. Foldcade reads `https://api.github.com/repos/<owner>/<name>/releases/latest`. |
| Asset | The one release file Foldcade downloads. Other assets, such as a source archive, are ignored. |
| Signers | The SHA-256 of each signing certificate, as lowercase hex. The APK's current signer set must equal it exactly. |

Protocol 1's registry has one entry:

| Field | Value |
| --- | --- |
| Package | `app.foldcade.moonlight` |
| Repository | `predmore/foldcade-moonlight` |
| Asset | `foldcade-moonlight.apk` |
| Signers | `36f68830…e6581ff`, Foldcade's release certificate. First-party plugins are signed with Foldcade's own key. |

Trust is pinned in the entry. It doesn't depend on how this copy of Foldcade is signed, so a debug Foldcade installs the same release a signed one does. Registry entries are checked when the registry loads: a plain `owner/name`, a plain `.apk` file name, a package name, at least one signer, and no package twice.

### What a plugin repository publishes

- Its newest full release (not a draft or pre-release) is `releases/latest`.
- That release has exactly one asset with the registered name, at `https://github.com/<owner>/<name>/releases/download/<tag>/<asset>`, no larger than 128 MiB.
- The APK has the registered package and is signed with the pinned certificates.
- Each release's `versionCode` is higher than the last.
- Optionally, a line `versionCode: N` in the release notes, with the APK's `versionCode`. Foldcade's and Foldcade Moonlight's release scripts write it. With it, a check can say **Update available · <version>** or **Up to date** without downloading anything. The version shown is the tag without its leading `v`. The line is only a hint: the downloaded APK still decides.

### Checking and installing

Foldcade checks every registry plugin when it starts. Opening a registry plugin's page, in the catalog or once it's installed, checks again, unless a check read GitHub in the last 10 minutes. **Check for updates** (or **Check again**) always checks. Nothing installs on its own: Install and Update start only when the user chooses them, and Android asks the user to confirm.

Install flow: fetch the release JSON and the asset through `:net` `PublicHttps` (with the GitHub API, github.com, and the release-asset download hosts added to its list). Stage the APK in the cache. Check it with `getPackageArchiveInfo(GET_SIGNING_CERTIFICATES)`: the registered package, a `versionCode` above what's installed, and the pinned signer set. An APK that isn't newer reads **Up to date** and isn't installed. After a first install from the catalog, Foldcade opens the plugin's page with its consent switch focused. After an update, focus stays on the Update row. Install it with a `PackageInstaller` session (`REQUEST_INSTALL_PACKAGES`) that requires the user's confirmation. When Android says Foldcade can't request installs, the page explains that and opens `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for Foldcade. Once the package arrives, its row asks for the scopes it wants.

A plugin installed another way, such as with `adb`, a debug build, or a third party, is discovered the same way and needs the same consent. The catalog's trust check applies only to installs Foldcade downloads itself.
