package io.github.miklires.mauction;

import java.io.File;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

final class Messages {
    private final JavaPlugin plugin;
    private YamlConfiguration selected;
    private YamlConfiguration english;

    Messages(JavaPlugin plugin) { this.plugin = plugin; reload(); }

    void reload() {
        save("en_US"); save("ru_RU");
        english = load("en_US");
        String locale = plugin.getConfig().getString("language", "en_US");
        if (locale == null || !locale.matches("[A-Za-z]{2}_[A-Za-z]{2}")) locale = "en_US";
        File file = new File(plugin.getDataFolder(), "lang/" + locale + ".yml");
        if (!file.isFile()) {
            plugin.getLogger().warning("Unknown language " + locale + "; using en_US");
            selected = english;
        } else selected = YamlConfiguration.loadConfiguration(file);
    }

    String text(String key, Object... replacements) {
        String value = selected.getString(key, english.getString(key, key));
        for (int i = 0; i + 1 < replacements.length; i += 2)
            value = value.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    private void save(String locale) { String path="lang/"+locale+".yml"; if(!new File(plugin.getDataFolder(),path).isFile()) plugin.saveResource(path,false); }
    private YamlConfiguration load(String locale) { return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"lang/"+locale+".yml")); }
}
