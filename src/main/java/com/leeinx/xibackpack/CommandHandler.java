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
    private boolean economyAvailable = false;

    public CommandHandler(XiBackpack plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        
        this.plugin = plugin;
        economyAvailable = setupEconomy();
        if (economyAvailable) {
            plugin.getLogger().info("经济系统集成成功");
        } else {
            plugin.getLogger().warning("经济系统不可用，背包升级功能将被禁用");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
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
                    } else {
                        // 未知子命令
                        player.sendMessage("§c未知的子命令: " + args[0]);
                        showHelp(player);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("处理命令时出错: " + e.getMessage());
            sender.sendMessage("§c处理命令时发生错误，请联系管理员");
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 升级玩家背包
     * @param player 玩家
     */
    private void upgradeBackpack(Player player) {
        if (player == null) {
            plugin.getLogger().warning("尝试为null玩家升级背包");
            return;
        }
        
        try {
            if (!economyAvailable) {
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
        } catch (Exception e) {
            plugin.getLogger().severe("升级背包时出错: " + e.getMessage());
            player.sendMessage("§c升级背包时发生错误，请联系管理员");
            e.printStackTrace();
        }
    }

    /**
     * 创建背包备份
     * @param player 玩家
     */
    private void createBackup(Player player) {
        if (player == null) {
            plugin.getLogger().warning("尝试为null玩家创建备份");
            return;
        }
        
        try {
            // 检查管理权限
            if (!player.hasPermission("xibackpack.admin")) {
                player.sendMessage(plugin.getMessage("backpack.backup_no_permission"));
                return;
            }
            
            // 创建备份逻辑
            PlayerBackpack backpack = plugin.getBackpackManager().getBackpack(player);
            String backupData = backpack.serialize();
            
            // 生成备份ID
            String backupId = "manual_backup_" + System.currentTimeMillis();
            
            // 保存备份到数据库
            boolean success = plugin.getDatabaseManager().savePlayerBackpackBackup(
                player.getUniqueId(), 
                backupData, 
                backupId
            );
            
            if (success) {
                player.sendMessage(plugin.getMessage("backpack.backup_created", "id", backupId));
            } else {
                player.sendMessage(plugin.getMessage("backpack.backup_create_failed"));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("创建背包备份时出错: " + e.getMessage());
            player.sendMessage("§c创建背包备份时发生错误，请联系管理员");
            e.printStackTrace();
        }
    }
    
    /**
     * 恢复背包备份
     * @param player 玩家
     * @param backupId 备份ID
     */
    private void restoreBackup(Player player, String backupId) {
        if (player == null || backupId == null) {
            plugin.getLogger().warning("恢复背包备份时参数为空: player=" + player + ", backupId=" + backupId);
            return;
        }
        
        try {
            // 检查管理权限
            if (!player.hasPermission("xibackpack.admin")) {
                player.sendMessage(plugin.getMessage("backpack.backup_no_permission"));
                return;
            }
            
            // 验证备份ID格式
            if (!backupId.matches("[a-zA-Z0-9_\\-]+")) {
                player.sendMessage(plugin.getMessage("backpack.backup_invalid_id"));
                return;
            }
            
            // 从数据库加载备份
            String backupData = plugin.getDatabaseManager().loadPlayerBackpackBackup(
                player.getUniqueId(), 
                backupId
            );
            
            if (backupData == null) {
                player.sendMessage(plugin.getMessage("backpack.backup_not_found"));
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
            
            player.sendMessage(plugin.getMessage("backpack.backup_restored", "id", backupId));
        } catch (Exception e) {
            plugin.getLogger().severe("恢复背包备份时出错: " + e.getMessage());
            player.sendMessage("§c恢复背包备份时发生错误，请联系管理员");
            e.printStackTrace();
        }
    }
    
    /**
     * 显示备份相关帮助
     * @param player 玩家
     */
    private void showBackupHelp(Player player) {
        if (player == null) {
            return;
        }
        
        player.sendMessage(plugin.getMessage("backpack.backup_help_title"));
        player.sendMessage(plugin.getMessage("backpack.backup_help_create"));
        player.sendMessage(plugin.getMessage("backpack.backup_help_restore"));
    }

    /**
     * 显示帮助信息
     * @param player 玩家
     */
    private void showHelp(Player player) {
        if (player == null) {
            return;
        }
        
        try {
            player.sendMessage(plugin.getMessage("command.help_title"));
            player.sendMessage(plugin.getMessage("command.help_open"));
            player.sendMessage(plugin.getMessage("command.help_upgrade"));
            player.sendMessage(plugin.getMessage("backpack.backup_help_create"));
            player.sendMessage(plugin.getMessage("backpack.backup_help_restore"));
            player.sendMessage(plugin.getMessage("command.help_help"));
            player.sendMessage(plugin.getMessage("command.help_legacy"));
        } catch (Exception e) {
            plugin.getLogger().severe("显示帮助信息时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 设置经济系统
     */
    private boolean setupEconomy() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
                return false;
            }
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                return false;
            }
            economy = rsp.getProvider();
            return economy != null;
        } catch (Exception e) {
            plugin.getLogger().severe("设置经济系统时出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}