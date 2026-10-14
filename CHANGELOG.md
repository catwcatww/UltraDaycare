# Changelog

## [1.3.0] - 2026-10-12

### Added

- **Incubators Module**: Introduced a fully configurable egg incubation system that can complement or replace standard party-based egg hatching.
- **Multiple Hatching Modes**: Added 4 flexible hatching conditions configurable per server: Time only, Steps only, Time OR Steps, and Time AND Steps.
- **Caretaker Pokémon System**: Incubators can be assigned caretaker Pokémon with thermal abilities (`Flame Body`, `Magma Armor`, `Steam Engine`) to boost hatching speed.
- **IV-Based Speed Scaling**: The higher the caretaker Pokémon's average IVs, the faster eggs hatch in the incubator (with configurable min/max speed multipliers).
- **In-Game Economy & Daily Limits**: Players can purchase incubators with limited uses via the economy system, with configurable daily purchase limits tied to player permissions.
- **Interactive Incubator GUIs**: Added dedicated menus (`/incubators`) for managing incubator slots, assigning caretakers, selecting eggs, and hatching ready Pokémon with visual progress bars.
- **Party Egg Restrictions**: Server owners can restrict eggs from being held or hatched in the player's active party, automatically transferring daycare eggs to the player's incubator storage.

### Changed

- **License**: Changed project license from GPL-3.0 to CC-BY-NC-4.0 (Non-Commercial).
- **Daycare Menu Integration**: Added a direct access button to the Incubators Hub from the main Daycare interface.

## [1.2.8] - 2026-09-06

### Changed

- **Cobblemon 1.8.0 Compatibility**: Updated compatibility and dependencies to support Cobblemon 1.8.0 (Minecraft 1.21.1).

## [1.2.7]

### Added

- **Multi-Database Support**: Added support for SQL databases (**H2**, **SQLite**, **MySQL**, and **MariaDB**) alongside existing JSON and MongoDB options, allowing server owners to choose their preferred storage system.

### Changed

- **Fully Configurable Form System**: Made form inheritance rules, regional biases, and egg forms completely customizable through `form.json`.
- **Automatic Configuration Repair**: The mod now automatically adds missing default Pokémon forms during startup without overwriting your custom changes.

### Fixed

- **Basculin & Basculegion Forms**: Fixed visual rendering and form textures for White-Striped, Blue-Striped, and Red-Striped Basculin and Basculegion so their forms display properly.
- **Regional Form Inheritance**: Guaranteed that regional variants (Alola, Galar, Hisui, and Paldea) correctly inherit their region bias when hatched from eggs.

## [1.2.6]

### Fixed

- **Pokémon Form & Regional Variant Inheritance**: Fixed form inheritance mechanics when breeding. Offspring eggs now correctly inherit species-specific forms and regional variants (e.g. Alolan, Galarian, Hisuian, Paldean) from parent Pokémon holding an Everstone or following mother inheritance rules, while properly excluding blacklisted cosmetic forms.
- **Breedable Property Matching**: Fixed the `breedable` property check so commands, custom property selectors, and system checks accurately identify whether a Pokémon is allowed to breed or if its breeding status has been disabled.

## [1.2.5]

### Added

- **Fertility System**: Added an optional, server-configurable fertility mechanic (`enableFertility` and `maxFertility`). Pokémon lose 1 fertility point per bred egg, and once fertility reaches 0, their breedable status is automatically disabled.
- **Fertility Lore Display**: Added `loreFertility` to language configuration (`lang/*.json`) to display a Pokémon's current/max fertility at the end of its item lore in daycare menus when fertility is enabled.

### Fixed

- **Regional Forms Inheritance**: Resolved regional form inheritance issues (Alola, Galar, Hisui, Paldea) for eggs. Prioritized direct plain form name parsing (`alolan`) and regional evolution biases (`region_bias=<region>`). Ensured clean form metadata string format for NBT storage and `/egginfo`.

## [1.2.4]

### Added

- **Configurable Poké Ball Blacklist**: Added a server-side configurable list `blacklistedBalls` to `pokeball.json` to decide which Poké Balls cannot be inherited (e.g. Master Ball and Cherish Ball by default). Case-insensitive and supports both full IDs and paths.

### Changed

- **Robust Exception Logging**: Refactored empty catches, console error printouts, and `e.printStackTrace()` calls across all mod modules to write full exception traces into the mod's logger (`UltraDaycare.LOGGER.error`).

### Fixed

- **Fixed Poké Ball Mechanic**: Resolved issues with inheritance logic, null-safety, and configuration file initialization/creation.

## [1.2.3]

### Added

- **PokeMMO Time-based Hatching**: Added time-based egg hatching to PokeMMO daycare mode. The hatching speed is configurable globally and supports permission-based durations for VIPs.
- **Egg IV Preview**: Added a visual preview of expected IV ranges/locked stats directly on the breeding button in the Plot Menu when in PokeMMO mode.
- **Egg Hatching Methods**: Added a new configuration option `pokemmoEggHatchMethod` supporting `STEPS`, `TIME`, and `ALL`.
- **Hatching Ability Acceleration**: Supported ability speedups (Flame Body, Magma Armor, Steam Engine) for both steps-based and time-based egg hatching.

