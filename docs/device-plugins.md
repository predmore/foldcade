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

Error codes: `not_allowed` (caller check), `no_root`, `unsupported` (the capability isn't there), `bad_request` (unknown method or value), `needs_color` (`lights.set` only: the mode needs a stick color and every stick LED color is black), `failed` (the CLI failed, or read back a different value), `internal`.

## 5. What each capability means on the Thor

Each capability is a probe of a thor-features CLI, so an older or missing module hides only its own control. Every action is one fixed command line built from an allowlisted value:

| Capability | Probe | Actions |
| --- | --- | --- |
| `fan` | `fanctl get` exits 0 with a known tier | `fanctl quiet\|balanced\|performance`, verified by `fanctl get` |
| `lights` | `stickfx get` exits 0 with a known mode | `stickfx off\|charge\|breathe\|usb`. stickfx refuses `charge` and `breathe` while every stick LED color is `0 0 0`; the reply is then `needs_color`. `off` and `usb` need no color. |
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

Controls appear only for capabilities the granted plugin reports. The host keeps the last state bundle any reply carried and shows it; every change goes through the plugin, and its reply replaces what the host showed. A refused change reads `state` again, so a control never keeps claiming a value the device doesn't have.

The host is level-triggered. It holds what it wants of the device: the panels (from the layout, the session's screens, and any start waiting for a panel), the games' fan tier, and the newest fan tier, lights mode and bottom level the player chose. One worker thread of its own compares that with the state the plugin last confirmed and sends whatever differs, one call at a time, until nothing does. A change, Home resuming or a display turning on wakes it. Nothing is dropped: a call that never returns only keeps the worker busy, and when it returns the worker reads what is wanted then, so it never sends a stale value. A call that fails waits for the next wake rather than retrying in a loop. A held D-pad sends only the newest choice, and until the plugin answers it every state the host shows carries it, so a quick second step starts from the first. Without a granted plugin nothing waits and nothing is sent.

- **Settings › Plugins › Foldcade Thor**: the release row, then the consent switch ("Control this device", with the caption in §2), as for a play plugin. Granting `device` to one plugin turns it off for every other, so the one granted most recently is the one in use; the one it replaces is cleaned up first, as when its consent is turned off. While `root` isn't `granted`, a **Root access** row follows it: Not allowed (`denied`) or Not found (`absent`), in the attention color, with the caption "Magisk won’t ask. In Magisk › Superuser, tap + and add Foldcade Thor." (or "It needs a rooted Thor, as with Magisk." when there is no `su`). Where Magisk's launcher entry resolves (`com.topjohnwu.magisk`), A on it is **Open Magisk**, through Foldcade's Android hand-off; otherwise it is **Check again** (`state` with `refresh`) and the caption ends ", then check again." Whenever Home resumes while `root` isn't `granted`, the host asks `state` with `refresh`, so coming back from Magisk updates the row by itself. The plugin's card reads **Needs root** meanwhile. A plugin whose `DEVICE` this host doesn't know says "Foldcade does not support this plugin’s device yet." and is never called.
- **Settings › Thor** (named for the device; a category between System and Plugins, shown only with `fan` or `lights`, wearing the clamshell): **Fan** ‹Quiet · Balanced · Performance›, showing `fanBaseline`, which reads **Stock** while it is `default`; **Stick lights** ‹Off · Charging · Breathe · Plugged in› (the wire's `usb`: lit while a cable is plugged in). Left and Right step them, and A steps forward, as every choice row does. Choosing a tier sends `fan.set`, which also ends a game's override: the games on the screens when the player chose then keep the player's tier, whether or not their override had begun, until another game starts. After a `needs_color` reply, Stick lights snaps back to the mode in force, offers only Off and Plugged in, and says under it "Charging and Breathe need a stick color. Set one in AYN › RGB lighting."; where AYN's settings can be opened, A goes there through Foldcade's Android hand-off: AynParts' RGB lighting screen (`org.lineageos.settings.ayn/.lights.RGBLightingSettingsActivity`) where it is exported (it isn't in today's AynParts), otherwise AYN's own settings (`.AynSettingsActivity`), one tap from it. Every mode is offered again when the colors may have changed: when Home resumes (as on the way back from AYN's screen), on Check again, or when a `lights.set` goes through.
- **R1**: with `brightness`, the slider becomes two, **Top** and **Bottom**, each named beside the sun. R1 still opens on the first. Top keeps Android's brightness setting and its Allow prompt, and now writes the default display's level alone; Bottom goes through `brightness.set bottom` and needs no Android permission. With `fan`, a **Fan** row under them shows the tier in force (a game's during its override) and steps it with `fan.set`.
- **Y › Fan** on a game ‹Default · Quiet · Balanced · Performance›, with `gameFan`, under Open with, on every tile Foldcade launches as a game: a ROM, a Moonlight stream, an Android game, a GameNative or DroidDeck game. Not an app, an emulator, a folder or a gap. Default sends nothing at launch. The choice is saved per game tile (`game_fan:`), as Open with is. An open menu drops the row if the plugin goes.
- **Settings › System › Layout**: with `screens`, Top only powers the bottom panel off as well as darkening it, and a fourth layout, **Bottom only**, puts the grid, the islands, their menus and every full page on the bottom screen and powers the top off. Bottom only is offered only with `screens`; once chosen it stays until the player steps away from it, even if the plugin goes. In a one-screen layout with `screens` (and in Bottom only always, even before the plugin first answers), A opens a one-screen game on the lit screen whatever Play on says, and X opens it on the dark one; Play on leaves Settings › System there, since it has nothing to choose.

The host keeps the panels at what its session wants: in a two-screen layout `dual`; in a one-screen layout Foldcade's own panel, plus any panel an app or game holds. A start that takes a dark panel (X, a both-screen game, a player that always starts on the top display, Move, a resume, a notice's app) adds that panel to what is wanted and waits for the plugin to confirm it lit, up to 2.5 s, then starts anyway. Before the plugin's first reply, as on a cold start, a panel the layout leaves dark counts as off and waits the same way; once the plugin is known not to power panels, nothing waits. The wait belongs to the launch, so B, a cancel or a newer launch drops it, and the panel stops being wanted. Whenever the session changes hands the panels follow, so a panel is darkened again once the game leaves it. A player whose process died with Home still behind gives its screen up too, unless another game took it since. It also reads `state` and applies it again whenever a display turns on (at once and 1.5 s later, since the device lights its panels as it wakes) and whenever Home resumes. The layout becoming a two-screen one sends `dual`.

Authorization is a generation. Turning the consent off, or granting another device plugin, first wants both panels on and no game override, and waits, up to 8 s, for the worker to finish the call it is in and for the plugin to confirm both, retrying a refused or failed cleanup call every 250 ms; then the generation moves on and the plugin is let go. A state shown on the main thread is checked against the generation there, so one read before the plugin was let go can't bring its controls back. The worker checks the generation before every call, so nothing reaches a plugin let go, and only a new grant lets that package be used again.

Every game Foldcade starts on a screen, one-screen, both-screen APK plugin or stand-in, holds that screen in Foldcade's session until it leaves it. While any game with a tier is on a screen, the highest of their tiers holds (quiet, then balanced, then performance, as appwatch picks): the host sends `game.begin` as such a game takes its screen, again when the tier wanted changes, and `game.end` once no game on the screens has one. Since the host compares with the `fanGame` the plugin reports, an override a crash or force-stop left behind is ended at the next read too: Home resuming, a display waking, or the plugin arriving.

Per-game fan tiers are part of Foldcade's setup backup, like a game's emulator choice. Consent is not.
