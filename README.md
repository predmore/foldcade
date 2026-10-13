# Foldcade

**A calm, two-screen home for your AYN Thor.**

Open the clamshell and your whole collection is waiting. The game you are thinking about fills the top screen with its own art. Everything else sits on a shelf just below it, a press away. Soft music plays while you browse. When you pick something, the music fades, Foldcade steps aside, and the game takes over.

Foldcade is free, open source, and made for one device: the Thor.

<!--
Hero clip and pair. Captured on a real Thor over adb, not an emulator.
The clip stacks both screens' screenrecords; the pair is one focused game.
Keep the hero-top.png and hero-bottom.png paths.
-->

<p align="center">
  <img src="docs/images/hero-browse.webp" alt="The Thor's two screens. The d-pad moves across a grid of 3DS box art on the bottom screen, and the top screen changes to each game's own art and logo: Bravely Second, Fantasy Life, Luigi's Mansion, and Super Mario 3D Land." width="640">
</p>

<p align="center"><em>Captured on a real Thor. Move on the bottom screen, and the top screen becomes the game.</em></p>

[![Get it on Obtainium](docs/images/obtainium.png)](#get-foldcade)

## Why Foldcade

Most Android launchers are built for one screen. The Thor has two, and they usually end up feeling like a phone with a spare. Foldcade treats them as one home, inspired by the handhelds that made two screens feel natural.

- **Two screens that work together.** The top screen shows off what you have selected. The bottom screen is where you browse. Nothing is cramped, and nothing is wasted.
- **Made for buttons, not fingers.** Move with the D-pad. A plays on the top screen, X on the bottom, and Y opens options for whatever you are on. L1 and R1 open your drawers. Every menu shows the one button to press next, so there is nothing to memorize.
- **Quiet by design.** No ads, no store, no feeds, no account to make. Just your games, a dark screen, and a little music.

## What you get

### Every game looks like itself

| Top screen | Bottom screen |
| :---: | :---: |
| ![The top screen is filled with Super Mario 3D Land's art and logo, with "Nintendo 3DS, Both screens, Played less than a minute, Today" beneath it.](docs/images/hero-top.png) | ![The bottom screen shows a grid of 3DS box art with Super Mario 3D Land highlighted.](docs/images/hero-bottom.png) |

Each game shows its box art on the grid. Hover over one and the top screen becomes that game: its wide art and logo, or its box beside its real title screen, along with when you last played. The grid is one long shelf that glides sideways with you, a column at a time.

Art comes from your RomM server, Steam, the libretro thumbnail library, and SteamGridDB if you add a free key. A connected RomM server can also find covers for games outside its library. You can turn art downloads off in Settings.

### Everything in one place

| Top screen | Bottom screen |
| :---: | :---: |
| ![The top screen shows a glossy Nintendo 3DS icon with "Nintendo 3DS, 27 games" beneath it.](docs/images/home-top.webp) | ![The bottom screen shows Home: glossy, neon-rimmed folder icons for All, Android games, 3DS, PC, Apps, DS, and more.](docs/images/home-bottom.webp) |

Every system gets its own folder, with a glossy icon you can spot at a glance. Your games can live in a few different places, and Foldcade brings them all into the same home.

- **Games on your Thor.** Point Foldcade at a folder on your device and it finds your games for you.
- **Games on your home server.** If you run [RomM](https://romm.app), approve a code on your phone to sign in, then browse that whole collection from the couch. You can add more than one server.
- **Android games and apps.** Your Android games get their own folder. Other apps live in a separate drawer, and you can hide the ones you never use. Emulators and streaming apps get an Emulators folder, so every other folder holds only games.
- **PC games.** Games you have set up in GameNative show up next to everything else. Export them from GameNative as `.steam` files, then choose **Add a folder** in Settings › Library. Add as many folders as you like.
- **Steam games on the device.** Play your Steam library through DroidDeck. Choose **Add to home screen** on a game in DroidDeck, or turn on DroidDeck's game-file sync and add that folder, and your whole library follows.
- **Games streamed from your PC.** In Moonlight, open a game's menu and choose **Create shortcut**, and it shows up in Foldcade.

### One press, and you are playing

Pick a game and press A. Foldcade opens it in the right app for you: Azahar for 3DS games, melonDS for DS games, GameNative for PC games, DroidDeck for Steam games, or Moonlight for streaming. Games from Game Boy to Switch open in the emulator you already have, such as Dolphin, PPSSPP, DuckStation, NetherSX2, or Eden. If you have more than one for a system, Foldcade uses the first one on its list.

Press A to play on the top screen, or X to play on the bottom. Games that need both screens get both.

### Two drawers, always a press away

| L1: your Foldcade | R1: your Thor |
| :---: | :---: |
| ![The L1 drawer: this week's play activity as a small bar chart, round buttons for Home, All, Games, and Apps, then Edit home and Settings.](docs/images/menu-l1.webp) | ![The R1 drawer: date, battery, and Wi-Fi, round buttons for Wi-Fi, Bluetooth, Battery, and Android settings, and a brightness slider.](docs/images/menu-r1.webp) |

**L1** shows how much you played this week, jumps to Home, All, Games, or Apps, and opens Edit home and Settings. **R1** is your Thor at a glance: Wi-Fi, Bluetooth, battery, a brightness slider for the top screen, and your notifications, which you can open or clear right from the buttons.

### Tidy up with one button

<img src="docs/images/menu-y.webp" alt="The Y menu over Super Mario 3D Land's art, with Move, Move to, New folder, and Remove from Home." width="640">

Press Y on any tile to move it, send it to a folder, start a new folder, or take it off Home. Folders can be renamed and given an icon the same way. Press Y twice to pick a tile up and slide it where you want it.

### The other screen stays useful

When a game or app uses just one screen, the other becomes a dim command center: the time, your battery, the game's art and name, and your RetroAchievements progress for RomM games. Press any button to wake it, or open a second app there, side by side.

### Your saves follow you

If you use RomM, Foldcade backs up your saves to your server. Play on the bus, and the save is there when you get home. If you are offline, it waits and uploads later, and a small badge on the tile shows downloads and syncs as they happen. If the same game was saved in two places, Foldcade asks on the bottom screen which to use, and keeps both copies either way. Save sync needs RomM 5.4 or newer.

### See where the hours went

Every game shows how long you have played it and when you last picked it up. L1 shows your week at a glance, and you can sort your library by **Recently played** to jump straight back into whatever you were in the middle of. Set **Play time** to **Everywhere** in Settings, and games you start outside Foldcade count too.

### Make it yours

<img src="docs/afterglow/ribbons-motion.gif" alt="Softly glowing teal and amber ribbons drift at their own paces and cross each other on a black screen." width="480">

- **Afterglow**, the first theme, is true black with soft neon tiles and slow ribbons of light that drift and cross on their own. It is easy on the eyes on a late-night session. More themes are on the way.
- **A background that suits you.** Choose drifting ribbons, glowing embers, a still frame, or plain black, and slow the motion down if you like it calmer.
- **Background music.** Lanternlight, a gentle original track, plays while you browse. Turn the volume down or switch it off whenever you like.
- **Arrange your home.** Pick the games you care about most and put them in the order you like.

<img src="docs/images/settings.webp" alt="Settings, with Look, Sound, Library, Saves, and System on the left, and Theme, Icons, Background, Motion, and Button labels on the right." width="640">

Settings keeps everything to a few short pages, with switches for on and off and a quick left or right for everything else.

## Is Foldcade for you?

Foldcade is a good fit if:

- You have an **AYN Thor** and want both screens to feel like one device.
- You want a **quiet, good-looking home** for the games you already have.
- You play **retro or console** games in emulators like Dolphin, PPSSPP, or Azahar, use **RomM**, **GameNative**, **DroidDeck**, or **Moonlight**, or just want your Android games tidy.

It might not be for you yet if:

- You do not have a Thor. Foldcade is designed around its two screens.
- You want a finished, polished app today. Foldcade is early and changing quickly.
- You use RetroArch. Foldcade hands games over without a file path, and RetroArch needs one, so it is not supported yet.

## Get Foldcade

Foldcade is installed with **Obtainium**, a free app that installs apps straight from where they are published and keeps them up to date.

1. Install [Obtainium](https://obtainium.imranr.dev) on your Thor.
2. Tap the badge below on your Thor. Obtainium opens with Foldcade ready to add.
3. Add Foldcade, then install it.

[![Get it on Obtainium](docs/images/obtainium.png)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22app.foldcade%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fpredmore%2Ffoldcade%22%2C%22author%22%3A%22predmore%22%2C%22name%22%3A%22Foldcade%22%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Efoldcade%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D)

Obtainium will let you know when there is an update.

Would rather install it by hand? Download `foldcade.apk` from [Releases](https://github.com/predmore/foldcade/releases) and open it on your Thor.

Want to try new features first? [Test builds](docs/releases.md#include-pre-releases) come out with every change. Expect rough edges.

## Questions

**Does it cost anything?**
No. Foldcade is free and open source.

**Does it come with games?**
No. Foldcade organizes and opens the games you already have.

**Will it replace my Android home screen?**
Only if you want it to. You can open Foldcade like any other app, or choose **Set as Home** in Settings › System so it is the first thing you see. You can switch back any time.

**Do I need a RomM server?**
No. A folder on your Thor works fine. RomM is there if you already use it.

**Does Foldcade keep my password?**
No. RomM sign-in uses a token, and Foldcade does not store your password.

**Is it finished?**
Not yet. Foldcade is early and in active development, so things will change and improve.

## License and credits

Foldcade's original code, artwork, and music are open source under [MIT](LICENSE) ([licensing](docs/licensing.md)).

Credits for the home music and the theme art are in [licenses/CREDITS.md](licenses/CREDITS.md). Afterglow's original art, sounds, and shaders are licensed under [MIT](themes/afterglow/LICENSE). Its display face is Nunito Regular under the [SIL Open Font License](themes/afterglow/OFL-Nunito.txt). The resource-theme format, composition rules, and native preview are in the [theme author guide](docs/themes.md).

Game names and artwork in the screenshots belong to their owners and are shown only to illustrate how Foldcade displays your own library.

## For developers

- [Building](docs/building.md)
- [Plugins](docs/plugins.md)
- [Releases](docs/releases.md)
- [Architecture](docs/architecture.md)
- [Contributing](CONTRIBUTING.md)
