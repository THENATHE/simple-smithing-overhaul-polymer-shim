# Simple Smithing Overhaul — Polymer Shim

**A separate, server-side Polymer shim that brings Simple Smithing Overhaul's repair and enchantment systems to vanilla Minecraft clients on Fabric 26.3.**

Keep the original mod and dependency JARs installed unchanged. This addon provides the network representations, item artwork, readable menu feedback, and targeted runtime fixes needed to use the original gameplay through standard Minecraft interfaces.

[Download the shim](https://github.com/THENATHE/simple-smithing-overhaul-polymer-shim/releases) · [Compatibility](#direct-compatibility-layers) · [Verification](QA.md)

| Target | Supported release |
|---|---|
| Minecraft | 26.3, Fabric |
| Original mod | Simple Smithing Overhaul 2.9.14 |
| Shim | 1.0.0+26.3 |
| Verified client | Unmodified Minecraft 26.3 |
| Installation | Server only; clients need no gameplay mods |

This is an independent compatibility addon for [pajic's Simple Smithing Overhaul](https://github.com/pajicadvance/simple-smithing-overhaul), built with [Polymer](https://github.com/Patbox/polymer). It does not replace the original mod. The repository name identifies it as a **Polymer shim**; the existing mod ID and release filename remain stable.

## Gameplay features

These systems belong to Simple Smithing Overhaul. The shim keeps their server logic in use and supplies the vanilla-client compatibility described below. Behavior follows the server's SSO configuration; the descriptions reflect its default rules.

| System | What players can do |
|---|---|
| Material-based repairs | Repair equipment with its configured materials and item-specific repair amounts. Additional repair mappings cover items such as bows, crossbows, brushes, fishing rods, tridents and shears. Netherite equipment can use diamonds. |
| Portable repairs | Combine damaged gear, repair material and flint or a whetstone in a crafting grid. Enchanted gear requires a whetstone carrying the relevant enchantments. Whetstones retain their durability and enchantments between uses. |
| Whetstone progression | Craft whetstones, enchant them at a table or with books in an anvil, repair them with quartz, and see their chipped, damaged and broken states. |
| Broken equipment | Equipment survives durability exhaustion in a broken state. Its normal functions and attribute bonuses return when repaired. The shim supplies readable broken-item names and appropriate client-visible attributes. |
| Inventory Mending | Crouch and use a damaged Mending item to repair it using inventory materials and a compatible whetstone. Automatic repair on break also uses these supplies. The original configurable change to XP-based Mending remains in effect. |
| Anvil improvements | Perform free unenchanted repairs and renames, preserve prior-work cost during eligible repairs, and complete permitted work above the vanilla 39-level ceiling. Anvils wear more slowly, remain as broken anvils, and can be repaired one stage at a time with iron blocks. |
| Grindstone improvements | Use one netherite scrap to halve an item's prior-work cost while preserving enchantments. Ordinary disenchanting uses SSO's increased XP rewards. |
| Enchantment upgrades | Use the Enchantment Upgrade template to raise an eligible enchantment by one level. The lapis stack count chooses which enchantment to upgrade; the server checks and charges the configured XP cost. Works with equipment and supported stored-enchantment items. |
| Pinnacle upgrades | Use the Pinnacle Enchantment template and an echo shard on eligible fully enchanted equipment to raise a random enchantment beyond its normal maximum. Rerolls preserve the configured limits and increase the XP cost. Normal clicks and shift-clicks are supported. |
| Loot and progression | Find upgrade templates and additional enchanted-book and XP-bottle loot; duplicate templates through crafting; progress through the original smithing advancement tree. |
| Enchantment balance | Apply the configured enchanting-table power cap, enchanted-loot limits, villager book level/use limits, weighted book enchantments and increased bottle XP. |

## What the shim adds and fixes

- **Vanilla client access:** Polymer representations for all six custom items, the broken-anvil block, three server-owned item components and the portable-repair recipe serializer.
- **Original item artwork:** the generated Polymer resource pack includes SSO's models, textures, whetstone damage stages and translations. Players can decline the pack and use named vanilla representations.
- **Readable interfaces:** server-provided XP costs and smithing guidance above the hotbar, English fallbacks for custom names and advancement text, and suppression of the misleading anvil cost-cap label when the operation is allowed.
- **Free repair eligibility:** zero-cost anvil repairs are available even when the player has zero XP, with the true cost established before the pickup check.
- **Mending edge cases:** crouch-use repairs run before bow, crossbow, trident and fishing-rod actions, and before broken-item rejection. The repair search skips incompatible whetstones; successful repairs retain their repair counter.
- **Minecraft 26.3 loot fixes:** SSO's older loot conditions and functions are converted to the current format, retaining probabilities, enchanted-book modifiers, stack counts and explosion conditions. Configured percentages are converted to probabilities.
- **Enchanting power correction:** the configured bookshelf limit applies to the final power count and respects the original enable/disable setting and Penchant guard.

## How it works

The server continues to store and process the original SSO items, blocks, recipes and components. Polymer overlays translate custom content into vanilla-readable representations as it is sent to players; server-only components remain on the server. A generated resource pack supplies the original item artwork.

Small Mixins attach to the original initialization, data-patching and gameplay methods at runtime. They correct the specific 26.3 issues above and replace client-only screen information with server messages. Crafting, upgrade eligibility, random pinnacle results, XP payment and material consumption remain server-authoritative. **No upstream JAR is edited or repackaged, and saved items retain their original identities.**

## Direct compatibility layers

| Mod / API | Integration implemented by this shim | Verification |
|---|---|---|
| **Simple Smithing Overhaul 2.9.14** | Item/block/component overlays, recipe registration handling, menu feedback, anvil, Mending, enchanting and loot fixes. | Gameplay and client-network tests passed. |
| **Polymer Core 0.18.2+26.3** | Converts the custom items and broken anvil for vanilla clients and provides client-facing item-stack adjustments. | Real vanilla connections and item/menu transactions passed. |
| **Polymer Registry Sync Manipulator** | Marks the portable-repair serializer and Polymer-managed content as server entries so vanilla clients do not need their custom registry definitions. Included with Polymer. | Registry/component conversion and connection tests passed. |
| **Polymer Resource Pack 0.18.2+26.3** | Adds SSO's installed assets to the generated pack and selects original item models when Polymer knows the player has the pack. | Pack generation, integrity and client asset loading passed. |
| **Mixson 2.2.1** | Hooks SSO's data-patching pipeline to convert its loot pools and retain the broken-anvil explosion condition. | Minecraft loot-codec and loaded-loot-table tests passed. |
| **Penchant** | The shim's enchanting-power fix explicitly observes SSO's Penchant integration flag. | Guard exists in code; a combined Penchant installation was not tested. |

### Required dependency compatibility

**Defaulted 1.3.8, CodecUI 26.3-1.4.3, Fzzy Config, Fabric Language Kotlin and Fabric API** were included in the tested server stack. They remain unchanged. These are dependencies, not additional handwritten compatibility layers in this shim. Defaulted and CodecUI required no extra shim-specific patches in the tested releases.

### Optional integrations inherited from SSO

The original mod also contains compatibility hooks for the following mods. Their presence in upstream code is **not** a claim that this shim has tested those combined installations or makes those other mods usable by vanilla clients:

| Mod | Original SSO integration |
|---|---|
| Penchant | Coordinates overlapping enchanting systems and supports whetstone enchanting. |
| Tax Free Levels | Delegates applicable XP payments to its level-cost calculation. |
| Modest Magic | Allows whetstones in supported tablet-enchanting smithing recipes. |
| Enchantment Disabler | Accounts for disabled enchantments when checking eligible enchantments. |
| Better Tridents | Coordinates trident repair-material handling. |
| Item Descriptions | Provides client-side description/translation adjustments, including the changed Mending description. This is not a vanilla-client configuration or UI layer. |

## Installation

1. Use a Fabric **26.3** server with Java **25 or newer** and Fabric Loader **0.19.5**.
2. Install the original SSO JAR and dependencies listed below.
3. Add `simple-smithing-polymer-compat-1.0.0+26.3.jar` from this repository's releases to the server's `mods/` directory.
4. Start the server. Players may connect with vanilla Minecraft 26.3.
5. For custom item artwork, run `/polymer generate-pack` and serve the generated pack through your Polymer hosting setup.

| Dependency | Tested version / filename |
|---|---|
| Simple Smithing Overhaul | `simple_smithing_overhaul-fabric-2.9.14+26.3.jar` |
| Defaulted | `defaulted-1.3.8.release-26.3-fabric.jar` |
| CodecUI | `codecui-26.3-1.4.3-fabric.jar` |
| Fabric API | `0.161.0+26.3` |
| Fzzy Config | `0.7.7+fix2+26.3` |
| Fabric Language Kotlin | `1.14.1+kotlin.2.4.20` |
| Mixson | `2.2.1` multiloader |
| Polymer Bundled | `0.18.2+26.3` |

Use the original **2.9.14** release, not the separate `2.9.14-port.1` fork. Install only one SSO JAR. This repository's release contains the shim; dependency JARs and test fixtures are not bundled.

Configuration stays in `config/simple_smithing_overhaul/config-v2.toml`. The original Fzzy Config editor is a client-mod interface; vanilla players use the server's configured rules.

## Verification and limits

The release passed **172 gameplay assertions at startup and another 172 after datapack reload**, plus a fixture-free server lifecycle check. Network checks covered an official unmodified vanilla connection, a free repair at zero XP, an anvil operation charging 65 levels, actual smithing ingredient placement and upgrade pickup, and grindstone scrap placement and pickup. Resource-pack loading and loot-codec checks also passed. See [QA.md](QA.md) and the [published verification summary](qa/evidence/1.0.0+26.3.json).

- Placed broken anvils use the vanilla damaged-anvil appearance while retaining their original server behavior.
- The original client-only screen drawings and configuration editor are not recreated; standard menus use server XP guidance.
- **Native SSO-client parity is unverified.** This release applies Polymer overlays without a native-client bypass. Testing vanilla clients does not establish that every original client-side feature is preserved for players with SSO installed.
- Optional third-party combinations, every configuration permutation, all rare advancement thresholds and statistical loot distributions were not exhaustively tested.
- Resource-pack generation and client loading were tested; an autohost download/acceptance flow was not part of those checks.

## Building

Use a Java 25 JDK. Place the exact original SSO, Defaulted, CodecUI, Fzzy Config, Fabric Language Kotlin and Mixson JARs from the table above in `libs/`. Gradle resolves Minecraft, Fabric Loader/API and the Polymer APIs from their configured repositories.

```sh
bash gradlew build --max-workers=2
```

If Gradle runs on Java 25 but you deliberately use a newer installed compiler, `-PcompilerVersion=27` selects that compiler while retaining `--release 25` output.

The installable artifact is `build/libs/simple-smithing-polymer-compat-1.0.0+26.3.jar`. The `-sources.jar` is for development. The repository includes QA source and scripts; [qa/README.md](qa/README.md) explains their local runtime requirements. The graphical network harness currently depends on sibling workspace QA launch metadata.

## Credits and license

Shim maintained by **THENATHE**. Original gameplay and artwork by **pajic and SSO contributors**; Polymer by **Patbox and contributors**. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md). This independent project is not an official SSO or Polymer release.
