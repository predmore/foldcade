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

Error codes: `not_allowed` (caller check), `no_root`, `unsupported` (the capability isn't there), `bad_request` (unknown method or value), `needs_color` (`lights.set` only: the mode needs a stick colour and every stick LED colour is black), `failed` (the CLI failed, or read back a different value), `internal`.

## 5. What each capability means on the Thor

Each capability is a probe of a thor-features CLI, so an older or missing module hides only its own control. Every action is one fixed command line built from an allowlisted value:

| Capability | Probe | Actions |
| --- | --- | --- |
| `fan` | `fanctl get` exits 0 with a known tier | `fanctl quiet\|balanced\|performance`, verified by `fanctl get` |
| `lights` | `stickfx get` exits 0 with a known mode | `stickfx off\|charge\|breathe\|usb`. stickfx refuses `charge` and `breathe` while every stick LED colour is `0 0 0`; the reply is then `needs_color`. `off` and `usb` need no colour. |
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

Controls appear only for capabilities the granted plugin reports. The host keeps the last state bundle any reply carried and shows it; every change goes through the plugin, and its reply replaces what the host showed. A refused or unanswered change reads `state` again, so a control never keeps claiming a value the device doesn't have. Calls go one at a time, in order, on one thread of the host's own, each bounded by a timeout (20 s for a probing `state`, 3 s for `brightness.set` and for lighting a panel before a start, 10 s otherwise). While a call that timed out is still in the plugin, later calls are dropped rather than queued behind it. A held D-pad sends only the newest fan tier, lights mode or bottom level, and until the plugin answers it, every state the host shows carries that value, so a quick second step starts from the first.

- **Settings › Plugins › Foldcade Thor**: the release row, then the consent switch ("Control this device", with the caption in §2), as for a play plugin. Granting `device` to one plugin turns it off for every other, so the one granted most recently is the one in use; the one it replaces is cleaned up first, as when its consent is turned off. While `root` isn't `granted`, a **Root access** row follows it: Not allowed (`denied`) or Not found (`absent`), in the attention colour, with the caption "Magisk never asks. Open Magisk › Superuser › +, add Foldcade Thor, then check again." (or "It needs root, as Magisk gives." when there is no `su`). A on it is **Check again**: `state` with `refresh`. The plugin's card reads **Needs root** meanwhile. A plugin whose `DEVICE` this host doesn't know says "Foldcade does not support this plugin’s device yet." and is never called.
- **Settings › Thor** (named for the device; a category between System and Plugins, shown only with `fan` or `lights`, wearing the clamshell): **Fan** ‹Quiet · Balanced · Performance›, showing `fanBaseline`, which reads **Stock** while it is `default`; **Stick lights** ‹Off · Charging · Breathe · USB›. Left and Right step them, and A steps forward, as every choice row does. Choosing a tier sends `fan.set`, which also ends a game's override: the games on the screens then keep the player's tier until another game starts. After a `needs_color` reply, Stick lights snaps back to the mode in force, offers only Off and USB, and says under it "Charging and Breathe need a stick colour. Set one in AYN › RGB lighting, then come back for them."; where AynParts' RGB lighting screen (`org.lineageos.settings.ayn/.lights.RGBLightingSettingsActivity`) is exported, A opens it through Foldcade's Android hand-off. Every mode is offered again when the colours may have changed: when Home resumes (as on the way back from AYN's screen), on Check again, or when a `lights.set` goes through.
- **R1**: with `brightness`, the slider becomes two, **Top** and **Bottom**, each named beside the sun. R1 still opens on the first. Top keeps Android's brightness setting and its Allow prompt, and now writes the default display's level alone; Bottom goes through `brightness.set bottom` and needs no Android permission. With `fan`, a **Fan** row under them shows the tier in force (a game's during its override) and steps it with `fan.set`.
- **Y › Fan** on a game ‹Default · Quiet · Balanced · Performance›, with `gameFan`, under Open with, on every tile Foldcade launches as a game: a ROM, a Moonlight stream, an Android game, a GameNative or DroidDeck game. Not an app, an emulator, a folder or a gap. Default sends nothing at launch. The choice is saved per game tile (`game_fan:`), as Open with is. An open menu drops the row if the plugin goes.
- **Settings › System › Layout**: with `screens`, Top only powers the bottom panel off as well as darkening it, and a fourth layout, **Bottom only**, puts the grid, the islands, their menus and every full page on the bottom screen and powers the top off. Bottom only is offered only with `screens`; once chosen it stays until the player steps away from it, even if the plugin goes. In a one-screen layout with `screens`, A opens a one-screen game on the lit screen whatever Play on says, and X opens it on the dark one.

The host keeps the panels at what its session wants: in a two-screen layout `dual`; in a one-screen layout Foldcade's own panel, plus any panel an app or game holds. A start that takes a dark panel (X, a both-screen game, a player that always starts on the top display, Move, a resume, a notice's app) first sends `screens.set dual` and waits for it, up to 2.5 s, then starts anyway. Whenever the session changes hands it applies the wanted panels again, so a panel is darkened again once the game leaves it. It also reads `state` and applies it again whenever a display turns on (at once and 1.5 s later, since the device lights its panels as it wakes) and whenever Home resumes. Turning the consent off sends `screens.set dual` and `game.end` first, while the host may still call the plugin; the layout becoming a two-screen one sends `dual`.

Every game Foldcade starts on a screen, one-screen, both-screen APK plugin or stand-in, holds that screen in Foldcade's session until it leaves it. While any game with a tier is on a screen, the highest of their tiers holds (quiet, then balanced, then performance, as appwatch picks): the host sends `game.begin` as such a game takes its screen, again when the tier wanted changes, and `game.end` once no game on the screens has one. Home resuming, and the plugin arriving or answering anew, send `game.end` too while no game on the screens has a tier, which heals an override left by a crash or force-stop.

Per-game fan tiers are part of Foldcade's setup backup, like a game's emulator choice. Consent is not.
