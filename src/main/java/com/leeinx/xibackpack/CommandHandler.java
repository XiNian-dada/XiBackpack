package com.leeinx.xibackpack;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

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
                    } else if (args[0].equalsIgnoreCase("team")) {
                        // 团队背包命令
                        handleTeamCommand(player, args);
                        return true;
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
            plugin.getLogger().log(Level.SEVERE, "处理命令时出错", e);
            sender.sendMessage("§c处理命令时发生错误，请联系管理员");
        }
        return false;
    }
    
    /**
     * 升级背包
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
            
            // 检查是否达到最大容量限制 (10页 = 10 * 45 = 450格)
            if (currentSize >= 450) {
                player.sendMessage(plugin.getMessage("backpack.upgrade_max_capacity"));
                return;
            }
            
            // 检查配置中是否有针对当前容量的自定义权限设置
            String permissionPath = "backpack.size-permissions." + currentSize;
            String requiredPermission = plugin.getConfig().getString(permissionPath);
            
            // 如果有自定义权限设置，检查玩家是否具有该权限
            if (requiredPermission != null && !requiredPermission.isEmpty()) {
                if (!player.hasPermission(requiredPermission)) {
                    player.sendMessage(plugin.getMessage("backpack.upgrade_insufficient_permission", 
                        "permission", requiredPermission, 
                        "size", String.valueOf(currentSize)));
                    return;
                }
            }
            
            // 获取升级费用配置（按段计算）
            int upgradeCost = getUpgradeCostForSize(currentSize);

            // 检查玩家是否有足够金币
            if (!economy.has(player, upgradeCost)) {
                player.sendMessage(plugin.getMessage("backpack.upgrade_insufficient_funds", "cost", String.valueOf(upgradeCost)));
                return;
            }

            // 扣除金币
            economy.withdrawPlayer(player, upgradeCost);

            // 升级背包大小（每次增加9格，没有上限）
            int newSize = currentSize + 9;
            // 确保不超过最大容量
            newSize = Math.min(newSize, 450);
            backpack.setSize(newSize);

            // 保存背包
            plugin.getBackpackManager().saveBackpack(backpack);

            player.sendMessage(plugin.getMessage("backpack.upgrade_success", 
                "size", String.valueOf(newSize), 
                "cost", String.valueOf(upgradeCost)));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "升级背包时出错", e);
            player.sendMessage("§c升级背包时发生错误，请联系管理员");
        }
    }
    
    /**
     * 根据当前背包大小获取升级费用（按段计算）
     * @param currentSize 当前背包大小
     * @return 升级费用
     */
    private int getUpgradeCostForSize(int currentSize) {
        // 获取所有配置的费用点
        Map<String, Object> upgradeCosts = plugin.getConfig().getConfigurationSection("backpack.upgrade-costs") != null ?
                plugin.getConfig().getConfigurationSection("backpack.upgrade-costs").getValues(false) : new HashMap<>();
        
        // 按照键（容量）排序
        List<Integer> costPoints = new ArrayList<>();
        for (String key : upgradeCosts.keySet()) {
            try {
                costPoints.add(Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
                // 忽略无效的键
            }
        }
        Collections.sort(costPoints);
        
        // 查找当前容量对应的费用段
        int cost = plugin.getConfig().getInt("backpack.upgrade-cost", 1000); // 默认费用
        
        // 从高到低查找匹配的费用段起点
        for (int i = costPoints.size() - 1; i >= 0; i--) {
            int point = costPoints.get(i);
            if (currentSize >= point) {
                cost = plugin.getConfig().getInt("backpack.upgrade-costs." + point, cost);
                break;
            }
        }
        
        return cost;
    }

    /**
     * 创建背包备份
     * @param player 玩家
     */
    private void createBackup(Player player) {
        if (player == null) {
            plugin.getLogger().warning("尝试为null玩家创建背包备份");
            return;
        }
        
        try {
            // 检查管理权限
            if (!player.hasPermission("xibackpack.admin")) {
                player.sendMessage(plugin.getMessage("backpack.backup_no_permission"));
                return;
            }
            
            // 获取玩家背包
            PlayerBackpack backpack = plugin.getBackpackManager().getBackpack(player);
            
            // 生成备份ID（使用时间戳）
            String backupId = "backup_" + System.currentTimeMillis();
            
            // 序列化背包数据
            String backpackData = backpack.serialize();
            
            // 保存到数据库
            boolean success = plugin.getDatabaseManager().savePlayerBackpackBackup(
                player.getUniqueId(), 
                backupId, 
                backpackData
            );
            
            if (success) {
                player.sendMessage(plugin.getMessage("backpack.backup_created", "id", backupId));
            } else {
                player.sendMessage(plugin.getMessage("backpack.backup_create_failed"));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "创建背包备份时出错", e);
            player.sendMessage("§c创建背包备份时发生错误，请联系管理员");
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
            plugin.getLogger().log(Level.SEVERE, "恢复背包备份时出错", e);
            player.sendMessage("§c恢复背包备份时发生错误，请联系管理员");
        }
    }
    
    /**
     * 处理团队背包相关命令
     * @param player 玩家
     * @param args 命令参数
     */
    private void handleTeamCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /xibackpack team <create|open|addmember|removemember>");
            return;
        }
        
        if (args[1].equalsIgnoreCase("create")) {
            // 创建团队背包
            if (args.length < 3) {
                player.sendMessage("§c用法: /xibackpack team create <名称>");
                return;
            }
            
            // 检查是否有权限创建团队背包
            if (!player.hasPermission("xibackpack.team.create")) {
                player.sendMessage("§c您没有权限创建团队背包!");
                return;
            }
            
            // 使用Vault检查创建团队背包的费用
            if (!economyAvailable) {
                player.sendMessage(plugin.getMessage("backpack.economy_not_enabled"));
                return;
            }
            
            int createCost = plugin.getConfig().getInt("team-backpack.create-cost", 5000);
            if (!economy.has(player, createCost)) {
                player.sendMessage(plugin.getMessage("team-backpack.create_insufficient_funds", "cost", String.valueOf(createCost)));
                return;
            }
            
            // 扣除费用
            economy.withdrawPlayer(player, createCost);
            
            // 组合背包名称
            StringBuilder nameBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                nameBuilder.append(args[i]);
                if (i < args.length - 1) {
                    nameBuilder.append(" ");
                }
            }
            String backpackName = nameBuilder.toString();
            
            // 创建团队背包
            String backpackId = plugin.getTeamBackpackManager().createBackpack(player, backpackName);
            
            player.sendMessage(plugin.getMessage("team-backpack.create_success", "name", backpackName)
                .replace("{id}", backpackId)
                .replace("{cost}", String.valueOf(createCost)));

        } else if (args[1].equalsIgnoreCase("open")) {
            // 打开团队背包
            if (args.length < 3) {
                player.sendMessage("§c用法: /xibackpack team open <ID>");
                return;
            }
            
            String backpackId = args[2];
            plugin.getTeamBackpackManager().openBackpack(player, backpackId);
        } else if (args[1].equalsIgnoreCase("addmember")) {
            // 添加成员到团队背包
            if (args.length < 4) {
                player.sendMessage("§c用法: /xibackpack team addmember <背包ID> <玩家名>");
                return;
            }
            
            String backpackId = args[2];
            String playerName = args[3];
            
            // TODO: 实现添加成员逻辑
            player.sendMessage("§e添加成员功能将在后续版本中实现");
        } else if (args[1].equalsIgnoreCase("removemember")) {
            // 从团队背包移除成员
            if (args.length < 4) {
                player.sendMessage("§c用法: /xibackpack team removemember <背包ID> <玩家名>");
                return;
            }
            
            String backpackId = args[2];
            String playerName = args[3];
            
            // TODO: 实现移除成员逻辑
            player.sendMessage("§e移除成员功能将在后续版本中实现");
        } else if (args[1].equalsIgnoreCase("list")) {
            // 列出玩家可以访问的所有团队背包
            // TODO: 实现列出背包逻辑
            player.sendMessage("§e列出团队背包功能将在后续版本中实现");
        } else {
            player.sendMessage("§c未知的团队背包子命令: " + args[1]);
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
        
        player.sendMessage(plugin.getMessage("backpack.backup_help_header"));
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
            player.sendMessage(plugin.getMessage("command.help_header"));
            player.sendMessage(plugin.getMessage("command.help_open"));
            player.sendMessage(plugin.getMessage("command.help_upgrade"));
            player.sendMessage(plugin.getMessage("command.help_backup"));
            player.sendMessage(plugin.getMessage("command.help_team"));
            player.sendMessage(plugin.getMessage("command.help_help"));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "显示帮助信息时出错", e);
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
            plugin.getLogger().log(Level.SEVERE, "设置经济系统时出错", e);
            return false;
        }
    }
}