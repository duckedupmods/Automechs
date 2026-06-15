# Automechs — Design Notes & Vision

> Living design doc — evolving direction and decisions for Automechs. Dated entries, newest first.

## 2026-06-16 — v0.2 storage network shipped + pre-release hardening

The **v0.2 Data Rack storage network** is in and working — an AE2-flavored, FE-powered storage system
that's its own self-contained loop:

- **Main Drive** — the powered network controller. Draws Forge Energy (FE) scaled by rack count + data
  stored; if the buffer can't cover the draw the network goes *offline* (terminal locked, bots idle) —
  stored items always stay safe. Floods the Data Cables to discover its members.
- **Data Cable** — connects the Drive to its Racks and Terminal.
- **Data Rack** — passive store split into "data sectors." Inserts deliberately *fragment* (scatter
  across empty sectors) so there's defrag work to do. Face shows live Defrag% + item count.
- **Storage Terminal** — the AE2-style access GUI (aggregated item grid, insert by shift-click,
  withdraw by click; left toolbar sorts by amount/name/mod).
- **Cache Crawler** — small spider-bots that physically scuttle to fragmented racks and **defrag** them
  (merge same-item fragments into full stacks, compact to the front). The signature gimmick.

This makes Automechs span **both** halves of base automation: mobile worker mechs *and* a storage/logistics
backbone — a stronger ATM pitch than mechs alone.

### Pre-release optimize + bug-hunt pass (same day)
First hardening pass before publishing. Fixed: a Terminal **withdraw item-loss** (partial-fit remainder
silently dropped); a **combat mech fighting for free** at low FE (only the build goal had been energy-gated);
and four performance hotspots — the Terminal `findDrive()` cable-flood storm (now a 20-tick cached
resolve), the Main Drive re-scanning the whole network 3× per tick (now tallied once on the rescan
cadence), a snapshot codec cap that could freeze the grid on huge stores (now truncated to the largest
types), and the Cache Crawler box-scanning ~26k blocks per bot every 3s even when already anchored (now
only re-scans when its anchor is gone). Two "bugs" were investigated and ruled out as false alarms
(smelter math is correct conservation; defrag can't overflow). Compiles clean on JDK 21.

### Release direction
First public release ships as **v0.2.0** to **CurseForge** (ATM source) + a **Modrinth** mirror, MIT-licensed
(modpack permission granted), source on **GitHub** (`duckedupmods/Automechs`). CurseForge does *not* require
GitHub — the jar uploads directly — but the repo gives us an issue tracker + open-source credibility.

## 2026-06-06 — Mechs as ROLES, not power tiers (+ modular upgrades)

The three mech models (built & in-game) are being **re-framed from a power ladder into job
archetypes**. Each "tier" model is really a different *role*; you pick the mech for the job, not a
strictly-better version. Upgrades are a *separate, orthogonal* modular system that applies to any role.

### The three mech roles
- **T1 — Miner / Farmer** (silver chibi): the launch role. Digs a configured area / harvests &
  replants crops; stores to inventory → deposits to a linked chest. (Built: mining loop.)
- **T2 — Builder / Logistics** (steel "Heavy Foreman"): moves & **sorts items chest→chest**, places
  blocks / runs build patterns. The "logistics" mech — keeps a base organized.
- **T3 — Combat / Looter** (navy "Combat Core"): **hunts hostile mobs** in an area and **hauls their
  drops** to a linked chest. The guard/farm-clearer mech.

> All three already render per-`tier` via `DATA_TIER` + `MiningMechModel`. Keep the current rendering
> and the tiered chassis items AS-IS for now; the *behavior* per role is the future work. The word
> "tier" in code currently = "role/model id"; we may rename later.

### Modular upgrades (orthogonal to role) — brainstorm
Applied at the Upgrade/Assembly Bench, stored on the mech (data), shown in its GUI:
- **Speed** — faster movement + faster task tick.
- **Energy Capacity** — bigger FE buffer (work longer between charges).
- **Range / Reach** — larger work area cap.
- **Inventory / Capacity** — more internal slots before it must deposit.
- **Efficiency** — less FE per action.
- *(later ideas)* Silk-touch / Fortune module (miner), Filter module (sorter — whitelist/blacklist
  what it moves), Targeting module (combat — choose mob types), Solar/auto-charge, Magnet (pickup
  radius), Chunk-loader, Waypoint/patrol.

Upgrades should be **config-gettable** (packs can cap/disable) and **datagen'd** (recipes, lang).

### TWO benches (decided 2026-06-06) — no tier "upgrading" anymore
The role re-frame kills the T1→T2→T3 *upgrade* path (upgrading between roles is meaningless). Instead:

1. **Mech Assembly Bench** (animated GeckoLib gantry, the centerpiece — building now):
   - You **create** a mech here from parts/materials and pick its **role** (miner/builder/combat).
   - Flavor centerpiece: a **half-built robot** sits in the cradle — deliberately *mismatched* parts
     (e.g. a T1 leg, a T3 arm, a bare glowing core, a skeletal limb) = "assembly in progress."
   - **Bigger rig, multiple arms** (user picked "bigger/more arms"): a 4-post bay frame with dual
     welding arms + a helper claw that **lower and weld the robot with animation + spark particles**
     when the player runs a build. Build effect = **weld the real mech parts on it** (not a wireframe).
   - This supersedes the plain `Assembly Workshop` block as the mech-creation station (decide whether
     to replace or keep the workshop when wiring Java).
2. **Mech Upgrade Bench** (second block, later): put a finished mech in → install modular upgrades
   (Speed, Energy Capacity, Range, Inventory, Efficiency, …). Separate from creation.

So: **Assembly Bench = which role; Upgrade Bench = how souped-up.**

### Still-open questions
- Assembly Bench output: a deployable **chassis item** of the chosen role, or spawn the mech directly?
- Upgrades: consumable module items slotted in the mech's GUI, or applied (consumed) at the bench?
- Replace the existing `Assembly Workshop` with the Assembly Bench, or keep both?
- Reconcile "tier" naming → "role" in code once behaviors diverge (currently `DATA_TIER` = role/model id).
