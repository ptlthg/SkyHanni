_Frequently Asked Questions_

> **1: How do I open the SkyHanni Menu?**
> Use the command `/sh`.

> **2: Why does SkyHanni tell me to update NotEnoughUpdates (NEU) even if I already have the latest version?**
> To make the mod work, you need to use the latest version from <#1123201092193366027>.

> **3: How can I resize the GUI?**
> Do `/sh gui` to open the position editor. Then hover over an element and scroll your mouse wheel to change the size of a single GUI element.
> Do `/sh scale` to change the global GUI scale of all elements at once.

> **4: My Garden Crop Milestones are not accurate. What should I do?**
> To sync your Crop Milestones with SkyHanni, open `/cropmilestones` once.

> **5: How can I move GUIs like Coins/Copper in SkyMart?**
> Use the command `/sh open hotkey` to set a hotkey, and press it inside the GUI you want to move.

> **6: Will SkyHanni support newer Minecraft versions (1.21+)?**
> Yes, it is planned. The Foraging Update isn't expected to release for several more months (state march 2025), but we're already working on updating the mod to support newer versions behind the scenes.
> Initially, we plan to support both 1.8.9 and 1.21.4, but we will consider removing support for 1.8.9 once the newer version becomes more integral for SkyBlock gameplay.

> **7: My Jacob Contest Display crops are wrong, how do I fix this?**
> Run the command `/shclearcontestdata` to clear the Jacob contest data.

> **8: How can I get bigger crop hit boxes?**
> Use Patcher or PolyPatcher to have 1.12 hit boxes in 1.8.9.
> - [Sk1erLLC's Patcher](<https://sk1er.club/mods/patcher>) (Versions 1.8.8 and after will have broken cactus hitboxes)
> - [Polyfrost's PolyPatcher](<https://modrinth.com/mod/patcher>) (a fork of Patcher with OneConfig, slightly different features, and bug fixes. Fixes cactus hitboxes.)

> **9: Why does my Item Tracker feature not track this item?**
> 1. Check if the item goes directly into your sacks.
> 2. If it does, enable the sack pickup chat message from Hypixel:
     >   - Go to `Hypixel Settings -> Personal -> Chat Feedback` and enable `Sack Notifications`.
> 3. If you want the [Sacks] messages to be hidden, do `/sh sacks hider` and enable that.

> **10: How do I remove SkyHanni GUI elements?**
> 1. Type `/sh gui`.
> 2. Click the UI element (if the UI element is not shown, follow FaQ #5).
> 3. Disable this feature.

> **11: How do I reset a SkyHanni tracker?**
> 1. Do you want to **view only the current session**?
     >   - Open the inventory (Press E) and hover over the display.
>   - Then click on `[This Session]`.
> 2. Do you want to **reset the current session**?
     >   - Open the inventory (Press E) and hover over the display.
>   - Then click on `Reset Session!`.
> 3. (**For Diana Trackers only**) Do you want to **view only the current mayor**?
     >   - Open the inventory (Press E) and hover over the display.
>   - Then click on `[This Mayor]`.
> 4. Do you want to **remove one specific item** from the tracker?
     >   - Open the inventory (Press E) and hover over the display.
>   - Then shift-click on an item in the list to remove it.
> 5. Do you want to reset the total stats of a tracker?
     >   - To reset a tracker, use the in-game command `/shcommands <tracker type>`.
>   - Execute the obtained command to reset the tracker.

> **12: Why can I still see the normal Scoreboard when using Custom Scoreboard?**
> Most of the time, this is a mod conflict.
> If you are using [Feather Client](https://feathermc.com/), please disable their own Scoreboard Feature (or just uninstall Feather).
> If you are using [Sidebar Mod](https://github.com/Alexdoru/SidebarMod), please remove this mod.
> If you are using [VanillaHUD](https://modrinth.com/mod/vanillahud), please update to 2.2.9 or newer to resolve this issue.
> If you are using [Apec](https://github.com/BananaFructa/Apec/) and want to remove their Scoreboard, you need to remove Apec since they don't have an option to disable their Scoreboard.
> If you are using [Odin](https://github.com/odtheking/Odin), disable their "Sidebar".
> If you are using [Patcher](https://sk1er.club/mods/patcher) or [PolyPatcher](https://modrinth.com/mod/patcher) and the vanilla scoreboard is flickering, disable the "HUD Caching" option (this may reduce FPS).
> If you don't use any of these mods, make sure the option to "Hide Vanilla Scoreboard" is actually enabled.

> **13: Why doesn't the burrow warp key and line prioritize my guess waypoint when there are known burrows nearby?**
> This is intended behavior. SkyHanni prioritizes the closest known burrow or guess waypoint, not necessarily the guess waypoint itself. Focusing on the closest point, even if it's a known burrow and not your guess, is faster and leads to a higher "burrows dug over time" rate, meaning more mobs, more inquisitors, more money and faster milestones.
> While interrupting your current chain to focus on a nearby burrow might feel counterintuitive, it ultimately benefits you in the long run.
> The existing chain isn't reset, and you gain the advantage of a higher burrow digging rate.

> **14: Why are the Players in Custom Wardrobe hidden?**
> If you are using [VolcAddons](https://github.com/zhenga8533/VolcAddons), disable Hide Far/Hide Close Entities.


*This FAQ was last updated on March 24rd, 2025.
If you believe there's something that should be added to this list, please tell us, so we can add it.*
