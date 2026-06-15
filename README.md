# Automechs

**Build, program, and power autonomous worker mechs.** Deploy a mech, slot it with a job module,
program its routine, and send it out to work for you — powered by Forge Energy.

> A NeoForge content mod for Minecraft 1.21.1, built to be polished, server-safe, and modpack-ready
> (targeting inclusion in big packs like All The Mods).

## The hook

Existing automation in big packs is either stationary machines or dumb combat pets. Automechs fills the
gap: a **programmable, mobile, FE-powered worker** you build and direct. Build a mech, give it a job
module, order its routine in a GUI, power it, and it goes to work.

## v0.1 (MVP) — Mining Mech

The first job is **Miner / Quarry**, as a complete self-contained loop:

1. Build a **Mech Chassis** at the **Assembly Workshop**.
2. Slot a **Mining Module** and link a deposit chest + mining area with the **Linker**.
3. Program its **routine** (mine area → deposit → recharge → repeat).
4. Power it: a **Charging Pad** accepts Forge Energy from any cable and tops up nearby mechs.

The mech digs the configured area, stores to its internal inventory, deposits to the linked chest,
returns to recharge, and repeats — pausing safely when out of power. It never grafts or grief-mines:
mineable blocks are tag-gated and block protection is respected.

## Roadmap

1. More job modules: Hauler/Magnet, Harvester (crops + trees), Guard/Combat, Builder/Logistics.
2. Chassis tiers (copper → iron → advanced): more module slots, inventory, HP, speed, FE buffer.
3. Deeper routines: conditions, waypoints, multi-area patrols.
4. A capstone "master chassis" (and/or a mini-boss core to chase).
5. JEI/EMI recipe support, advancements, config presets.

## Tech

- **Minecraft** 1.21.1 · **NeoForge** 21.1.233 · **JDK 21**
- Build: **ModDevGradle** · Mappings: Mojmap + **Parchment** 2024.11.17
- Power via NeoForge's `IEnergyStorage` (Forge Energy) — soft integration, no hard dependencies.

## Building

```powershell
$env:JAVA_HOME = 'E:\MinecraftFirstRealMod\tools\jdk-21.0.11+10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat build       # compile + produce jar (build\libs)
.\gradlew.bat runClient   # dev client
.\gradlew.bat runServer   # dedicated-server smoke test
```

## License

[MIT](LICENSE) — modpack inclusion is explicitly permitted.
