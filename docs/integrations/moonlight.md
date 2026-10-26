# Moonlight

Foldcade lists the games on your computer and streams them with the official [Moonlight Android client](https://moonlight-stream.org). Pair your computer and configure streaming in Moonlight first; differently packaged forks are not detected automatically.

Set up your computer with [Sunshine](https://github.com/LizardByte/Sunshine) or Apollo, then pair Moonlight on the Thor using the [Moonlight setup guide](https://github.com/moonlight-stream/moonlight-docs/wiki/Setup-Guide). Check that a game streams in Moonlight.

## List every game on a computer

1. With the Thor on the same network as the computer, open **L1 › Settings › Library**.
2. Choose your computer's tile and turn on **Games**. Foldcade shows a four-digit code.
3. On the computer, open Sunshine's web page (**https://localhost:47990**), choose **PIN**, type the code, give the device a name such as Foldcade, and send it. Apollo's page works the same way.
4. Every app on the computer appears in the **Moonlight** folder within a few seconds, with the computer's own box art.

Apps you add or remove on the computer follow within a few minutes while Home is shown. New apps go on Home only when **New games on Home** is on, like any other library's.

Foldcade pairs as a device of its own, next to Moonlight. It only lists apps; Moonlight still streams them, so Moonlight must stay paired with the same computer. On Apollo, a second device can list apps but not start them, which is all Foldcade needs.

Turn the switch off and choose **Forget** to stop listing that computer. Its games leave Home, except ones you pinned in Moonlight. Sunshine keeps Foldcade in its client list until you remove it there.

If you clear Foldcade's data, reinstall it, or move to another Thor, turn the switch on and pair again: the key Foldcade paired with stays on the device that made it.

## Add one game with a shortcut

This works without pairing Foldcade, and for a computer on another network that Moonlight reaches.

1. In Foldcade, open **L1 › Settings › System › Home app** and make Foldcade the default home app.
2. In Moonlight's game list, open the game's menu, such as by long-pressing its tile, and choose **Create shortcut**.
3. Return to Foldcade. The game appears in the **Moonlight** folder, even when **New games on Home** is off.

Pins update at startup, when you return to Foldcade, and after creating a shortcut. A computer shortcut does not add all its games.

## Play

Press **A** to stream on the top screen or **X** on the bottom. The other screen remains available to Foldcade.

Moonlight wakes a sleeping computer when it can, asks before quitting a different game that is running, and resumes the same one.

Saves stay on the computer or with the game's cloud service; Foldcade does not sync them through RomM.

## Art and status

Each game shows the box art Moonlight keeps for it, the same art as in Moonlight's game list, with no key or account. For a paired computer, a game Moonlight has not shown yet uses the computer's own art. Anything else uses the shared [artwork setup](README.md#artwork).

While Home is shown, Foldcade asks each computer on your network how it is. The top screen names the computer a game is on, or says it is offline. The game a computer is streaming reads **Running on** that computer, and its tile shows the paused mark. Sunshine says which game that is to any device; Apollo says it only to a paired one.

## Remove or restore a game

**Y › Hide** takes the game off Home. **Show** in **L1 › Edit home › Hidden** restores it. **Unpin** on a Home shortcut keeps the game in the Moonlight folder. A game added with a shortcut also has **Delete shortcut**: hold **A** on it to remove its Android shortcut and Home tile; create a new shortcut in Moonlight to add it again. The game stays installed on your computer.

Foldcade skips shortcuts Moonlight disables after their computer or game is removed.

## If something is missing or streaming fails

- **No tile for the computer:** Check that Moonlight is installed and the Thor is on the computer's network, then reopen Settings. The tile appears once Foldcade finds the computer.
- **That code didn't match:** Turn the switch on again for a new code, and type that one.
- **The computer didn't pair:** Another device may be pairing with it. Wait a few minutes and try again.
- **Only the Moonlight app tile appears:** Pair Foldcade with the computer, or create individual game shortcuts; the tile in Emulators opens Moonlight itself.
- **"PC not found" when a game starts:** Moonlight is not paired with that computer. Pair it in Moonlight.
- **Stream will not start:** Try it in Moonlight and check pairing, the computer, and network access.

[All integration guides](README.md)
