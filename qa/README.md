# Disposable gameplay verification

`fixture/RepairChecks.java` is a test-only Fabric entrypoint. It runs real server objects with the untouched upstream Simple Smithing Overhaul jar, its dependencies, and the separate Polymer compatibility addon. It is never included in the release jar or installation bundle. The fixture deliberately creates blocks and a synthetic server player: run only in the disposable world created by `server_smoke.py`.

From the addon project directory:

```sh
python3 qa/build_fixture.py --jar build/libs/simple-smithing-polymer-compat-1.0.0+26.3.jar --mods qa/deps
python3 qa/server_smoke.py --jar build/libs/simple-smithing-polymer-compat-1.0.0+26.3.jar --mods qa/deps --fixture build/qa-fixture/sso-qa-repair-checks.jar
```

`qa/deps` must contain copies of the original upstream SSO jar and all listed runtime dependencies. The compiler locates the original SSO jar in the parent Minecraft folder by default; `--original` overrides that path. The build requires cached Minecraft 26.3 server libraries and Fabric Loader 0.19.5. The smoke runner uses Java 25 and binds only localhost, creates a new world, reloads datapacks, and stops the server. Each run records copied jar hashes, console output, and `result.json` under `build/qa-run/`.

Final verified run: `build/qa-run/20260930-015456-73971/` — **172 assertions at startup and 172 after reload**, successful shutdown, exit code 0, no reported errors. Production addon SHA-256: `51c2cea2196531c9a362038e07152f9413bf51f46836ba41bd94a8d25103514f`.

Assertions run once on startup and again after `/reload`:

- Repair materials for nine vanilla items; flint crafting repair amounts, input preservation, consumed materials and repair counters; invalid materials and enchanted flint rejection; enchanted whetstone matching, enchantment preservation, retained whetstone and broken whetstone rejection.
- Durability exhaustion preserves broken gear; attributes disappear while broken and return on repair; damaged anvils become broken; three iron-block repair stages consume three blocks.
- Anvil repairs, zero-XP pickup, free rename, combinations above the vanilla 40-level limit and broken-anvil rejection.
- Mending inventory repair, actual server crouch-use callbacks for bow/crossbow/trident/fishing rod, broken bow recovery, exactly-one-material consumption, repair counters, non-crouch/disabled/missing-material rejection, compatible later whetstone selection and component preservation, automatic repair on inventory tick.
- Whetstone enchanting-table offers and stored enchantments, anvil book application, physical bookshelf-cap comparison, cap disabled/re-enabled, quartz whetstone crafting and both template duplication recipes.
- Actual villager trade offers obey configured book level and use limits; grindstone disenchanting, boosted XP and zero XP for scrap; actual thrown-bottle hit produces XP orbs in the configured range.
- Enchantment upgrade lapis selection, XP gate, shift pickup and XP payment; pinnacle eligibility, XP gate, normal and shift pickup, one upgrade per pickup, reroll count, increased reroll cost, enchantment-count limit and ingredient consumption.
- Fourteen smithing advancements load; actual repair, enchantment-upgrade and pinnacle actions award their advancements. Loaded template loot retains 10% chance, and added book/count modifiers survive 26.3 decoding.
- Six Polymer overlays resolve to vanilla items; actual transformed item stacks hide server components without mutating original stacks; broken-anvil overlay, custom-component registration and server-only recipe serializer are present.

This is broad server regression coverage, not a claim of exhaustive coverage. It does not exercise every configurable value, optional third-party integration, every rare advancement threshold, or large-sample random loot distributions. A server fixture does not verify visual appearance in a graphical client; network/client-packet and resource-pack tests are separate.
