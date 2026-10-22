# Moonlight

Foldcade launches game shortcuts from the official [Moonlight Android client](https://moonlight-stream.org). Pair your computer and configure streaming in Moonlight first; differently packaged forks are not detected automatically.

## Add a game

1. Set up your computer and pair Moonlight on the Thor using the [Moonlight setup guide](https://github.com/moonlight-stream/moonlight-docs/wiki/Setup-Guide).
2. Check that the game streams in Moonlight.
3. In Foldcade, open **L1 › Settings › System › Home app** and make Foldcade the default home app.
4. In Moonlight's game list, open the game's menu, such as by long-pressing its tile, and choose **Create shortcut**.
5. Return to Foldcade. The game appears in the **Moonlight** folder.

Repeat for each game or application you want. New pins go on Home even when **New games on Home** is off. A computer shortcut does not add all its games.

## Discovery and launch

Pins update at startup, when you return to Foldcade, and after creating a shortcut. Foldcade does not import the computer's whole app list; create a shortcut for each new game.

Press **A** to stream on the top screen or **X** on the bottom. The other screen remains available to Foldcade. Your computer must be reachable.

Moonlight wakes a sleeping computer when it can, asks before quitting a different game that is running, and resumes the same one.

Saves stay on the computer or with the game's cloud service; Foldcade does not sync them through RomM.

## Art and status

Each game shows the box art Moonlight keeps for it, the same art as in Moonlight's game list, with no key or account. A game Moonlight has not shown yet uses the shared [artwork setup](README.md#artwork) instead; open its computer in Moonlight once to fetch the rest.

While Home is shown, Foldcade asks each of Moonlight's computers on your network how it is. The top screen names the computer the game is on, or says it is offline. With Sunshine, the game the computer is streaming reads **Running on** that computer, and its tile shows the paused mark. This works on the same network without pairing, and Foldcade asks each computer nothing else.

## Remove or restore a game

**Y › Hide** takes the game off Home and keeps its Android shortcut. **Show** in **L1 › Edit home › Hidden** restores it. **Unpin** on a Home shortcut keeps the game in the Moonlight folder. Hold **A** on **Delete shortcut** to remove its Android shortcut and Home tile; create a new shortcut in Moonlight to add it again. The game stays installed on your computer.

Foldcade skips shortcuts Moonlight disables after their computer or game is removed.

## If a shortcut is missing or streaming fails

- **Shortcut is missing:** Make Foldcade the default home app and request it again. Pins from another launcher are not imported.
- **Only the Moonlight app tile appears:** Create individual game shortcuts; the tile in Emulators opens Moonlight itself.
- **Stream will not start:** Try it in Moonlight and check pairing, the computer, and network access.

[All integration guides](README.md)
