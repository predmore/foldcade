# RomM client

Pure JVM client for one RomM server. It is not a library backend and not a metadata provider. Wiring waits until these Android API 33 fixes are green and merged, and until the host pull request moves `PluginEntry`. The shell does not call this module.

Calls suspend on OkHttp. Cancelling the coroutine cancels the OkHttp call for the whole exchange, including a body copy, so a blocked read does not wait out the read timeout. Connect, read, and write timeouts bound every call. Same-scheme redirects are followed. An HTTPS response is not followed onto HTTP.

`:romm:test` depends on Animal Sniffer against the Android API 33 signature (`gummy-bears-api-33`). `testDebugUnitTest` runs that check. A class-file test also fails if the module references `java.net.http`.

## Pinned server

Tested against RomM **5.4.0-alpha.2**. The pinned major is **5**.

Browse, download, and device-code sign-in work on any 5.x. Platform objects from stable 5.3.1 omit `abbreviation` and `alternative_names`. Those fields are optional: the abbreviation falls back to the slug, and missing names are an empty list.

Save sync needs **5.4.0** or newer. `POST /api/sync/negotiate` reads `info.version` from `/openapi.json` first. If that major is greater than 5, the client throws `RommProtocolMismatch` and does not negotiate. The exception carries the server version and the tested major so a later screen can show the mismatch. If the server is older than 5.4.0, the client throws `RommUnsupportedServer` with "server too old for save sync" and does not open a session. A newer minor of major 5, such as 5.9.0, is still negotiated.

RomM 5.1's device-sync document uses a different body. This client does not speak it. A 5.4 response that is missing the tested fields, including `total_delete`, fails to parse and is not applied. Older servers never reach that parser.

The public demo at the time this was written served OpenAPI 5.2.0. That document does not have `rom_ids`, `emulators`, or `total_delete` on negotiate. It was not used as the contract.

## What it calls

Paths below are on the 5.4.0-alpha.2 schema and in the 5.4.0-alpha docs for authentication, downloads, and device sync.

| Call | Path |
| --- | --- |
| Version gate | `GET /openapi.json` |
| Reachability | `GET /api/heartbeat` |
| Device-code sign-in | `POST /api/auth/device/init`, `POST /api/auth/device/token` |
| Platforms | `GET /api/platforms` |
| ROMs | `GET /api/roms` |
| ROM bytes | `GET /api/roms/{id}/content/{file_name}?purpose=play` |
| Register | `POST /api/devices`, `PUT /api/devices/{device_id}` |
| Negotiate | `POST /api/sync/negotiate` |
| Save bytes | `POST /api/saves`, `GET /api/saves/{id}/content` |
| Confirm a download | `POST /api/saves/{id}/downloaded` |
| Finish the session | `POST /api/sync/sessions/{id}/complete` |

`format` is never sent. A 202 from a conversion is surfaced and not retried. Multi-file downloads pass `file_ids` on the ROM content path. A finished cache file is reused. A `.partial` file is resumed with `Range`.

Device registration uses client slug `foldcade`, platform `android`, and `sync_mode` `api`. A stored device id whose client version still matches is left alone.

Device-code scopes: `roms.read`, `platforms.read`, `assets.read`, `assets.write`, `devices.read`, `devices.write`, `firmware.read`. `platforms.read` is the scope `GET /api/platforms` declares. `firmware.read` is not used yet. Play sessions and `tasks.run` are not requested. Username and password are not sent.

Negotiate sends `restore_unlisted: false`. Pass `emulators` for the player that will load the save (`azahar`, `melonds`) so a different emulator's file in the same slot is not paired. `rom_ids` limits downloads to that game.

A conflict uploads the local bytes with a null slot, then writes the server copy. It does not send `overwrite=true`. An upload that cannot reach the server can be copied into `SaveUploadQueue` and retried later. The queue is not a background sync.

Playtime is omitted from the complete call. Remote install, states, and format conversion are not implemented.
