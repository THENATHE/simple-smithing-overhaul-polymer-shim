# Verification — Fabric 26.3

Full server logs, launch audits and disposable worlds are retained locally and excluded from Git. The [committed verification summary](qa/evidence/1.0.0+26.3.json) records the release digest and completed checks.

Tested 2026-09-30 with the original Simple Smithing Overhaul 2.9.14, Defaulted
1.3.8 and CodecUI 26.3-1.4.3 JARs. No original JAR was modified.

Release: `simple-smithing-polymer-compat-1.0.0+26.3.jar`

SHA-256: `51c2cea2196531c9a362038e07152f9413bf51f46836ba41bd94a8d25103514f`

## Production artifact

- Clean Gradle build passed using the Java 27 compiler with `--release 25`.
- ZIP integrity, Fabric version/mixin metadata and Java 25 class versions passed.
- The addon contains only its own classes and resources: no original mod classes,
  bundled dependency JARs or test fixtures.
- A fresh dedicated server using the production JAR and original dependencies,
  without a QA fixture, passed startup, datapack reload and clean shutdown with
  no logged errors. Evidence: `build/qa-run/20260930-015329-70139/`.
- Original checksums match the filenames and digests in [the verification summary](qa/evidence/1.0.0+26.3.json).

## Gameplay regression checks

The final production JAR passed **172 assertions at startup and another 172 after
datapack reload**, with all assertions enabled. Evidence:
`build/qa-run/20260930-015456-73971/` and `qa/fixture/RepairChecks.java`.

Coverage includes repair materials and quantities; flint and enchanted whetstone
rules; unchanged recipe inputs; repair counters; breaking and restoring gear;
equipment attributes; free anvil repairs at zero XP; renaming; work above 39
levels; broken anvils and all three iron-block repair stages; grindstone scrap,
disenchanting and XP; manual and automatic Mending, item subclasses, incompatible
whetstone selection and configuration toggles; whetstone enchanting and book
application; the bookshelf cap enabled and disabled; crafting recipes; villager
book levels and trade uses; actual spawned bottle XP; lapis enchantment selection;
upgrade and pinnacle XP gates and payment; normal-click and shift-click pinnacle
application, rerolls and limits; all 14 advancement definitions and several actual
awards; loaded loot tables; six Polymer item conversions, block mapping, hidden
components and preservation of the original server stacks.

The broader checks exposed and verified the original enchanting-table cap bug:
full shelves now produce the same offers as the configured shelf count; disabling
the cap restores vanilla full-power offers.

## Real client and network checks

Evidence: `qa/network-run/result.json`, per-side `launch-audit.json`, full logs,
and `control/client-evidence.txt` / `control/server-evidence.txt`.

- The official Mojang 26.3 client connected and ticked with an empty `mods`
  directory, without a resource pack.
- A second client used only Fabric API and a test driver. It had no SSO, Polymer,
  Defaulted, CodecUI, registry replacements or menu patches. Its item registry
  contained only Minecraft items.
- That driver called Minecraft's normal `handleContainerInput` method, exercising
  vanilla client prediction and the actual network rather than server-only
  simulated clicks.
- At zero XP, a free anvil repair produced a usable repaired item without charging
  XP. A 65-level enchanted anvil operation charged exactly 65 levels (100 → 35),
  with the misleading vanilla cost-cap label suppressed.
- Six normal client clicks placed the custom smithing template, enchanted item
  and lapis into their slots. Taking the result upgraded Sharpness I → II and
  charged five levels (100 → 95), with the expected ingredient consumption.
- Normal client clicks inserted netherite scrap into the grindstone's second
  slot. Taking the result halved prior work cost (31 → 15), preserved enchantments,
  consumed the scrap and awarded no XP (100 → 100).
- An earlier no-pack run also passed all four output pickup transactions;
  evidence is retained in `qa/network-prefilled-evidence/`.

## Resource pack and loot codecs

- `/polymer generate-pack` completed. The ZIP passed integrity checks and includes
  all six original item definitions, models, textures and translations.
- The generated pack loaded in the second client without model or asset errors.
  This was a local pack-load check, not an autohost download/acceptance test.
- Actual Minecraft loot-codec regression tests passed for template probabilities,
  configured percentages, enchantment/count modifiers, combined conditions and
  broken-anvil explosion conditions. Pool preparation preserves its input and is
  idempotent. Evidence: `build/qa-loot-codecs/result.log`.

## Reproduction

`qa/deps/` contains copies of the tested original SSO and dependencies, excluding
the addon itself. Runtime checks use fresh disposable worlds and localhost ports.
Never install QA fixture JARs on a normal server.

```sh
python3 qa/build_fixture.py --jar build/libs/simple-smithing-polymer-compat-1.0.0+26.3.jar --mods qa/deps
python3 qa/server_smoke.py --jar build/libs/simple-smithing-polymer-compat-1.0.0+26.3.jar --mods qa/deps --fixture build/qa-fixture/sso-qa-repair-checks.jar --java /usr/lib/jvm/java-25-openjdk/bin/java
python3 qa/server_smoke.py --jar build/libs/simple-smithing-polymer-compat-1.0.0+26.3.jar --mods qa/deps --java /usr/lib/jvm/java-25-openjdk/bin/java
python3 qa/check_loot_codecs.py --libraries build/qa-run/20260930-015329-70139/libraries
python3 qa/network_run.py
```

The network harness reuses cached official Minecraft binaries and launch metadata
from this workspace's existing MapStitch/Map Atlases QA setup and requires an X11
display. It is a local development harness, not a portable installer.

## Limits

The checks cover the supplied originals, their listed dependencies, default
settings and selected configuration toggles. They are not a compatibility claim
for every third-party mod or configuration combination. Optional integrations
such as Penchant and Modest Magic were not installed. Native clients with SSO
installed were not tested; the supported client setup is vanilla 26.3.

Placed broken anvils intentionally use vanilla damaged-anvil visuals. The
original client-only configuration editor and custom screen drawings cannot be
added by a server addon; gameplay uses standard screens with server XP guidance.
