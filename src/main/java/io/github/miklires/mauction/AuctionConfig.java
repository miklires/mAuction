package io.github.miklires.mauction;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AuctionConfig {
    private static final List<String> DEFAULT_BLOCKED = List.of("BEDROCK", "BARRIER", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "STRUCTURE_BLOCK", "JIGSAW");
    private final MAuctionPlugin plugin;
    AuctionConfig(MAuctionPlugin plugin) { this.plugin = plugin; }
    void load() {
        plugin.saveDefaultConfig(); plugin.reloadConfig(); FileConfiguration config = plugin.getConfig(); config.options().copyDefaults(true);
        config.set("config-version", 2);
        String language = config.getString("language", "en_US");
        if (!"en_US".equalsIgnoreCase(language) && !"ru_RU".equalsIgnoreCase(language)) config.set("language", "en_US");
        bounded(config, "listing.limit-per-player", 1, 100, 10); bounded(config, "listing.duration-hours", 1, 720, 48);
        bounded(config, "listing.sell-cooldown-seconds", 0, 60, 2); bounded(config, "listing.maximum-serialized-bytes", 65_536, ItemCodec.HARD_MAX_BYTES, 1_048_576);
        normalizeMaterials(config);
        double minimum = finite(config.getDouble("price.minimum", 1.0), 0.01, 1_000_000_000_000.0, 1.0);
        double maximum = finite(config.getDouble("price.maximum", 1_000_000_000.0), minimum, 1_000_000_000_000.0, 1_000_000_000.0);
        config.set("price.minimum", minimum); config.set("price.maximum", maximum);
        config.set("economy.tax-percent", finite(config.getDouble("economy.tax-percent", 5.0), 0.0, 100.0, 5.0));
        bounded(config, "economy.fraction-digits", 0, 6, 2); bounded(config, "gui.live-refresh-ticks", 10, 200, 20); bounded(config, "gui.page-size", 9, 45, 45);
        bounded(config, "transactions.reservation-timeout-seconds", 30, 3600, 300); bounded(config, "transactions.audit-retention-days", 1, 3650, 180);
        plugin.saveConfig();
    }
    private void normalizeMaterials(FileConfiguration config) {
        Set<String> values = new LinkedHashSet<>();
        for (String raw : config.getStringList("listing.blocked-materials")) { String name = raw.strip().toUpperCase(Locale.ROOT); Material material = Material.matchMaterial(name); if (material != null && material.isItem()) values.add(material.name()); if (values.size() == 256) break; }
        config.set("listing.blocked-materials", values.isEmpty() ? DEFAULT_BLOCKED : List.copyOf(values));
    }
    private static int bounded(FileConfiguration config, String path, int minimum, int maximum, int fallback) { int value = config.getInt(path, fallback); if (value < minimum || value > maximum) { config.set(path, fallback); return fallback; } return value; }
    private static double finite(double value, double minimum, double maximum, double fallback) { return Double.isFinite(value) && value >= minimum && value <= maximum ? value : fallback; }
}
