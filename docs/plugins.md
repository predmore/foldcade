# Plugins

The contract lives in `:api` ([Apache-2.0](../api/LICENSE)). The host that loads it is `:host`. Samples are `:plugins:sample` and `:samples:out-of-tree`.

## Contract

A plugin is a `PluginEntry` (`app.foldcade.api.plugin.PluginEntry`). One entry may contribute to any of four slots. The host stores each contribution in its own slot. A library and a metadata provider from the same entry stay separate objects.

- `platforms` — a system a library can classify a game into. `id` is canonical. `aliases` are other ids that mean the same platform.
- `players` — an installed app that runs a game for a platform.
- `libraries` — a source of games and saves (`LibraryBackend`). This is not a metadata provider.
- `metadataProviders` — titles, short text, and artwork (`MetadataProvider`). `cached` stays synchronous. `fetch` is the cancellable read.

The service file name is `META-INF/services/app.foldcade.api.plugin.PluginEntry`. That name is part of this contract.

`PluginHost.load` reads every entry that class loader advertises. The caller supplies the loader. This is the path for a bundled jar and for an out-of-tree jar. It does not discover or open an installed package. `load` runs off the main thread. One bad provider is recorded in `rejected` and does not stop the providers that follow. `load` does not throw, except `VirtualMachineError`.

`apiVersion` must equal `PLUGIN_API_VERSION` (1). The host rejects a different major. Set `apiMinor` to `PLUGIN_API_MINOR` (2). That is the additive revision this plugin was compiled against. The host loads a minor less than or equal to its own and rejects a newer minor. The interface default is 0, so a plugin that does not declare a minor still loads, marked as an older minor.

Public value types in `:api` are frozen data classes, or sealed interfaces whose leaves are frozen data classes. Adding, removing, reordering, or changing the type of a constructor property bumps the major. Adding an interface member with a default body bumps the minor and does not bump the major. Open enums and sealed types must be branched with an `else`. Adding a value there bumps the minor. Any other enum or sealed type in the module is closed. Adding a value there bumps the major.

The host calls `PluginEntry.bind` after validation and before the entry is stored. `CredentialAccess` opens credentials only for plugin ids this entry contributed. The default does nothing, so an older plugin minor of this major stays loadable.

Library I/O and metadata fetch are suspending. An implementation must stop that work when the calling coroutine is cancelled, and must rethrow `CancellationException` rather than wrap it in `PluginException`. The shell calls `ensureLocal`, then `prepareLaunch`, then `reconcile`, in that order.

An independent plugin that is not in this repository, and that uses only this API, may use any license. Plugins shipped here stay GPLv3. See [licensing](licensing.md).

## Moonlight

`:plugins:moonlight` (`MoonlightEntry`) is the official Moonlight client, `com.limelight`. The player starts `com.limelight.ShortcutTrampoline` with the host UUID and a string `AppId`. It does not pair, and it does not open Moonlight's database.

`MoonlightLibrary` lists pinned shortcuts, or the list the user confirmed on the import sheet. The left-panel Moonlight row switches between Imported list and Pinned shortcuts only. Switching does not clear either list, and it does not remove the All Apps or Moonlight-folder membership the import recorded. That membership is data for the home grid. The sheet does not draw the grid. `reconcile` does nothing. RomM does not configure this library: the entry has no metadata provider, and reconcile does not call RomM. The official client does not take both displays. The stream starts on the screen the picker targets, and the other screen keeps the picker.

## Samples

`:plugins:sample` (`SampleEntry`) is the in-tree sample. It implements the four interfaces and does nothing else. Library and metadata are separate objects so the host can store them in separate slots.

`:samples:out-of-tree` (`OutOfTreeEntry`) compiles against the API module only. The host does not compile against this module. It is loaded from its jar through `PluginEntry`, the same entry an external plugin uses.

## Reserved RomM ids

`romm` and `romm.metadata` belong to the built-in RomM entry, `app.foldcade.plugins.romm.RommEntry`. Another entry cannot take them, even if it is registered first. The exemption is that class object on this host's class loader. A plugin loader can define a class with the same binary name. That object is a different class, and it is refused.

## Minify

Minify is off for debug and release. Bundled plugins are found by `ServiceLoader` from `META-INF/services/app.foldcade.api.plugin.PluginEntry`. If minify is enabled later, keep rules are required for that service file and for `PluginEntry`. Without them, R8 drops the file and the entry classes, and the host loads no plugins.
