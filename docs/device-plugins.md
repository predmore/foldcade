# Device plugins

Device protocol 1. Foldcade owns this MIT contract. A **device plugin** is a separate installed app that gives Foldcade typed controls over hardware Android doesn't expose to apps: a fan, controller lights, per-screen backlight and panel power. The first is Foldcade Thor (`app.foldcade.thor`, Apache-2.0, [predmore/foldcade-thor](https://github.com/predmore/foldcade-thor)), which drives the root CLIs of [thor-features](https://github.com/Wingdom/thor-features) on the AYN Thor.

Play plugins ([APK plugins](apk-plugins.md)) call **into** Foldcade's broker. A device plugin is the reverse: **Foldcade calls the plugin**. The plugin may hold root, so it authenticates Foldcade, and it accepts only the typed actions below, never a command string. Foldcade itself never holds root and never runs `su`.

With no device plugin installed, without consent, or with a plugin that reports no capability, Foldcade looks and behaves exactly as it does without this protocol.

## 1. Declaring a device plugin (plugin manifest)

The plugin exports one `ContentProvider` with this intent filter and metadata:

```xml
<provider android:name=".FoldcadeDeviceProvider"
    android:authorities="app.foldcade.thor.device"
    android:exported="true">
    <intent-filter>
        <action android:name="app.foldcade.plugin.action.DEVICE" />
    </intent-filter>
    <meta-data android:name="app.foldcade.plugin.PROTOCOL" android:value="1" />
    <meta-data android:name="app.foldcade.plugin.DEVICE" android:value="ayn-thor" />
</provider>
```

| Key | Value |
| --- | --- |
| `PROTOCOL` | Integer. The highest device protocol the plugin speaks. The host ignores a missing, unparsable or sub-1 value. A plugin above the host's version is used at the host's version. |
| `DEVICE` | The device the plugin controls. Protocol 1 knows `ayn-thor`. The host lists a plugin with an unknown device as unsupported and never calls it beyond `state`. |

The authority is the plugin's package name plus `.device`. The host finds device plugins with `PackageManager.queryIntentContentProviders(Intent(ACTION_DEVICE), GET_META_DATA)`, and declares the action in its manifest `<queries>`. It looks again whenever a package is added, replaced or removed.

## 2. Identity and consent (host)

