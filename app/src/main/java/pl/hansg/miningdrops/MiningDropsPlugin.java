package pl.hansg.miningdrops;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.Sound;
// import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class MiningDropsPlugin extends JavaPlugin implements Listener { //, TabExecutor {

    // private final Set<UUID> toggledPlayers = new HashSet<>();
    private final Set<UUID> toggledAutoPickupPlayers = new HashSet<>();
    private final Set<UUID> toggledNoCobblePlayers = new HashSet<>();
    private final Random random = new Random();

    private NamespacedKey skipCustomBlockBreakHandlersKey;

    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDataFile();
        // loadToggledPlayers();

        loadToggledPlayers("auto");
        loadToggledPlayers("nocobble");

        skipCustomBlockBreakHandlersKey = new NamespacedKey("custommechanics", "skip_custom_block_break_handlers");

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("miningdrops") != null) {
            getCommand("miningdrops").setExecutor(this);
            getCommand("miningdrops").setTabCompleter(this);
        }

        getLogger().info("MiningDrops enabled.");
    }

    @Override
    public void onDisable() {
        // saveToggledPlayers();

        saveToggledPlayers("auto");
        saveToggledPlayers("nocobble");

        getLogger().info("MiningDrops disabled.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        World world = block.getWorld();

        if (!player.hasPermission("miningdrops.use")) {
            return;
        }

        if (shouldSkipCustomBlockBreakHandlers(player)) {
            return;
        }

        if (!isWorldEnabled(world.getName())) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean autoPickupEnabled = isAutoPickupEnabled(player);
        boolean noCobbleEnabled = isNoCobbleEnabled(player);

        /*
         * Normal vanilla drops:
         * If auto-pickup is enabled, prevent vanilla item entities and give drops directly.
         * If auto-pickup is disabled, leave vanilla behavior untouched.
         */
        if (autoPickupEnabled) {
            Collection<ItemStack> normalDrops = block.getDrops(tool, player);

            event.setDropItems(false);

            for (ItemStack drop : normalDrops) {
                if (noCobbleEnabled && isBlockedNormalDrop(drop.getType())) {
                    continue;
                }

                giveOrDrop(player, block, drop);
            }
        
            if (getConfig().getBoolean("settings.give-exp-directly", true)) {
                int exp = event.getExpToDrop();
                event.setExpToDrop(0);
                player.giveExp(exp);
            }
        }

        if (!autoPickupEnabled && noCobbleEnabled) {
            Collection<ItemStack> normalDrops = block.getDrops(tool, player);

            event.setDropItems(false);

            for (ItemStack drop : normalDrops) {
                if (isBlockedNormalDrop(drop.getType())) {
                    continue;
                }

                block.getWorld().dropItemNaturally(block.getLocation(), drop);
            }
        }

        if (player.hasPermission("miningdrops.bonus")) {
            boolean hasSilkTouch = hasSilkTouch(tool);
            boolean respectSilkTouch = getConfig().getBoolean("settings.respect-silk-touch-for-bonus-drops", true);

            if (!(hasSilkTouch && respectSilkTouch)) {
                giveBonusDrops(player, block, tool, autoPickupEnabled);
            }
        }
    }

    private boolean shouldSkipCustomBlockBreakHandlers(Player player) {
        if (skipCustomBlockBreakHandlersKey == null) {
            return false;
        }

        return player.getPersistentDataContainer().has(skipCustomBlockBreakHandlersKey, PersistentDataType.BYTE);
    }

    private boolean isWorldEnabled(String worldName) {
        List<String> enabledWorlds = getConfig().getStringList("worlds.enabled");
        return enabledWorlds.isEmpty() || enabledWorlds.contains(worldName);
    }

    private boolean isAutoPickupEnabled(Player player) {
        boolean defaultEnabled = getConfig().getBoolean("settings.auto-pickup-default", true);
        boolean isToggled = toggledAutoPickupPlayers.contains(player.getUniqueId()); //toggledPlayers.contains(player.getUniqueId());

        if (defaultEnabled) {
            return !isToggled;
        }

        return isToggled;
    }

    private boolean hasSilkTouch(ItemStack tool) {
        return tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH);
    }

    private int getFortuneLevel(ItemStack tool) {
        if (tool == null) {
            return 0;
        }

        return tool.getEnchantmentLevel(Enchantment.FORTUNE);
    }

    private boolean isBlockedNormalDrop(Material material) {
        if (!getConfig().getBoolean("settings.block-normal-drops.enabled", false)) {
            return false;
        }

        List<String> blocked = getConfig().getStringList("settings.block-normal-drops.materials");
        return blocked.contains(material.name());
    }

    private boolean isNoCobbleEnabled(Player player) {
        boolean defaultEnabled = getConfig().getBoolean("settings.no-cobble-default", true);
        boolean isToggled = toggledNoCobblePlayers.contains(player.getUniqueId());

        if (defaultEnabled) {
            return !isToggled;
        }
        
        return isToggled;
    }

    private void giveBonusDrops(Player player, Block block, ItemStack tool, boolean autoPickupEnabled) {
        String blockType = block.getType().name();
        String path = "bonus-drops." + blockType;

        if (!getConfig().isList(path)) {
            return;
        }

        List<Map<?, ?>> drops = getConfig().getMapList(path);
        int fortuneLevel = getFortuneLevel(tool);

        for (Map<?, ?> dropConfig : drops) {
            String materialName = String.valueOf(dropConfig.get("material"));
            Material material = Material.matchMaterial(materialName);

            if (material == null) {
                getLogger().warning("Invalid material in config: " + materialName);
                continue;
            }

            double baseChance = getDouble(dropConfig, "chance", 0.0);
            double effectiveChance = calculateEffectiveChance(baseChance, fortuneLevel);

            if ((random.nextDouble() * 100.0) > effectiveChance) {
                continue;
            }

            int minAmount = getInt(dropConfig, "min-amount", 1);
            int maxAmount = getInt(dropConfig, "max-amount", minAmount);

            if (maxAmount < minAmount) {
                maxAmount = minAmount;
            }

            int baseAmount = minAmount + random.nextInt((maxAmount - minAmount) + 1);
            int effectiveAmount = calculateEffectiveAmount(baseAmount, fortuneLevel);

            ItemStack item = new ItemStack(material, effectiveAmount);

            if (autoPickupEnabled) {
                giveOrDrop(player, block, item);
            } else {
                block.getWorld().dropItemNaturally(block.getLocation(), item);
            }

            int minExp = getInt(dropConfig, "min-exp", 0);
            int maxExp = getInt(dropConfig, "max-exp", minExp);

            if (maxExp < minExp) {
                maxExp = minExp;
            }

            if (maxExp > 0) {
                int exp = effectiveAmount*(minExp + random.nextInt((maxExp - minExp) + 1));
                giveBonusExp(player, block, exp, autoPickupEnabled);
            }
        }
    }

    private void giveBonusExp(Player player, Block block, int exp, boolean autoPickupEnabled) {
        if (exp <= 0) {
            return;
        }

        if (autoPickupEnabled) {
            player.giveExp(exp);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.0f);
        } else {
            block.getWorld().spawn(block.getLocation(), ExperienceOrb.class, orb -> {
                orb.setExperience(exp);
            });
        }
    }

    private double calculateEffectiveChance(double baseChance, int fortuneLevel) {
        String mode = getFortuneMode();

        if (fortuneLevel <= 0 || (!mode.equals("CHANCE") && !mode.equals("BOTH"))) {
            return clampChance(baseChance);
        }

        double multiplier = getConfig().getDouble("fortune.chance-multiplier-per-level", 0.25);
        double effectiveChance = baseChance * (1.0 + (fortuneLevel * multiplier));

        return clampChance(effectiveChance);
    }

    private int calculateEffectiveAmount(int baseAmount, int fortuneLevel) {
        String mode = getFortuneMode();

        if (fortuneLevel <= 0 || (!mode.equals("AMOUNT") && !mode.equals("BOTH"))) {
            return clampAmount(baseAmount);
        }

        double multiplier = getConfig().getDouble("fortune.amount-multiplier-per-level", 0.35);
        int effectiveAmount = (int) Math.round(baseAmount * (1.0 + (fortuneLevel * multiplier)));

        return clampAmount(Math.max(1, effectiveAmount));
    }

    private String getFortuneMode() {
        return getConfig().getString("fortune.mode", "AMOUNT").toUpperCase(Locale.ROOT);
    }

    private double clampChance(double chance) {
        double maxChance = getConfig().getDouble("fortune.max-effective-chance", 100.0);

        if (chance < 0.0) {
            return 0.0;
        }

        return Math.min(chance, maxChance);
    }

    private int clampAmount(int amount) {
        int maxAmount = getConfig().getInt("fortune.max-effective-amount", 64);

        if (amount < 1) {
            return 1;
        }

        return Math.min(amount, maxAmount);
    }

    private double getDouble(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int getInt(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void giveOrDrop(Player player, Block block, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);

        if (!leftovers.isEmpty() && getConfig().getBoolean("settings.drop-leftovers-on-ground", true)) {
            for (ItemStack leftover : leftovers.values()) {
                block.getWorld().dropItemNaturally(block.getLocation(), leftover);
            }
        }
    }

    private void showBlockInfo(CommandSender sender, String blockName) {
        Material blockMaterial = Material.matchMaterial(blockName);

        if (blockMaterial == null || !blockMaterial.isBlock()) {
            sender.sendMessage(Component.text("Invalid block: " + blockName, NamedTextColor.RED));
            sender.sendMessage(Component.text("Example: STONE, COBBLESTONE, DEEPSLATE", NamedTextColor.GRAY));
            return;
        }

        String blockKey = blockMaterial.name();
        String path = "bonus-drops." + blockKey;

        if (!getConfig().isList(path)) {
            sender.sendMessage(Component.text("No bonus drops configured for ", NamedTextColor.YELLOW)
                .append(Component.text(blockKey, NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.YELLOW))
            );
            return;
        }

        List<Map<?, ?>> drops = getConfig().getMapList(path);

        if (drops.isEmpty()) {
            sender.sendMessage(Component.text("No bonus drops configured for ", NamedTextColor.YELLOW)
                .append(Component.text(blockKey, NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.YELLOW))
            );
            return;
        }

        sender.sendMessage(Component.text(NamedTextColor.GOLD + "Bonus drops for ")
            .append(Component.text(blockKey, NamedTextColor.WHITE))
            .append(Component.text(":", NamedTextColor.GOLD))
        );

        for (Map<?, ?> dropConfig : drops) {
            String materialName = String.valueOf(dropConfig.get("material"));
            Material dropMaterial = Material.matchMaterial(materialName);

            if (dropMaterial == null) {
                sender.sendMessage(Component.text("- Invalid material in config: " + materialName, NamedTextColor.RED));
                continue;
            }

            double baseChance = getDouble(dropConfig, "chance", 0.0);
            int minAmount = getInt(dropConfig, "min-amount", 1);
            int maxAmount = getInt(dropConfig, "max-amount", minAmount);

            if (maxAmount < minAmount) {
                maxAmount = minAmount;
            }

            String amountText = minAmount == maxAmount
                    ? String.valueOf(minAmount)
                    : minAmount + "-" + maxAmount;

            sender.sendMessage(
                    Component.text("- ", NamedTextColor.GRAY)
                            .append(Component.text(dropMaterial.name(), NamedTextColor.AQUA))
                            .append(Component.text(" | Chance: ", NamedTextColor.GRAY))
                            .append(Component.text(baseChance + "%", NamedTextColor.GREEN))
                            .append(Component.text(" | Amount: ", NamedTextColor.GRAY))
                            .append(Component.text(amountText, NamedTextColor.YELLOW))
            );
        }

        sender.sendMessage(Component.text("Fortune mode: " + getFortuneMode(), NamedTextColor.DARK_GRAY));
    }

    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");

        if (!dataFile.exists()) {
            try {
                getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException exception) {
                getLogger().severe("Could not create data.yml");
                exception.printStackTrace();
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadToggledPlayers(String command) {
        // toggledPlayers.clear();

        // List<String> uuids = dataConfig.getStringList("toggled-players");

        // for (String uuidString : uuids) {
        //     try {
        //         toggledPlayers.add(UUID.fromString(uuidString));
        //     } catch (IllegalArgumentException ignored) {
        //         getLogger().warning("Invalid UUID in data.yml: " + uuidString);
        //     }
        // }

        switch(command) {
            case "nocobble":
                toggledNoCobblePlayers.clear();

                List<String> nocobble_uuids = dataConfig.getStringList("toggled-nocobble-players");

                for (String uuidString : nocobble_uuids) {
                    try {
                        toggledNoCobblePlayers.add(UUID.fromString(uuidString));
                    } catch (IllegalArgumentException ignored) {
                        getLogger().warning("Invalid UUID in toggled-nocobble-players in data.yml" + uuidString);
                    }
                }

                break;

            case "auto":
                toggledAutoPickupPlayers.clear();

                List<String> auto_uuids = dataConfig.getStringList("toggled-auto-players");

                for (String uuidString : auto_uuids) {
                    try {
                        toggledAutoPickupPlayers.add(UUID.fromString(uuidString));
                    } catch (IllegalArgumentException ignored) {
                        getLogger().warning("Invalid UUID in toggled-auto-players in data.yml" + uuidString);
                    }
                }

                break;
        }
    }

    private void saveToggledPlayers(String command) {
        List<String> uuids = new ArrayList<>();

        // for (UUID uuid : toggledPlayers) {
        //     uuids.add(uuid.toString());
        // }

        switch (command) {
            case "nocobble":

                for (UUID uuid : toggledNoCobblePlayers) {
                    uuids.add(uuid.toString());
                }
                
                dataConfig.set("toggled-nocobble-players", uuids);
                
                break;
            
            case "auto":

                for (UUID uuid : toggledAutoPickupPlayers) {
                    uuids.add(uuid.toString());
                }

                dataConfig.set("toggled-auto-players", uuids);

                break;
        }

        // dataConfig.set("toggled-players", uuids);

        try {
            dataConfig.save(dataFile);
        } catch (IOException exception) {
            getLogger().severe("Could not save data.yml");
            exception.printStackTrace();
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(NamedTextColor.YELLOW + "MiningDrops commands:");
        sender.sendMessage(Component.text("/miningdrops toggle", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/miningdrops nocobble", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/miningdrops status", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/miningdrops info <block>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/miningdrops reload", NamedTextColor.GRAY));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("miningdrops")) {
            return false;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("miningdrops.admin")) {
                sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
                return true;
            }

            reloadConfig();
            sender.sendMessage(Component.text("MiningDrops config reloaded.", NamedTextColor.GREEN));
            return true;
        }

        // if (args[0].equalsIgnoreCase("toggle")) {
        //     if (!(sender instanceof Player player)) {
        //         sender.sendMessage(NamedTextColor.RED + "Only players can use this command.");
        //         return true;
        //     }

        //     if (!player.hasPermission("miningdrops.toggle")) {
        //         player.sendMessage(NamedTextColor.RED + "You do not have permission.");
        //         return true;
        //     }

        //     UUID uuid = player.getUniqueId();

        //     if (toggledPlayers.contains(uuid)) {
        //         toggledPlayers.remove(uuid);
        //     } else {
        //         toggledPlayers.add(uuid);
        //     }

        //     saveToggledPlayers();

        //     boolean enabled = isAutoPickupEnabled(player);

        //     player.sendMessage(NamedTextColor.YELLOW + "Auto-pickup is now " +
        //             (enabled ? NamedTextColor.GREEN + "enabled" : NamedTextColor.RED + "disabled") +
        //             NamedTextColor.YELLOW + ".");

        //     return true;
        // }

        if (args[0].equalsIgnoreCase("auto")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                return true;
            }

            if (!player.hasPermission("miningdrops.auto")) {
                player.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
                return true;
            }

            UUID uuid = player.getUniqueId();

            if (toggledAutoPickupPlayers.contains(uuid)) {
                toggledAutoPickupPlayers.remove(uuid);
            } else {
                toggledAutoPickupPlayers.add(uuid);
            }

            saveToggledPlayers("auto");

            boolean enabled = isAutoPickupEnabled(player);

            player.sendMessage(Component.text("Auto-pickup is now ", NamedTextColor.YELLOW)
                    .append(enabled ? Component.text("enabled", NamedTextColor.GREEN) : Component.text("disabled", NamedTextColor.RED))
                    .append(Component.text(".", NamedTextColor.YELLOW))
                );

            return true;
        }

        if (args[0].equalsIgnoreCase("nocobble")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                return true;
            }

            if (!player.hasPermission("miningdrops.nocobble")) {
                player.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
                return true;
            }
            UUID uuid = player.getUniqueId();

            if(toggledNoCobblePlayers.contains(uuid)) {
                toggledNoCobblePlayers.remove(uuid);
            } else {
                toggledNoCobblePlayers.add(uuid);
            }

            saveToggledPlayers("nocobble");

            boolean enabled = isNoCobbleEnabled(player);

            player.sendMessage(Component.text("NoCobble is now ", NamedTextColor.YELLOW)
                .append(enabled ? Component.text("enabled", NamedTextColor.GREEN) : Component.text("disabled", NamedTextColor.RED))
            );
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                return true;
            }

            boolean enabled = isAutoPickupEnabled(player);

            player.sendMessage(Component.text("Auto-pickup: ", NamedTextColor.YELLOW)
                    .append(enabled ? Component.text("enabled", NamedTextColor.GREEN) : Component.text("disabled", NamedTextColor.RED))
                );

            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            if (!sender.hasPermission("miningdrops.info")) {
                sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /miningdrops info <block>", NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Example: /miningdrops info STONE", NamedTextColor.GRAY));
                return true;
            }

            showBlockInfo(sender, args[1]);
            return true;
        }

        sendUsage(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (!command.getName().equalsIgnoreCase("miningdrops")) {
            return suggestions;
        }

        if (args.length == 1) {
            // suggestions.add("toggle");
            suggestions.add("auto");
            suggestions.add("nocobble");
            suggestions.add("status");
            suggestions.add("info");

            if (sender.hasPermission("miningdrops.admin")) {
                suggestions.add("reload");
            }

            return filterSuggestions(suggestions, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            ConfigurationSection section = getConfig().getConfigurationSection("bonus-drops");

            if (section != null) {
                suggestions.addAll(section.getKeys(false));
            }

            return filterSuggestions(suggestions, args[1]);
        }

        return suggestions;
    }

    private List<String> filterSuggestions(List<String> suggestions, String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();

        for (String suggestion : suggestions) {
            if (suggestion.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                filtered.add(suggestion);
            }
        }

        return filtered;
    }
}