### Changed

- **Config Defaults**: Configured `multiplierAbilityAcceleration` to default to `2.0` to speed up hatching times by half.
- **License Update**: Updated project license from "All Rights Reserved" to GPL-3.0-only.

### Fixed

- **Optimized Ticking & Performance**: Avoided duplicate database/LuckPerms permission lookups during the active 1-second ticking task loop.

## [1.2.2]

### 🔧 Fixes

- **Invalid UUID Exception**: Fixed a server crash (`java.lang.IllegalArgumentException: Invalid UUID string: ???`) that
  occurred when deserializing Pokémon eggs or hatched Pokémon. The system now correctly assigns the player's UUID or a
  fallback nil UUID to the original trainer field instead of a placeholder `"???"` or username string, which are not
  valid UUID format.
- **Menu Cooldowns**: Removed the minimum and maximum limits for cooldown settings when opening menus.

## [1.2.1]

### 🔧 Fixes

- **Breeding Fee in PokeMMO Mode**: Fixed the breed button showing without checking the breeding fee in PokeMMO mode.
  The fee gate now correctly takes priority when `enableBreedingFee` is enabled.
- **Paid Experience Config Ignored**: Fixed claim XP buttons always charging money regardless of the
  `enablePaidExperience` config value. When set to `false`, the claim XP buttons are now hidden entirely.
- **Config Initialization**: Added missing default values for `enablePaidExperience`, `payXpPrice`, and `payXpAmount` in
  the config constructor.

---

## [1.2.0] - Modular Daycare & PokeMMO Mode

### 🌟 Added

- **Modular Daycare Modes**: Added full support for switching between standard Pokémon passive daycare and active
  PokeMMO-style daycare (where parents are consumed instantly to produce an egg) via `daycareMode` in `config.json`.
- **Extensibility API**: Created `DaycareRegistry` and `DaycareMode` interface so third-party developers can easily add
  and register new gameplay modes.
- **PokeMMO IV Inheritance**: Implemented a specialized PokeMMO IV inheritance formula with stats-locking using braces (
  Power items) and a configurable success percentage (`percentagePowerItem`).
- **Subpackage Mechanics Isolation**: Refactored the codebase to place mode-specific mechanics under `mechanics/pokemon`
  and `mechanics/pokemmo` subpackages, maximizing readability and allowing easy extensibility.

### 🛠️ Improvements & Refactoring

- **Code Reuse Optimization**: Reused identical Pokémon subpackage mechanics within PokeMMO mode to avoid class
  duplication and reduce maintenance overhead.
- **Dynamic Instance Helper**: Introduced `UltraDaycare.getActiveMechanic(Class)` to dynamically query the active mode's
  mechanic instances.
- **Instance-Based Incense Refactor**: Rewrote `DayCareInciense` to hold instance-based incense lists, preventing
  static-field configuration pollution when multiple modes are initialized at startup.
- **Standardized File Reading**: Migrated manual JSON text parsing in `DayCareInciense` to use `UtilsFile`.

### 🔧 Fixes

- **Nature Module**: Fixed a bug where the nature module was not being applied correctly.

---

## [1.1.1] - Optimizations & Bug Fixes

### 🔧 Fixes

- **Everstone Nature Inheritance**: Refactored Everstone detection during breeding to avoid fragile
  `instanceof CobblemonItem` checks, ensuring reliable nature inheritance.
- **Hatch Null Safety**: Added safety checks in `getEggInfo` to prevent `NullPointerException` crashes when hatching
  eggs with empty nature metadata.
- **Hatch All Command Fixed**: The command to hatch all eggs now works correctly.
- **Improved Hatch Event**: The hatch event now sends information about the resulting Pokémon. This allows mods like
  UltraQuests and UltraEvents to filter and detect when your Pokémon are born, opening up new possibilities for custom
  quests and events.

### ⚡ Performance Improvements

- **Save System Optimization**: The system for saving egg and training data is now even faster and more efficient.
  Changes are applied instantly without slowing down the server.
- **Overall Stability Improvement**: Internal code has been optimized to reduce resource consumption and improve overall
  game fluidity.

---

## [1.1.0] - UltraDaycare: Automatic Breeding & Dynamic Training

### 🌟 New Features

- **Automatic Breeding**: Eggs now generate automatically in the background! Even better: notifications for new eggs are
  **much faster than before**. No more waiting around to know if the stork has arrived!
- **Step-Based Training System**: Your Pokémon now gain experience while in the Daycare **simply by you walking around
  the world**. Train your team while you explore!
- **Experience Claiming**: We've added dedicated buttons above each Pokémon so you can see how much experience they've
  accumulated and **pay to apply it** instantly.
- **Breeding Costs**: Administrators can now enable a breeding fee, making the server economy more dynamic.

### 🛠️ Improvements & Fixes

- **Max Level Detection**: The menu is now smarter and automatically hides training info once your Pokémon reaches the
  training level cap.
- **Pokemon Selection Fix**: We've fixed the selection menu so you can navigate through all pages of your PC and Party
  without any issues.
- **Better Performance**: Optimized the internal engine so data saving and menu opening are instantaneous and don't
  affect gameplay fluidity.

---

_UltraDaycare - The definitive breeding solution for Cobblemon._

