package com.leeinx.xibackpack;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.plugin.RegisteredServiceProvider;
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

/*
 * 团队背包功能完整测试流程
 * =========================
 * 
 * 测试前准备:
 * 1. 确保服务器已安装Vault插件并配置好经济系统
 * 2. 确保数据库连接正常（MySQL/PostgreSQL）
 * 3. 准备至少2个测试账号（玩家A和玩家B）
 * 
 * 测试步骤:
 * 
 * 第一部分：个人背包功能测试
 * 1. 玩家A执行命令 /backpack
 *    - 验证：打开个人背包界面，初始27格
 * 2. 在背包中放入各种物品（普通物品、附魔物品、命名物品）
 * 3. 关闭背包界面，重新打开
 *    - 验证：物品正确保存和加载
 * 4. 玩家A执行命令 /xibackpack upgrade
 *    - 验证：背包扩容，扣除相应金币
 * 5. 重复步骤2-3，验证扩容后功能正常
 * 
 * 第二部分：团队背包数据库存储测试
 * 1. 玩家A执行命令 /xibackpack team create "测试团队背包"
 *    - 验证：成功创建团队背包，扣除创建费用
 *    - 验证：数据库team_backpacks表新增记录
 *    - 验证：数据库team_backpack_members表新增记录，玩家A为OWNER角色
 * 2. 重启服务器
 * 3. 玩家A重新登录，执行命令 /xibackpack team gui
 *    - 验证：在管理界面能看到刚创建的团队背包
 * 
 * 第三部分：团队背包GUI管理界面测试
 * 1. 玩家A执行命令 /xibackpack team gui
 *    - 验证：打开GUI界面，四周为黑色玻璃板，底部中央为"创建团队背包"按钮
 * 2. 点击"创建团队背包"按钮
 *    - 验证：界面关闭，聊天框提示输入背包名称
 * 3. 在聊天框输入"第二个测试背包"
 *    - 验证：成功创建背包，在GUI界面中显示
 * 4. 点击新创建的背包图标（左键）
 *    - 验证：打开团队背包界面
 * 5. 在团队背包中放入物品，关闭界面后重新打开
 *    - 验证：物品正确保存和加载
 * 
 * 第四部分：团队背包权限测试
 * 1. 玩家A打开已有团队背包（自己创建的）
 *    - 验证：背包图标显示附魔效果
 *    - 验证：可以放入和取出物品
 * 2. 玩家B执行命令 /xibackpack team gui
 *    - 验证：看不到玩家A创建的背包（因为还未授权）
 * 3. 玩家A执行命令 /xibackpack team addmember <背包ID> 玩家B
 *    - 验证：玩家B成功添加到团队背包成员
 * 4. 玩家B执行命令 /xibackpack team gui
 *    - 验证：可以看到共享的团队背包
 *    - 验证：背包图标无附魔效果（因为不是所有者）
 * 5. 玩家B打开团队背包
 *    - 验证：可以查看物品但不能修改（根据权限设定）
 * 6. 玩家A执行命令 /xibackpack team removemember <背包ID> 玩家B
 *    - 验证：玩家B成功从团队背包成员中移除
 * 
 * 第五部分：数据持久化测试
 * 1. 玩家A在个人背包和团队背包中都放入不同物品
 * 2. 玩家A断开连接
 *    - 验证：触发PlayerQuitEvent，自动保存背包数据
 * 3. 重启服务器
 * 4. 玩家A重新登录，打开个人背包和团队背包
 *    - 验证：所有物品正确加载
 * 
 * 第六部分：分页功能测试
 * 1. 玩家A将团队背包扩容到较大尺寸（如100格）
 * 2. 打开团队背包，检查分页显示
 *    - 验证：每页正确显示45个物品槽位
 *    - 验证：超出背包大小的槽位用屏障方块填充
 * 3. 使用下一页/上一页按钮切换页面
 *    - 验证：页面切换正常，物品显示正确
 * 
 * 第七部分：异常情况测试
 * 1. 在聊天输入背包名称时，输入特殊字符或超长名称
 *    - 验证：系统正确处理，不会导致数据库错误
 * 2. 并发访问同一个团队背包
 *    - 验证：多个玩家同时访问背包时数据一致性和同步性
 * 3. 数据库连接中断时的操作
 *    - 验证：有适当的错误提示，不会导致服务器崩溃
 * 
 * 预期结果:
 * - 个人背包和团队背包数据完全隔离存储
 * - 团队背包可通过GUI界面方便管理
 * - 所有背包数据正确持久化，重启后不丢失
 * - 权限控制按预期工作（所有者可修改，成员只读）
 * - 分页显示正确，屏障方块正确填充
 * - 异常情况得到妥善处理
 */

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
    
    // 添加用于跟踪玩家创建团队背包状态的Map
    private Map<UUID, Boolean> playerCreatingTeamBackpack = new HashMap<>();

    private net.milkbowl.vault.economy.Economy economy = null;
    
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

        if(!loadDependencies()){
            setEnabled(false);
            return;
        }

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
            
            // 移除创建团队背包状态
            playerCreatingTeamBackpack.remove(player.getUniqueId());
            
            getLogger().fine("玩家 " + player.getName() + " 的背包数据已保存");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "保存玩家背包数据时出错", e);
        }
    }
    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        // 玩家进服时，异步拉取一下他的团队背包数量
        if (teamBackpackManager != null) {
            teamBackpackManager.updateTeamCountCache(event.getPlayer().getUniqueId());
        }
    }
    
    @EventHandler
    public void onPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String message = event.getMessage();
        
        // 检查玩家是否正在创建团队背包
        if (Boolean.TRUE.equals(playerCreatingTeamBackpack.get(playerUUID))) {
            event.setCancelled(true); // 取消聊天消息
            
            // 创建团队背包
            String backpackId = teamBackpackManager.createBackpack(player, message);
            
            if (backpackId != null) {
                player.sendMessage("§a成功创建团队背包: " + message);
                player.sendMessage("§a背包ID: " + backpackId);
            } else {
                player.sendMessage("§c创建团队背包失败，请重试");
            }
            
            // 移除创建状态
            playerCreatingTeamBackpack.remove(playerUUID);
        }
    }
    
    /**
     * 设置玩家创建团队背包的状态
     *
     * @param playerUUID 玩家UUID
     * @param creating 是否正在创建
     */
    public void setPlayerCreatingTeamBackpack(UUID playerUUID, boolean creating) {
        if (creating) {
            playerCreatingTeamBackpack.put(playerUUID, true);
        } else {
            playerCreatingTeamBackpack.remove(playerUUID);
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // 当玩家关闭背包时更新背包数据
        if (event.getPlayer() instanceof Player) {
            try {
                Player player = (Player) event.getPlayer();
                // 检查是否是我们插件创建的背包界面
                // 注意：必须先检查团队背包，因为团队背包也可能被误识别为个人背包
                if (teamBackpackManager != null && teamBackpackManager.isTeamBackpackInventory(event.getInventory())) {
                    teamBackpackManager.updateBackpackFromInventory(player, event.getInventory());
                    teamBackpackManager.onPlayerCloseBackpack(player); // 通知团队背包管理器玩家已关闭背包
                    getLogger().fine("玩家 " + player.getName() + " 的团队背包已更新");
                } else if (backpackManager.isCloudBackpackInventory(event.getInventory())) {
                    backpackManager.updateBackpackFromInventory(player, event.getInventory());
                    getLogger().fine("玩家 " + player.getName() + " 的个人背包已更新");
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "更新背包数据时出错", e);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            // 【新增】阻止在加载界面进行任何操作
            if (event.getInventory().getHolder() instanceof LoadingHolder) {
                event.setCancelled(true);
                return;
            }

            try {
                Player player = (Player) event.getWhoClicked();
                Inventory inventory = event.getInventory();
                // Inventory clickedInventory = event.getClickedInventory(); // clickedInventory 在某些逻辑中可能不需要，这里暂时保留

                // 检查是否是我们的背包界面
                if (backpackManager.isCloudBackpackInventory(inventory)) {
                    int slot = event.getRawSlot();
                    PlayerBackpack backpack = backpackManager.getBackpack(player); // 这里会从缓存获取，不会阻塞

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

                    if (inventory.getHolder() instanceof TeamBackpackManager.TeamBackpackPageHolder) {
                        TeamBackpackManager.TeamBackpackPageHolder holder =
                                (TeamBackpackManager.TeamBackpackPageHolder) inventory.getHolder();
                        String backpackId = holder.getBackpackId();
                        int currentPage = holder.getPage();

                        TeamBackpack backpack = teamBackpackManager.getBackpack(backpackId); // 这里会从缓存获取，不会阻塞
                        if (backpack == null) {
                            event.setCancelled(true);
                            return;
                        }

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
                                int actualSlot = event.getRawSlot() + currentPage * 45; // 使用从Holder获取的currentPage

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
                } else if (inventory.getHolder() instanceof TeamBackpackManagementHolder) {
                    // 处理团队背包管理界面点击
                    event.setCancelled(true);
                    int slot = event.getRawSlot();
                    teamBackpackManager.handleManagementGUIClick(player, slot, event.getClick());
                    return;
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "处理背包点击事件时出错", e);
                // 为安全起见，取消该事件
                event.setCancelled(true);
            }
        }
    }

    /**
     * 获取插件实例
     * @return 插件实例
     */
    public static XiBackpack getInstance() {
        return instance;
    }

    /**
     * 获取数据库管理器实例
     * @return 数据库管理器实例
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    /**
     * 获取个人背包管理器实例
     * @return 个人背包管理器实例
     */
    public BackpackManager getBackpackManager() {
        return backpackManager;
    }
    
    /**
     * 获取团队背包管理器实例
     * @return 团队背包管理器实例
     */
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
    //检测&加载相关附属前置的方法
    private boolean loadDependencies(){

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
            try{
                boolean success = new XiBackpackExpansion(this).register();
                if (success) {
                    getLogger().info("已经成功加载 Papi 前置！");
                } else {
                    getLogger().warning("Papi 前置加载失败！请检查版本！");
                }
            } catch (Throwable e){
                getLogger().log(Level.SEVERE, "发生未知错误！无法加载 Papi 前置", e);
            }
        } else {
            getLogger().warning("未找到 Papi 前置！将跳过注册！");
        }

        if(Bukkit.getPluginManager().getPlugin("Vault") != null){
            try{
                boolean success = setupEconomy();
                if (success) {
                    getLogger().info("已经成功加载 Vault 前置！");
                }else {
                    getLogger().severe("Vault 前置加载失败！请检查版本！插件将停用");
                    setEnabled(false);
                    return false;
                }
            } catch (Throwable e){
                getLogger().log(Level.SEVERE, "发生未知错误！无法加载 Vault 前置", e);
                setEnabled(false);
                return false;
            }
        }else{
            getLogger().severe("未找到 Vault 前置！插件将停用");
            setEnabled(false);
            return false;
        }

        if(Bukkit.getPluginManager().getPlugin("NBTAPI") !=  null){
            try{
                de.tr7zw.nbtapi.NBT.class.getName();
                getLogger().info("已经成功加载 NBTAPI 前置！");
            } catch (Throwable e){
                getLogger().log(Level.SEVERE, "发生未知错误！无法加载 NBTAPI 前置", e);
                setEnabled(false);
                return false;
            }
        } else {
            getLogger().severe("未找到 NBTAPI 前置！插件将停用");
            setEnabled(false);
            return false;
        }

        return true;
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

    private boolean setupEconomy() {
        try {
            RegisteredServiceProvider<Economy> rsp =
                    getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (rsp == null) {
                return false;
            }
            economy = rsp.getProvider();
            return economy != null;
        } catch (Throwable e) {
            return false;
        }
    }
    public Economy getEconomy() {
        return economy;
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
        // 2. 新增：保存团队背包 (必须添加)
        if (teamBackpackManager != null) {
            try {
                teamBackpackManager.saveAllBackpacks();
                getLogger().info("所有团队背包数据已保存");
            }catch (Exception e){
                getLogger().log(Level.SEVERE, "保存团队背包数据时出错", e);
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