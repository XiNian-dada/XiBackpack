package com.leeinx.xibackpack;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class BackpackManager {
    private XiBackpack plugin;
    private Map<UUID, PlayerBackpack> loadedBackpacks;
    // 存储玩家当前查看的背包页
    private Map<UUID, Integer> playerPages;

    public BackpackManager(XiBackpack plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        
        this.plugin = plugin;
        this.loadedBackpacks = new HashMap<>();
        this.playerPages = new HashMap<>();
    }

    public PlayerBackpack getBackpack(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        
        UUID playerUUID = player.getUniqueId();
        if (!loadedBackpacks.containsKey(playerUUID)) {
            // 从数据库加载背包数据
            PlayerBackpack backpack = loadBackpackFromDatabase(playerUUID);
            loadedBackpacks.put(playerUUID, backpack);
        }
        
        return loadedBackpacks.get(playerUUID);
    }
    
    public void openBackpack(Player player) {
        openBackpackPage(player, 0);
    }
    
    public void openBackpackPage(Player player, int page) {
        if (player == null) {
            plugin.getLogger().warning("尝试为null玩家打开背包");
            return;
        }
        
        try {
            PlayerBackpack backpack = getBackpack(player);
            
            // 保存玩家当前页面
            playerPages.put(player.getUniqueId(), page);
            
            // 创建并打开背包GUI
            Inventory inventory = createBackpackInventory(backpack, page);
            
            // 计算页面范围
            int startSlot = page * 45; // 前5行用于物品显示（45格），最后一行用于控制按钮
            int endSlot = Math.min(startSlot + 45, backpack.getSize());
            
            // 将物品放入GUI
            for (int i = startSlot; i < endSlot; i++) {
                ItemStack item = backpack.getItem(i);
                if (item != null) {
                    inventory.setItem(i - startSlot, item);
                }
            }
            
            // 添加控制按钮（下一页、上一页）
            addControlButtons(inventory, page, backpack.getSize());
            
            player.openInventory(inventory);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "打开背包页面时出错", e);
            player.sendMessage("§c打开背包页面时发生错误，请联系管理员");
        }
    }

    private PlayerBackpack loadBackpackFromDatabase(UUID playerUUID) {
        if (playerUUID == null) {
            plugin.getLogger().warning("尝试加载null UUID的背包数据");
            // 返回默认背包
            return new PlayerBackpack(UUID.randomUUID(), plugin.getConfig().getInt("backpack.size", 27));
        }
        
        try {
            // 异步加载背包数据
            return loadBackpackFromDatabaseAsync(playerUUID);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "加载背包数据时出错", e);
            // 返回默认背包
            return new PlayerBackpack(playerUUID, plugin.getConfig().getInt("backpack.size", 27));
        }
    }
    
    private PlayerBackpack loadBackpackFromDatabaseAsync(UUID playerUUID) {
        // 从数据库加载背包数据（同步方式，简单实现）
        String backpackData = plugin.getDatabaseManager().loadPlayerBackpack(playerUUID);
        if (backpackData != null) {
            try {
                return PlayerBackpack.deserialize(backpackData, playerUUID);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "反序列化背包数据时出错", e);
            }
        }
        // 默认背包大小从配置文件读取
        int defaultSize = plugin.getConfig().getInt("backpack.size", 27);
        return new PlayerBackpack(playerUUID, defaultSize);
    }

    private Inventory createBackpackInventory(PlayerBackpack backpack, int page) {
        // 从配置文件读取背包名称
        String backpackName = plugin.getConfig().getString("backpack.name", plugin.getMessage("backpack.name"));
        // 始终创建54格的界面（6行），其中最后一行用于控制按钮
        return Bukkit.createInventory(null, 54, backpackName + " §7(" + getLocalizedPageText(page) + ")");
    }
    
    private String getLocalizedPageText(int page) {
        if ("zh".equals(plugin.getLanguage())) {
            return "第" + (page + 1) + "页";
        } else {
            return "Page " + (page + 1);
        }
    }
    
    private void addControlButtons(Inventory inventory, int page, int backpackSize) {
        if (inventory == null) {
            plugin.getLogger().warning("尝试向null背包添加控制按钮");
            return;
        }
        
        try {
            int totalPages = (int) Math.ceil((double) backpackSize / 45);
            
            // 上一页按钮（左下角）
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            if (prevMeta != null) {
                if (page <= 0) {
                    prevMeta.setDisplayName(plugin.getMessage("backpack.page_prev_first"));
                } else {
                    prevMeta.setDisplayName(plugin.getMessage("backpack.page_prev", 
                        "page", String.valueOf(page), 
                        "total", String.valueOf(totalPages)));
                }
                prevButton.setItemMeta(prevMeta);
            }
            inventory.setItem(45, prevButton); // 左下角
            
            // 下一页按钮（右下角）
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName(plugin.getMessage("backpack.page_next_unlimited", 
                    "page", String.valueOf(page + 2), 
                    "total", String.valueOf(totalPages)));
                nextButton.setItemMeta(nextMeta);
            }
            inventory.setItem(53, nextButton); // 右下角
            
            // 显示当前页信息
            ItemStack infoButton = new ItemStack(Material.PAPER);
            ItemMeta infoMeta = infoButton.getItemMeta();
            if (infoMeta != null) {
                infoMeta.setDisplayName(plugin.getMessage("backpack.info_title"));
                infoMeta.setLore(java.util.Arrays.asList(
                    plugin.getMessage("backpack.info_capacity", "size", String.valueOf(backpackSize)),
                    getLocalizedCurrentPageText(page + 1)
                ));
                infoButton.setItemMeta(infoMeta);
            }
            inventory.setItem(49, infoButton); // 中下
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "添加控制按钮时出错", e);
        }
    }
    
    private String getLocalizedCurrentPageText(int page) {
        if ("zh".equals(plugin.getLanguage())) {
            return plugin.getMessage("backpack.info_page", "page", String.valueOf(page));
        } else {
            return "§7Current page: " + page;
        }
    }

    public void saveBackpack(PlayerBackpack backpack) {
        if (backpack == null) {
            plugin.getLogger().warning("尝试保存null背包");
            return;
        }
        
        // 异步保存背包数据
        saveBackpackAsync(backpack);
    }
    
    private void saveBackpackAsync(PlayerBackpack backpack) {
        try {
            // 使用Bukkit调度器异步保存
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        // 将背包数据保存到数据库
                        String serializedData = backpack.serialize();
                        boolean success = plugin.getDatabaseManager().savePlayerBackpack(backpack.getPlayerUUID(), serializedData);
                        
                        // 增加数据库操作计数
                        plugin.incrementDatabaseOperations();
                        
                        if (!success) {
                            plugin.getLogger().warning("保存玩家 " + backpack.getPlayerUUID() + " 的背包数据失败");
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "异步保存背包数据时出错", e);
                    }
                }
            }.runTaskAsynchronously(plugin);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "调度异步保存任务时出错", e);
        }
    }

    public void updateBackpackFromInventory(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            plugin.getLogger().warning("更新背包时参数为空: player=" + player + ", inventory=" + inventory);
            return;
        }
        
        try {
            PlayerBackpack backpack = getBackpack(player);
            Integer page = playerPages.get(player.getUniqueId());
            if (page == null) page = 0;
            
            // 计算页面范围
            int startSlot = page * 45;
            int endSlot = startSlot + 45;
            
            // 确保背包足够大以容纳当前页面
            if (backpack.getSize() < endSlot) {
                backpack.setSize(endSlot);
            }
            
            // 更新背包中的物品
            for (int i = 0; i < 45; i++) { // 只处理前5行的物品格
                ItemStack item = inventory.getItem(i);
                backpack.setItem(i + startSlot, item);
            }
            
            // 保存到数据库
            saveBackpack(backpack);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "从背包界面更新背包时出错", e);
        }
    }
    
    /**
     * 处理背包界面中的控制按钮点击
     * @param player 玩家
     * @param slot 点击的槽位
     * @param backpackSize 背包总大小
     * @return 是否处理了控制按钮点击
     */
    public boolean handleControlButton(Player player, int slot, int backpackSize) {
        if (player == null) {
            plugin.getLogger().warning("处理控制按钮时玩家为空");
            return false;
        }
        
        try {
            Integer currentPage = playerPages.get(player.getUniqueId());
            if (currentPage == null) currentPage = 0;
            
            // 检查是否点击了控制按钮
            if (slot == 45) { // 上一页
                if (currentPage > 0) {
                    openBackpackPage(player, currentPage - 1);
                    return true;
                }
            } else if (slot == 53) { // 下一页
                // 允许翻到新的空白页，即使超过了当前背包大小
                openBackpackPage(player, currentPage + 1);
                return true;
            } else if (slot == 49) { // 信息按钮
                // 信息按钮不需要特殊处理，只是显示信息
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "处理控制按钮点击时出错", e);
        }
        
        return false;
    }
    
    /**
     * 检查指定的Inventory是否为云背包界面
     * 使用更兼容的方式检查，避免直接调用getTitle()
     * @param inventory 要检查的Inventory
     * @return 是否为云背包界面
     */
    public boolean isCloudBackpackInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        
        try {
            // 检查inventory是否为54格大小（云背包的标准大小）
            if (inventory.getSize() != 54) {
                return false;
            }
            
            // 检查是否有我们特定的控制按钮
            ItemStack infoButton = inventory.getItem(49); // 信息按钮位置
            if (infoButton != null && infoButton.getType() == Material.PAPER) {
                ItemMeta meta = infoButton.getItemMeta();
                if (meta != null) {
                    String displayName = meta.getDisplayName();
                    // 检查是否包含背包信息标题
                    if (displayName != null && displayName.contains(plugin.getMessage("backpack.info_title").substring(2))) {
                        return true;
                    }
                }
            }
            
            // 检查箭头按钮
            ItemStack prevButton = inventory.getItem(45);
            ItemStack nextButton = inventory.getItem(53);
            if (prevButton != null && nextButton != null) {
                if (prevButton.getType() == Material.ARROW && nextButton.getType() == Material.ARROW) {
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "检查背包界面时出错", e);
        }
        
        return false;
    }
    
    /**
     * 获取玩家当前查看的背包页
     * @param player 玩家
     * @return 当前页码
     */
    public int getPlayerPage(Player player) {
        if (player == null) {
            return 0;
        }
        
        return playerPages.getOrDefault(player.getUniqueId(), 0);
    }
    
    /**
     * 保存所有已加载的背包
     */
    public void saveAllBackpacks() {
        try {
            // 保存所有已加载的背包数据
            for (PlayerBackpack backpack : loadedBackpacks.values()) {
                saveBackpack(backpack);
            }
            plugin.getLogger().info("已调度保存所有背包数据");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "保存所有背包数据时出错", e);
        }
    }
}