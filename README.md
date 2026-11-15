# forge-early-loading-screen-fabric
 Port of (Neo)Forge's earlydisplay to Fabric

The mod is separated into 4 parts: `core`, `neoforge`, `loader`, and `earlydisplay`.

### `core`
`core` contains all classes under `com.jab125.earlyloadingscreen` and is responsible for patching Fabric Loader to get verbose information about entrypoints, as well as handling the window handoff. It is also responsible for initializing the early display. 
Classes are patched using Byte Buddy Agent and some classes are forcibly loaded with the help of `net.minecraftforge:unsafe`.
### `neoforge`
`neoforge` contains all classes from NeoForge that are required to make the transition between the early display and Minecraft. All the classes here are identical to their NeoForge counterparts.
### `loader`
`loader` contains all the classes needed for `earlydisplay` to function, with a few changes:
- `FMLConfig` is replaced with a stub class, providing only the default values
- `ImmediateWindowHandler` has been hardcoded to only launch the `DisplayWindow`
- `ProgressMeter` has an extra field called `originalName`
### `earlydisplay`
`earlydisplay` is almost identical to FML's `earlydisplay` subproject, with the only changes being the replacement of some "NeoForge" strings with "Fabric", changes to how the `NeoForgeLoadingOverlay` is found, and compatibility with Drippy Loading Screen.
###
In addition, each `minecraft/<version>` subproject provides a `NeoForgeLoadingOverlay` (and, where needed, mixins) for that Minecraft version.
They are bundled into the main jar with jar-in-jar, and Fabric Loader picks the one whose `minecraft` range matches.

| Subproject | Minecraft |
|------------|-----------|
| (none, `core`) | 1.20.1, 1.20.4 |
| `minecraft/1.21` | 1.21 - 1.21.1 |
| `minecraft/1.21.2` | 1.21.2 - 1.21.3 |
| `minecraft/1.21.4` | 1.21.4 |
| `minecraft/1.21.8` | 1.21.8 |
| `minecraft/1.21.10` | 1.21.10 |
| `minecraft/1.21.11` | 1.21.11 |
| `minecraft/26.1.2` | 26.1.2 |
| `minecraft/26.2` | 26.2 |

Minecraft 26.1 and newer is not obfuscated, so it needs a different Loom plugin and a different access widener namespace.
`core-26` is the variant of `core` for those versions. It compiles the same sources and is published as a separate jar (`early-loading-screen-26`).
Minecraft 26.3 replaced GLFW with SDL3, so it is not supported yet: the early display in `earlydisplay` is built on GLFW.

### Building
Gradle runs on Java 25 (Loom 1.19 and Minecraft 26.1+ need it). The older subprojects still compile with `release` 17 or 21.
Run `./gradlew build`. To launch a client with the test mod, use `./gradlew :core:runTestmodClient` (or `:core-26:runTestmodClient`).
### Equivalent Version of NeoForge/FancyModLoader

| Mod   | NeoForge               | FancyModLoader         |
|-------|------------------------|------------------------|
| 0.1.0 | (Forge) 1.20.1-47.0.44 | (Forge) 1.20.1-47.0.44 |
| 0.2.0 | 20.4.27-beta           | 2.0.1                  |
| 0.3.0 | 20.4.27-beta           | 2.0.1                  |
| 0.3.1 | 20.4.27-beta           | 2.0.1                  |
| 0.4.0 | 20.4.48-beta           | 2.0.1                  |
| 0.5.0 | 20.4.48-beta           | 2.0.1                  |
| 0.6.0 | 20.6.99-beta           | 3.0.41                 |
| 0.6.1 | 20.6.99-beta           | 3.0.41                 |
| 0.7.0 | 20.6.99-beta           | 3.0.42                 |
## Licensing
The entire mod is licensed under LGPL-2.1-only

All untouched code  is completely Forge/NeoForge's.