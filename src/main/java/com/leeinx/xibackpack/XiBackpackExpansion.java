package com.leeinx.xibackpack;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class XiBackpackExpansion extends PlaceholderExpansion {
    private final XiBackpack plugin;

    public XiBackpackExpansion(XiBackpack plugin) {
        this.plugin = plugin;
    }

    @Override
    @NotNull
    public String getIdentifier(){
        return "xibackpack";
    }
    @Override
    @NotNull
    public String getAuthor(){
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    @NotNull
    public String getVersion(){
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist(){
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params){
        if(player ==  null){
            return "Error!";
        }

        //%xibackpack_player_size% 玩家背包大小
        if(params.equalsIgnoreCase("player_size")){
            PlayerBackpack backpack = plugin.getBackpackManager().getBackpack(player.getPlayer());
            return backpack!=null?String.valueOf(backpack.getSize()): "0";
        }

        //%xibackpack_team_count% 有多少个团队背包
        if(params.equalsIgnoreCase("team_count")){
            return String.valueOf(plugin.getTeamBackpackManager().getCachedTeamCount(player.getUniqueId()));
        }


        return null;
    }
}
