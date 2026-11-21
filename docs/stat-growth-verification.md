# Stat growth verification (1.1.0)

## Behavior

Edits replace a signed bonus: `requested - currentNatural`. Reads use the current natural formula
plus that bonus, clamped to `[1, Integer.MAX_VALUE]`. Editor input limits do not cap later growth.
Legacy absolute edits migrate only after complete hydration or before genuine natural-stat changes.

## Automated checks

```bash
JAVA_HOME="$PWD/.jdk21" ./gradlew --no-daemon --no-parallel --max-workers=1 \
  --console=plain -Dorg.gradle.jvmargs=-Xmx1024m clean build test
node scripts/test-showdown.cjs server/showdown
```

JUnit covers arithmetic, negative bonuses, repeated edits, overflow, exceeding input limits,
hydration nesting/thread isolation/failure recovery, and the existing editor/model tests.
Showdown tests include grown packed stats, growth while transformed, resets using the post-growth
natural baseline and permanent Mega-X form preservation, in addition to existing damage, Speed,
pending-choice, reset and fixed-HP checks.

## Isolated runtime validation

A separate Fabric 0.19.4 / Minecraft 1.21.1 / Cobblemon 1.7.3 headless fixture was run with a
throwaway probe mod, 768 MiB heap, a new flat world and loopback-only port 25591. No production
world or configuration was copied. The probe is not part of the release JAR.

The runtime probe passed **121 assertions**, covering:

* actual stat-provider mixins for all six edited stats, level 5 → 6 and current HP adjustment;
* actual `BattleRegistry.packTeam` output with grown effective stats;
* NBT save/load, subsequent growth, re-edit, individual/all resets and negative bonuses;
* signed payload codec round trips and server-authoritative bonuses with a stale client level;
* species evolution, legacy direct growth and legacy NBT migration;
* shiny Heat Rotom legacy Defence = 100 through NBT and the full client packet codec;
* stale-level battle clones with aliased NBT: rebasing matches effective stats without changing
  the source's permanent bonuses, including reset snapshots;
* actual embedded Graal battles refreshed by server ticks after level, IV, EV-container and
  minted-nature changes, without an explicit stat-edit request;
* growth combined with explicit edits, individual HP reset and full reset in the same server tick,
  including continued growth of an already-reset HP stat.

The Rotom regression was reproduced before hydration suppression: legacy Defence 100 incorrectly
became 130. With the fix it remains 100, and its bonus is anchored against the restored Heat form.
The Transform growth regression was also confirmed failing before its simulator fix.
Reset-after-growth and permanent Mega-X baseline regressions were likewise reproduced before
adding authoritative natural snapshots with source-form-aware baseline handling.

The actual Java-packed level-6 team was additionally consumed by the shipped Showdown simulator;
all six effective stats matched the packed extension exactly.

This is headless validation, not a visual GUI playtest. The existing GUI behavior is unchanged.
