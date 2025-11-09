package com.leeinx.xibackpack;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.util.Map;

public class CommandHandler implements CommandExecutor {
    private XiBackpack plugin;
    private Economy economy = null;

    public CommandHandler(XiBackpack plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessage("command.player_only"));
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("xibackpack")) {
            // 检查基本权限
            if (!player.hasPermission("xibackpack.use")) {
                player.sendMessage("§c您没有权限使用此命令!");
                return true;
            }
            
            if (args.length == 0) {
                // 默认打开背包
                // 检查冷却时间
                if (!plugin.checkAndApplyCooldown(player)) {
                    return true;
                }
                plugin.getBackpackManager().openBackpack(player);
                return true;
            } else if (args.length >= 1) {
                if (args[0].equalsIgnoreCase("open")) {
                    // 打开背包
                    // 检查冷却时间
                    if (!plugin.checkAndApplyCooldown(player)) {
                        return true;
                    }
                    plugin.getBackpackManager().openBackpack(player);
                    return true;
                } else if (args[0].equalsIgnoreCase("upgrade")) {
                    // 升级背包
                    upgradeBackpack(player);
                    return true;
                } else if (args[0].equalsIgnoreCase("backup")) {
                    // 备份背包
                    if (args.length >= 2 && args[1].equalsIgnoreCase("create")) {
                        createBackup(player);
                        return true;
                    } else if (args.length >= 3 && args[1].equalsIgnoreCase("restore")) {
                        restoreBackup(player, args[2]);
                        return true;
                    } else {
                        showBackupHelp(player);
                        return true;
                    }
                } else if (args[0].equalsIgnoreCase("help")) {
                    // 显示帮助
                    showHelp(player);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 升级玩家背包
     * @param player 玩家
     */
    private void upgradeBackpack(Player player) {
        if (economy == null) {
            player.sendMessage(plugin.getMessage("backpack.economy_not_enabled"));
            return;
        }

        // 获取玩家当前背包
        PlayerBackpack backpack = plugin.getBackpackManager().getBackpack(player);
        int currentSize = backpack.getSize();
        int upgradeCost = plugin.getConfig().getInt("backpack.upgrade-cost", 1000);

        // 检查玩家是否有足够金币
        if (!economy.has(player, upgradeCost)) {
            player.sendMessage(plugin.getMessage("backpack.upgrade_insufficient_funds", "cost", String.valueOf(upgradeCost)));
            return;
        }

        // 扣除金币
        economy.withdrawPlayer(player, upgradeCost);

        // 升级背包大小（每次增加9格，没有上限）
        int newSize = currentSize + 9;
        backpack.setSize(newSize);

        // 保存背包
        plugin.getBackpackManager().saveBackpack(backpack);

        player.sendMessage(plugin.getMessage("backpack.upgrade_success", 
            "size", String.valueOf(newSize), 
            "cost", String.valueOf(upgradeCost)));
    }

    /**
     * 创建背包备份
     * @param player 玩家
     */
    private void createBackup(Player player) {
        // 检查管理权限
        if (!player.hasPermission("xibackpack.admin")) {
            player.sendMessage("§c您没有权限创建背包备份!");
            return;
        }
        
        // 创建备份逻辑
        PlayerBackpack backpack = plugin.getBackpackManager().getBackpack(player);
        String backupData = backpack.serialize();
        
        // 保存备份到数据库
        boolean success = plugin.getDatabaseManager().savePlayerBackpackBackup(
            player.getUniqueId(), 
            backupData, 
            "manual_backup_" + System.currentTimeMillis()
        );
        
        if (success) {
            player.sendMessage("§a背包备份创建成功!");
        } else {
            player.sendMessage("§c背包备份创建失败!");
        }
    }
    
    /**
     * 恢复背包备份
     * @param player 玩家
     * @param backupId 备份ID
     */
    private void restoreBackup(Player player, String backupId) {
        // 检查管理权限
        if (!player.hasPermission("xibackpack.admin")) {
            player.sendMessage("§c您没有权限恢复背包备份!");
            return;
        }
        
        // 从数据库加载备份
        String backupData = plugin.getDatabaseManager().loadPlayerBackpackBackup(
            player.getUniqueId(), 
            backupId
        );
        
        if (backupData == null) {
            player.sendMessage("§c未找到指定的备份数据!");
            return;
        }
        
        // 反序列化备份数据
        PlayerBackpack backupBackpack = PlayerBackpack.deserialize(backupData, player.getUniqueId());
        
        // 应用备份到当前背包
        PlayerBackpack currentBackpack = plugin.getBackpackManager().getBackpack(player);
        currentBackpack.getItems().clear();
        
        // 复制备份的物品到当前背包
        for (Map.Entry<Integer, ItemStack> entry : backupBackpack.getItems().entrySet()) {
            currentBackpack.setItem(entry.getKey(), entry.getValue());
        }
        
        currentBackpack.setSize(backupBackpack.getSize());
        
        // 保存背包
        plugin.getBackpackManager().saveBackpack(currentBackpack);
        
        player.sendMessage("§a背包已从备份恢复: " + backupId);
    }
    
    /**
     * 显示备份相关帮助
     * @param player 玩家
     */
    private void showBackupHelp(Player player) {
        player.sendMessage("§e======= §6背包备份命令 §e=======");
        player.sendMessage("§a/xibackpack backup create §7- §f创建背包备份");
        player.sendMessage("§a/xibackpack backup restore <ID> §7- §f恢复指定备份");
    }

    /**
     * 显示帮助信息
     * @param player 玩家
     */
    private void showHelp(Player player) {
        player.sendMessage(plugin.getMessage("command.help_title"));
        player.sendMessage(plugin.getMessage("command.help_open"));
        player.sendMessage(plugin.getMessage("command.help_upgrade"));
        player.sendMessage("§a/xibackpack backup create §7- §f创建背包备份");
        player.sendMessage("§a/xibackpack backup restore <ID> §7- §f恢复指定备份");
        player.sendMessage(plugin.getMessage("command.help_help"));
        player.sendMessage(plugin.getMessage("command.help_legacy"));
    }

    /**
     * 设置经济系统
     */
    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }
}