Identity, the plugin card, the Verified mark, and grants work exactly as in [APK plugins §2](apk-plugins.md#2-identity-and-consent-host): package plus current signer set. A device plugin asks for one scope, `device`. Its switch reads "Control this device", with the caption "It can change the fan, stick lights and screens. Foldcade tells it when a game with its own fan setting starts and stops."

The host calls only a device plugin that holds the `device` grant. At most one device plugin is used: the one granted most recently.

## 3. Caller check (plugin)

Every `call()` first checks its caller. The plugin:

1. Reads `getCallingPackage()` (the `ContentProvider` method, which Android ties to the binder uid) and requires it to be `app.foldcade` or `app.foldcade.debug`.
2. Requires `Binder.getCallingUid()` to equal that package's uid.
3. Reads that package's current signers (`GET_SIGNING_CERTIFICATES`, `signingInfo.apkContentsSigners`, SHA-256 of each) and requires the set to equal the set pinned for that package name, exactly.

Anything else gets `{ok: false, error: "not_allowed"}` and no root work. `query`, `insert`, `update`, `delete` and `openFile` are refused for every caller.

Foldcade Thor pins `app.foldcade` to Foldcade's release certificate (`36f68830…e6581ff`) and `app.foldcade.debug` to the maintainer's debug certificate (`b8069324…74f36bd2`). A fork that wants its own debug Foldcade to drive the plugin adds its digest to the plugin and builds it.

## 4. Methods

`ContentResolver.call(Uri.parse("content://<authority>"), method, arg, extras)`, off the main thread. Every reply is a `Bundle` with boolean `ok`; when false it holds `error` (below) and `message` (English, for logs). The provider doesn't throw for expected failures. Calls are serialised in the plugin: one root command runs at a time.

| Method | Arguments | Reply when `ok` |
| --- | --- | --- |
| `state` | extras `refresh` (boolean, optional) | The state bundle below. Capabilities are probed on the first call and when `refresh` is true; values are read fresh every call. |
| `fan.set` | `arg` = `quiet`, `balanced` or `performance` | The state bundle. |
| `lights.set` | `arg` = `off`, `charge`, `breathe` or `usb` | The state bundle. |
| `brightness.set` | `arg` = `top` or `bottom`; extras float `level`, 0–1 | `level` (float, as applied after clamping to 0.01–1). Kept small so a held D-pad can call it repeatedly. |
| `screens.set` | `arg` = `dual`, `top` or `bottom` | The state bundle. |
| `game.begin` | extras `fan` = `quiet`, `balanced` or `performance` | The state bundle. |
| `game.end` | none | `restored` (boolean: an override was active and has ended), plus the state bundle. |

### The state bundle

| Key | Type | Meaning |
| --- | --- | --- |
| `protocol` | int | The plugin's device protocol. |
| `device` | String | As the manifest's `DEVICE`. |
| `root` | String | `granted`, `denied` (the root manager refused this app), or `absent` (no `su`). |
| `capabilities` | String[] | Which of `fan`, `lights`, `brightness`, `screens`, `gameFan` work now. Empty unless `root` is `granted`. |
| `fan` | String | Present with `fan`. The tier in force: `quiet`, `balanced`, `performance`, or `default` (stock trip points). |
| `fanBaseline` | String | Present with `fan`. The tier the user chose: equal to `fan`, except during a game override, when it is the tier `game.end` restores. |
| `fanGame` | String | Present only while a game override is active: its tier. |
| `lights` | String | Present with `lights`: `off`, `charge`, `breathe` or `usb`. |
| `brightnessTop`, `brightnessBottom` | float | Present with `brightness`: each panel's level, 0–1. |
| `screens` | String | Present with `screens`: `dual`, `top` (bottom panel off) or `bottom` (top panel off). |

Error codes: `not_allowed` (caller check), `no_root`, `unsupported` (the capability isn't there), `bad_request` (unknown method or value), `failed` (the CLI failed, or read back a different value), `internal`.

## 5. What each capability means on the Thor

Each capability is a probe of a thor-features CLI, so an older or missing module hides only its own control. Every action is one fixed command line built from an allowlisted value:

| Capability | Probe | Actions |
| --- | --- | --- |
| `fan` | `fanctl get` exits 0 with a known tier | `fanctl quiet\|balanced\|performance`, verified by `fanctl get` |
| `lights` | `stickfx get` exits 0 with a known mode | `stickfx off\|charge\|breathe\|usb` |
| `brightness` | the `thor-screenbright` module is active (without it the bottom panel follows the top and ignores its own level), and `cmd display get-brightness --id <id>` reads on both panels | `cmd display set-brightness <level> --id <id>` |
| `screens` | `screenblank status` parses | `screenblank on\|off top\|bottom`, ordered so a panel is lit before the other goes dark |
| `gameFan` | `fan` | see §6 |

Display ids come from `screenmove status` (or Android's display list) at runtime. They are never assumed.

`screens.set` only powers panels. It moves no app: Foldcade only darkens a screen it isn't using (§7). A panel lights again on its own whenever the device wakes from sleep, so the host re-applies its wanted mode when a display turns on.

## 6. Per-game fan, and thor-features' appwatch

Foldcade knows which **game** is running, even inside RetroArch; appwatch knows only which **app** is in front. A game can carry its own fan tier in Foldcade. While such a game is running, Foldcade's choice wins. Otherwise appwatch's per-app profiles and the user's tier apply as they do without Foldcade.

`game.begin(fan)`:

1. If no override is active, it reads `appwatch status`. If appwatch's daemon is running, it runs `appwatch stop`, which restores appwatch's own recorded baseline, and remembers to start it again. It does not change appwatch's enabled switch.
2. It records `fanctl get` as the baseline, unless an override is already active, in which case the first baseline is kept.
3. It runs `fanctl <tier>` and verifies it with `fanctl get`.
4. It persists `{tier, baseline, appwatchWasRunning}` in its own storage before replying, so a process death can't lose the way back.

`game.end`, idempotent and cheap when nothing is active:

1. If `fanctl get` still reads the game's tier, it runs `fanctl <baseline>` (including `default`, if that is what the user had). If it reads anything else, someone changed the fan during the game, and that newer choice stays.
2. If appwatch was running, `appwatch start`. Its start-up pass applies the profile of whatever app is in front.
3. It clears the persisted override.

`fan.set` during a game override ends the override first, without restoring: the user took the fan in hand, and that tier stays after the game. appwatch is started again if it was running.

The host calls `game.begin` after a game whose fan tier is set starts, and `game.end` when that play session ends. It also calls `game.end` whenever Home resumes with no play session open, which heals an override left by a crash or force-stop of either app. A reboot during a game also heals at the first Home.

## 7. Where Foldcade shows the controls (host)

Controls appear only for capabilities the granted plugin reports:

- **Settings › Thor** (named for the device; a category shown only with a granted device plugin that has `fan` or `lights`): **Fan** ‹Quiet · Balanced · Performance›, showing `fanBaseline`; **Stick lights** ‹Off · Charging · Breathe · USB›.
- **R1**: with `brightness`, the brightness slider becomes two, **Top** and **Bottom**. Top keeps Android's brightness setting; Bottom goes through `brightness.set`. With `fan`, a **Fan** row shows the tier in force.
- **Y › Fan** on a game ‹Default · Quiet · Balanced · Performance›, with `gameFan`. Default sends nothing at launch.
- **Settings › System › Layout**: with `screens`, Top only powers the bottom panel off instead of dimming it, and a fourth layout, **Bottom only**, keeps Foldcade on the bottom panel and powers the top off. Foldcade lights a panel before a game takes it, darkens it again when the game leaves it, and returns to `dual` when the plugin's consent is turned off or the layout changes to a two-screen one.
- **Settings › Plugins › Foldcade Thor**: the consent switch, and while `root` isn't `granted`, a **Root access** row that says what to do: Magisk 31 never prompts, so the user opens Magisk › Superuser › + and adds Foldcade Thor.

Per-game fan tiers are part of Foldcade's setup backup, like a game's emulator choice. Consent is not.
