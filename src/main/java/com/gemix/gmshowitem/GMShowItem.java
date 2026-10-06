package com.gemix.gmshowitem;

import org.bukkit.plugin.java.JavaPlugin;

public class GMShowItem extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("GMShowItem has been enabled!");
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("GMShowItem has been disabled!");
    }
}
