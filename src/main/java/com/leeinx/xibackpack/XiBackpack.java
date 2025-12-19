package com.leeinx.xibackpack;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;


//TODO Multi upgrade method
//TODO Multi player backpack
//TODO Backup restore


public final class XiBackpack extends JavaPlugin implements Listener {
    private static XiBackpack instance;
    private DatabaseManager databaseManager;
    private BackpackManager backpackManager;
    private TeamBackpackManager teamBackpackManager; // 添加团队背包管理器
    private CommandHandler commandHandler;
    private FileConfiguration messagesConfig;
    private String language;
    // 冷却时间记录
    private Map<UUID, Long> cooldowns = new HashMap<>();
    
    // 性能监控
    private long totalBackpackOpens = 0;
    private long totalDatabaseOperations = 0;

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置
        saveDefaultConfig();
        
        // 验证配置
        validateConfig();
        
        // 获取语言设置
        language = getConfig().getString("language", "zh");
        
        // 加载消息配置
        loadMessagesConfig();

        // 初始化数据库管理器
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "数据库初始化失败", e);
            // 禁用插件
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 初始化背包管理器
        try {
            backpackManager = new BackpackManager(this);
            teamBackpackManager = new TeamBackpackManager(this); // 初始化团队背包管理器
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "背包管理器初始化失败", e);
            // 禁用插件
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // 初始化指令处理器
        try {
            commandHandler = new CommandHandler(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "指令处理器初始化失败", e);
            // 禁用插件
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 注册命令和事件监听器
        registerCommands();
        registerEvents();

        getLogger().info(getMessage("plugin.enabled"));
        getLogger().info("插件初始化完成，准备就绪");
    }
    
    /**
     * 验证配置文件
     */
    private void validateConfig() {
        // 验证数据库配置
        String dbType = getConfig().getString("database.type", "mysql");
        if (!dbType.matches("mysql|postgresql|mongodb")) {
            getLogger().warning("数据库类型配置无效: " + dbType + "，使用默认值 mysql");
            getConfig().set("database.type", "mysql");
        }
        
        // 验证背包配置
        int backpackSize = getConfig().getInt("backpack.size", 27);
        if (backpackSize <= 0) {
            getLogger().warning("背包大小配置无效: " + backpackSize + "，使用默认值 27");
            getConfig().set("backpack.size", 27);
        }
        
        // 验证冷却时间配置
        long cooldown = getConfig().getLong("backpack.cooldown", 1000);
        if (cooldown < 0) {
            getLogger().warning("冷却时间配置无效: " + cooldown + "，使用默认值 1000");
            getConfig().set("backpack.cooldown", 1000);
        }
        
        // 验证备份数量配置
        int maxBackups = getConfig().getInt("backpack.backup.max-count", 10);
        if (maxBackups <= 0) {
            getLogger().warning("最大备份数量配置无效: " + maxBackups + "，使用默认值 10");
            getConfig().set("backpack.backup.max-count", 10);
        }
    }

    private void registerCommands() {
        // 注册命令执行器
        try {
            this.getCommand("backpack").setExecutor(this);
            this.getCommand("xibackpack").setExecutor(commandHandler);
            getLogger().info("命令注册完成");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "命令注册失败", e);
        }
    }

    private void registerEvents() {
        // 注册事件监听器
        try {
            Bukkit.getPluginManager().registerEvents(this, this);
            getLogger().info("事件监听器注册完成");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "事件监听器注册失败", e);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("backpack")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                
                // 检查权限
                if (!player.hasPermission("xibackpack.use")) {
                    player.sendMessage("§c您没有权限使用此命令!");
                    return true;
                }
                
                // 检查冷却时间
                if (!checkAndApplyCooldown(player)) {
                    return true;
                }
                
                // 增加统计计数
                totalBackpackOpens++;
                
                backpackManager.openBackpack(player);
                return true;
            } else {
                sender.sendMessage(getMessage("command.player_only"));
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 玩家退出时保存背包数据
        try {
            Player player = event.getPlayer();
            PlayerBackpack backpack = backpackManager.getBackpack(player);
            backpackManager.saveBackpack(backpack);
            
            // 移除冷却时间记录
            cooldowns.remove(player.getUniqueId());
            
            getLogger().fine("玩家 " + player.getName() + " 的背包数据已保存");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "保存玩家背包数据时出错", e);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // 当玩家关闭背包时更新背包数据
        if (event.getPlayer() instanceof Player) {
            try {
                Player player = (Player) event.getPlayer();
                // 检查是否是我们插件创建的背包界面
                if (backpackManager.isCloudBackpackInventory(event.getInventory())) {
                    backpackManager.updateBackpackFromInventory(player, event.getInventory());
                    getLogger().fine("玩家 " + player.getName() + " 的个人背包已更新");
                } else if (teamBackpackManager != null && teamBackpackManager.isTeamBackpackInventory(event.getInventory())) {
                    teamBackpackManager.updateBackpackFromInventory(player, event.getInventory());
                    teamBackpackManager.onPlayerCloseBackpack(player); // 通知团队背包管理器玩家已关闭背包
                    getLogger().fine("玩家 " + player.getName() + " 的团队背包已更新");
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "更新背包数据时出错", e);
            }
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            try {
                Player player = (Player) event.getWhoClicked();
                Inventory inventory = event.getInventory();
                
                // 检查是否是我们的背包界面
                if (backpackManager.isCloudBackpackInventory(inventory)) {
                    int slot = event.getRawSlot();
                    PlayerBackpack backpack = backpackManager.getBackpack(player);
                    
                    // 检查是否点击了控制按钮区域（45-53槽位）
                    if (slot >= 45 && slot <= 53) {
                        event.setCancelled(true); // 取消控制按钮的默认行为
                        
                        // 处理控制按钮点击
                        if (backpackManager.handleControlButton(player, slot, backpack.getSize())) {
                            return;
                        }
                    }
                    
                    // 防止玩家将物品放入控制按钮槽位
                    if (event.getCursor() != null && !event.getCursor().getType().isAir()) {
                        if (event.getRawSlot() >= 45 && event.getRawSlot() <= 53) {
                            event.setCancelled(true);
                            return;
                        }
                        
                        // 防止玩家将物品放入未解锁的槽位
                        if (event.getRawSlot() < 45) { // 只检查物品区域
                            int currentPage = backpackManager.getPlayerPage(player);
                            int actualSlot = event.getRawSlot() + currentPage * 45;
                            
                            // 如果槽位超过背包大小，则取消放置
                            if (actualSlot >= backpack.getSize()) {
                                event.setCancelled(true);
                                player.sendMessage(getMessage("backpack.slot_not_unlocked"));
                                return;
                            }
                        }
                    }
                    
                    // 防止玩家拿走屏障方块
                    ItemStack clickedItem = event.getCurrentItem();
                    if (clickedItem != null && clickedItem.getType() == Material.BARRIER) {
                        event.setCancelled(true);
                        return;
                    }
                } else if (teamBackpackManager != null && teamBackpackManager.isTeamBackpackInventory(inventory)) {
                    // 处理团队背包界面点击
                    int slot = event.getRawSlot();
                    
                    String backpackId = teamBackpackManager.getPlayerCurrentBackpackId(player);
                    if (backpackId != null) {
                        TeamBackpack backpack = teamBackpackManager.getBackpack(backpackId);
                        
                        // 检查是否点击了控制按钮区域（45-53槽位）
                        if (slot >= 45 && slot <= 53) {
                            event.setCancelled(true); // 取消控制按钮的默认行为
                            
                            // 处理控制按钮点击
                            if (teamBackpackManager.handleControlButton(player, slot, backpack.getSize())) {
                                return;
                            }
                        }
                        
                        // 防止玩家将物品放入控制按钮槽位
                        if (event.getCursor() != null && !event.getCursor().getType().isAir()) {
                            if (event.getRawSlot() >= 45 && event.getRawSlot() <= 53) {
                                event.setCancelled(true);
                                return;
                            }
                            
                            // 防止玩家将物品放入未解锁的槽位
                            if (event.getRawSlot() < 45) { // 只检查物品区域
                                int currentPage = teamBackpackManager.getPlayerPage(player);
                                int actualSlot = event.getRawSlot() + currentPage * 45;
                                
                                // 如果槽位超过背包大小，则取消放置
                                if (actualSlot >= backpack.getSize()) {
                                    event.setCancelled(true);
                                    player.sendMessage(getMessage("backpack.slot_not_unlocked"));
                                    return;
                                }
                            }
                        }
                        
                        // 防止玩家拿走屏障方块
                        ItemStack clickedItem = event.getCurrentItem();
                        if (clickedItem != null && clickedItem.getType() == Material.BARRIER) {
                            event.setCancelled(true);
                            return;
                        }
                        
                        // 检查玩家是否有权限修改背包内容（只有所有者和管理员可以修改）
                        if (!backpack.isOwner(player.getUniqueId()) && !player.hasPermission("xibackpack.admin")) {
                            // 如果不是所有者且不是管理员，则禁止修改背包内容
                            if (event.getRawSlot() < 45) { // 只检查物品区域
                                event.setCancelled(true);
                                player.sendMessage("§c您没有权限修改此团队背包的内容！");
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "处理背包点击事件时出错", e);
                // 为安全起见，取消该事件
                event.setCancelled(true);
            }
        }
    }

    public static XiBackpack getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public BackpackManager getBackpackManager() {
        return backpackManager;
    }
    
    public TeamBackpackManager getTeamBackpackManager() {
        return teamBackpackManager;
    }
    
    // 消息配置相关方法
    private void loadMessagesConfig() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        
        try {
            messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
            
            // 加载默认消息配置
            InputStream defaultMessagesStream = getResource("messages.yml");
            if (defaultMessagesStream != null) {
                YamlConfiguration defaultMessagesConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultMessagesStream));
                messagesConfig.setDefaults(defaultMessagesConfig);
            }
            getLogger().info("消息配置加载完成");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "无法加载消息配置文件", e);
        }
    }
    
    public String getMessage(String path) {
        // 使用配置中的语言设置
        String message = messagesConfig.getString(language + "." + path, "§c消息未找到: " + path);
        // 将 & 符号替换为 § 符号以支持颜色代码
        return message.replace('&', '§');
    }
    
    public String getMessage(String path, String defaultValue) {
        // 使用配置中的语言设置，提供默认值
        String message = messagesConfig.getString(language + "." + path, defaultValue);
        // 将 & 符号替换为 § 符号以支持颜色代码
        return message.replace('&', '§');
    }
    
    public String getMessage(String path, String placeholder1, String value1) {
        return getMessage(path, "§c消息未找到: " + path)
                .replace("{" + placeholder1 + "}", value1);
    }
    
    public String getMessage(String path, String placeholder1, String value1, 
                           String placeholder2, String value2) {
        return getMessage(path, "§c消息未找到: " + path)
                .replace("{" + placeholder1 + "}", value1)
                .replace("{" + placeholder2 + "}", value2);
    }
    
    public String getLanguage() {
        return language;
    }
    
    /**
     * 检查并应用冷却时间
     * @param player 玩家
     * @return 是否可以通过冷却时间检查
     */
    public boolean checkAndApplyCooldown(Player player) {
        // 检查是否拥有绕过冷却时间的权限
        if (player.hasPermission("xibackpack.bypass.cooldown")) {
            return true;
        }
        
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long cooldownTime = getConfig().getLong("backpack.cooldown", 1000); // 默认1秒冷却
        
        if (cooldowns.containsKey(playerUUID)) {
            long lastOpenTime = cooldowns.get(playerUUID);
            long timePassed = currentTime - lastOpenTime;
            
            if (timePassed < cooldownTime) {
                long timeLeft = (cooldownTime - timePassed) / 1000;
                player.sendMessage("§c请等待 " + (timeLeft + 1) + " 秒后再打开背包!");
                return false;
            }
        }
        
        // 更新冷却时间
        cooldowns.put(playerUUID, currentTime);
        return true;
    }
    
    /**
     * 增加数据库操作计数
     */
    public void incrementDatabaseOperations() {
        totalDatabaseOperations++;
    }
    
    @Override
    public void onDisable() {
        // 保存所有背包数据
        if (backpackManager != null) {
            try {
                backpackManager.saveAllBackpacks();
                getLogger().info("所有个人背包数据已保存");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "保存个人背包数据时出错", e);
            }
        }

        // 关闭数据库连接
        if (databaseManager != null) {
            try {
                databaseManager.close();
                getLogger().info("数据库连接已关闭");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "关闭数据库连接时出错", e);
            }
        }
        
        // 输出性能统计
        getLogger().info("插件运行统计: 打开背包 " + totalBackpackOpens + " 次，数据库操作 " + totalDatabaseOperations + " 次");

        getLogger().info(getMessage("plugin.disabled"));
    }
}