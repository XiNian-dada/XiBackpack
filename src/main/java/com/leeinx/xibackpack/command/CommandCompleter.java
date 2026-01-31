package com.leeinx.xibackpack.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.leeinx.xibackpack.main.XiBackpack;

public class CommandCompleter implements TabCompleter {
    private XiBackpack plugin;

    /**
     * 构造函数，初始化命令补全器
     * @param plugin 插件主类实例
     * @throws IllegalArgumentException 当plugin为null时抛出
     */
    public CommandCompleter(XiBackpack plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("xibackpack")) {
            if (args.length == 1) {
                // 一级命令补全
                completions.add("open");
                completions.add("upgrade");
                completions.add("backup");
                completions.add("team");
                completions.add("teamgui");
                completions.add("help");
                completions.add("reload");
            } else if (args.length == 2) {
                // 二级命令补全
                switch (args[0].toLowerCase()) {
                    case "backup":
                        completions.add("create");
                        completions.add("restore");
                        completions.add("list");
                        break;
                    case "team":
                        completions.add("create");
                        completions.add("open");
                        completions.add("addmember");
                        completions.add("removemember");
                        completions.add("list");
                        completions.add("manage");
                        completions.add("gui");
                        break;
                }
            } else if (args.length == 3) {
                // 三级命令补全
                switch (args[0].toLowerCase()) {
                    case "backup":
                        if (args[1].equalsIgnoreCase("restore")) {
                            completions.add("index");
                            // 添加备份ID补全
                            List<String> backupIds = plugin.getDatabaseManager().getPlayerBackupIds(player.getUniqueId());
                            completions.addAll(backupIds);
                        }
                        break;
                    case "team":
                        switch (args[1].toLowerCase()) {
                            case "open":
                                // 添加团队背包ID补全
                                List<String> teamBackpackIds = plugin.getDatabaseManager().getPlayerOwnedTeamBackpacks(player.getUniqueId());
                                completions.addAll(teamBackpackIds);
                                break;
                            case "addmember":
                            case "removemember":
                                // 添加团队背包ID补全
                                teamBackpackIds = plugin.getDatabaseManager().getPlayerOwnedTeamBackpacks(player.getUniqueId());
                                completions.addAll(teamBackpackIds);
                                break;
                        }
                        break;
                }
            } else if (args.length == 4) {
                // 四级命令补全
                switch (args[0].toLowerCase()) {
                    case "backup":
                        if (args[1].equalsIgnoreCase("restore") && args[2].equalsIgnoreCase("index")) {
                            // 添加索引补全（1-10）
                            for (int i = 1; i <= 10; i++) {
                                completions.add(String.valueOf(i));
                            }
                        }
                        break;
                    case "team":
                        switch (args[1].toLowerCase()) {
                            case "addmember":
                            case "removemember":
                                // 添加在线玩家名补全
                                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                    completions.add(onlinePlayer.getName());
                                }
                                break;
                        }
                        break;
                }
            }
        }

        // 过滤补全选项，只返回以当前输入开头的选项
        List<String> filteredCompletions = new ArrayList<>();
        String lastArg = args[args.length - 1];
        for (String completion : completions) {
            if (completion.toLowerCase().startsWith(lastArg.toLowerCase())) {
                filteredCompletions.add(completion);
            }
        }

        return filteredCompletions;
    }
}
