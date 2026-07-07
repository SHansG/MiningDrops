# MiningDrops

MiningDrops is a custom Paper plugin that combines global auto-pickup with configurable bonus mining drops. It is designed for Paper 26.1.x-style servers and uses only the standard Paper/Bukkit API.

## Features

- Auto-pickup for all broken blocks.
- Configurable bonus drops from selected blocks such as `STONE`, `COBBLESTONE`, and `DEEPSLATE`.
- Fortune enchantment scaling for bonus drops.
- Optional Silk Touch protection for bonus drops.
- Optional blocking of unwanted normal drops, such as `COBBLESTONE`, `ANDESITE`, `DIORITE`, and `GRANITE`.
- Full inventory fallback: leftover items are dropped naturally on the ground.
- Direct XP pickup when auto-pickup is enabled.
- Player auto command for auto-pickup.
- Player nocobble command for toggling on/off pickup of cobblestone and other blocked materials.
- Info command to display configured bonus drops for a block.
- LuckPerms-compatible permissions.

## Requirements

- Paper server compatible with the plugin build target.
- Java 25.
- Minecraft/Paper API version matching the server version shown by `/version`.

## Commands

| Command | Description |
|---|---|
| `/miningdrops status` | Shows whether auto-pickup is enabled for the player. |
| `/miningdrops auto` | Toggles auto-pickup for the player. |
| `/miningdrops nocobble` | Toggles on/off pickup of cobblestone and other blocked materials in config for the player. |
| `/miningdrops info <block>` | Shows configured bonus drops for a block, for example `/miningdrops info STONE`. |
| `/miningdrops reload` | Reloads the plugin configuration. |

Aliases:

```text
/mdrops
/ap
```

## Permissions

| Permission | Description | Default |
|---|---|---|
| `miningdrops.use` | Allows auto-pickup for broken blocks. | true |
| `miningdrops.auto` | Allows use of `/miningdrops auto`. | true |
| `miningdrops.nocobble` | Allows use of `/miningdrops nocobble`. | true |
| `miningdrops.info` | Allows use of `/miningdrops info <block>`. | true |
| `miningdrops.bonus` | Allows receiving configured bonus drops. | true |
| `miningdrops.admin` | Allows use of `/miningdrops reload`. | op |

Example LuckPerms setup:

```text
/lp group default permission set miningdrops.use true
/lp group default permission set miningdrops.auto true
/lp group default permission set miningdrops.nocobble true
/lp group default permission set miningdrops.info true
/lp group default permission set miningdrops.bonus true
/lp group admin permission set miningdrops.admin true
```

## Configuration overview

Bonus drops are configured in `config.yml` under `bonus-drops`.

Example:

```yaml
bonus-drops:
  STONE:
    - material: COAL
      chance: 5.0
      min-amount: 1
      max-amount: 2
      min-exp: 0
      max-exp: 1

    - material: RAW_IRON
      chance: 2.0
      min-amount: 1
      max-amount: 1
      min-exp: 2
      max-exp: 3

    - material: DIAMOND
      chance: 0.15
      min-amount: 1
      max-amount: 1
      min-exp: 3
      max-exp: 7
```

In this example, breaking `STONE` can give extra coal, raw iron, or diamond according to the configured chances.

## Fortune scaling

Fortune scaling is configured under `fortune`.

Available modes:

| Mode | Behavior |
|---|---|
| `NONE` | Fortune does not affect bonus drops. |
| `CHANCE` | Fortune increases bonus drop chance. |
| `AMOUNT` | Fortune increases bonus drop amount. |
| `BOTH` | Fortune increases both chance and amount. |

Recommended starting mode:

```yaml
fortune:
  mode: AMOUNT
```

## Notes

- The plugin respects cancelled `BlockBreakEvent` events, so it should not give drops in protected areas if another protection plugin cancels the break event.
- When auto-pickup is enabled, vanilla drops and bonus drops are placed directly into the player inventory.
- When auto-pickup is disabled, vanilla drops behave normally and bonus drops fall naturally on the ground.
- When nocobble is enabled, cobblestone and other blocked materials specified in config.yml will not be picked to inventory.
- If the inventory is full, leftover items are dropped naturally on the ground.
- If Silk Touch protection is enabled, bonus drops are not given while mining with Silk Touch.
