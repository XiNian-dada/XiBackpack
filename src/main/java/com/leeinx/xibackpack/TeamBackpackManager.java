package com.leeinx.xibackpack;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Level;

public class TeamBackpackManager {
    private XiBackpack plugin;
    private Map<String, TeamBackpack> loadedBackpacks;
    private Map<UUID, Integer> playerPages;
    private Map<UUID, String> playerCurrentBackpack; // 记录玩家当前查看的团队背包ID
    // 记录正在查看特定团队背包的所有玩家
    private Map<String, Set<UUID>> backpackViewers;

    public TeamBackpackManager(XiBackpack plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        this.plugin = plugin;
        this.loadedBackpacks = new HashMap<>();
        this.playerPages = new HashMap<>();
        this.playerCurrentBackpack = new HashMap<>();
        this.backpackViewers = new HashMap<>();
    }

    /**
     * 获取团队背包
     *
     * @param backpackId 背包ID
     * @return 团队背包实例
     */
    public TeamBackpack getBackpack(String backpackId) {
        if (backpackId == null || backpackId.isEmpty()) {
            throw new IllegalArgumentException("Backpack ID cannot be null or empty");
        }

        if (!loadedBackpacks.containsKey(backpackId)) {
            // 从数据库加载背包数据
            TeamBackpack backpack = loadBackpackFromDatabase(backpackId);
            if (backpack != null) {
                loadedBackpacks.put(backpackId, backpack);
            }
            return backpack;
        }

        return loadedBackpacks.get(backpackId);
    }

    /**
     * 创建新的团队背包
     *
     * @param owner  所有者
     * @param name   背包名称
     * @return 新创建的背包ID
     */
    public String createBackpack(Player owner, String name) {
        if (owner == null) {
            throw new IllegalArgumentException("Owner cannot be null");
        }

        String backpackId = "team_" + UUID.randomUUID().toString();
        TeamBackpack backpack = new TeamBackpack(backpackId, owner.getUniqueId(), name);
        
        // 保存到数据库
        saveBackpack(backpack);
        
        // 加载到内存
        loadedBackpacks.put(backpackId, backpack);
        
        return backpackId;
    }

    /**
     * 从数据库加载背包数据
     *
     * @param backpackId 背包ID
     * @return 团队背包实例
     */
    private TeamBackpack loadBackpackFromDatabase(String backpackId) {
        // TODO: 从数据库加载团队背包数据
        // 这里暂时返回null，后续需要实现数据库操作
        return null;
    }

    /**
     * 保存背包到数据库
     *
     * @param backpack 团队背包
     */
    public void saveBackpack(TeamBackpack backpack) {
        // TODO: 保存团队背包到数据库
        // 这里暂时留空，后续需要实现数据库操作
    }

    /**
     * 为玩家打开团队背包
     *
     * @param player     玩家
     * @param backpackId 背包ID
     */
    public void openBackpack(Player player, String backpackId) {
        openBackpackPage(player, backpackId, 0); // 默认打开第一页
    }

    /**
     * 为玩家打开团队背包的指定页面
     *
     * @param player     玩家
     * @param backpackId 背包ID
     * @param page       页面编号
     */
    public void openBackpackPage(Player player, String backpackId, int page) {
        if (player == null) {
            plugin.getLogger().warning("尝试为null玩家打开团队背包");
            return;
        }

        TeamBackpack backpack = getBackpack(backpackId);
        if (backpack == null) {
            player.sendMessage("§c找不到指定的团队背包");
            return;
        }

        // 检查玩家是否有权限访问此背包
        if (!backpack.isMember(player.getUniqueId()) && !player.hasPermission("xibackpack.admin")) {
            player.sendMessage("§c您没有权限访问此团队背包");
            return;
        }

        try {
            // 保存玩家当前页面和背包ID
            playerPages.put(player.getUniqueId(), page);
            playerCurrentBackpack.put(player.getUniqueId(), backpackId);
            
            // 将玩家添加到背包观察者列表中
            backpackViewers.computeIfAbsent(backpackId, k -> new HashSet<>()).add(player.getUniqueId());

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

            // 添加屏障方块到未解锁的槽位
            addBarrierBlocks(inventory, backpack, startSlot, endSlot);

            // 添加控制按钮（下一页、上一页）
            addControlButtons(inventory, page, backpack.getSize());

            player.openInventory(inventory);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "打开团队背包页面时出错", e);
            player.sendMessage("§c打开团队背包页面时发生错误，请联系管理员");
        }
    }

    /**
     * 创建背包界面
     *
     * @param backpack 团队背包
     * @param page     页面编号
     * @return Inventory对象
     */
    private Inventory createBackpackInventory(TeamBackpack backpack, int page) {
        String backpackName = backpack.getName() != null ? backpack.getName() : "团队背包";
        return Bukkit.createInventory(null, 54, backpackName + " §7(" + getLocalizedPageText(page) + ")");
    }

    private String getLocalizedPageText(int page) {
        if ("zh".equals(plugin.getLanguage())) {
            return "第" + (page + 1) + "页";
        } else {
            return "Page " + (page + 1);
        }
    }

    /**
     * 在未解锁的槽位中添加屏障方块
     *
     * @param inventory  背包界面
     * @param backpack   团队背包
     * @param startSlot  起始槽位
     * @param endSlot    结束槽位
     */
    private void addBarrierBlocks(Inventory inventory, TeamBackpack backpack, int startSlot, int endSlot) {
        // 只在前5行（0-44槽位）中添加屏障方块
        // 默认只显示27个槽位（前3行），其余用屏障填充
        int maxAllowedSlot = Math.min(27, 45); // 默认最大27个槽位

        // 为未解锁的槽位添加屏障方块
        for (int i = 0; i < 45; i++) {
            // 检查是否是已解锁的槽位
            if (i >= maxAllowedSlot) {
                // 未解锁的槽位用屏障方块填充
                ItemStack barrier = new ItemStack(Material.BARRIER);
                ItemMeta meta = barrier.getItemMeta();
                if (meta != null) {
                    // 修复屏障方块显示名称问题
                    meta.setDisplayName(plugin.getMessage("backpack.slot_locked", "§c锁定槽位"));
                    barrier.setItemMeta(meta);
                }
                inventory.setItem(i, barrier);
            }
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
                    prevMeta.setDisplayName(plugin.getMessage("backpack.page_prev_first", "§7第一页"));
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
                // 修复下一页按钮显示，正确显示当前页和总页数
                if (page >= totalPages - 1) {
                    // 如果已经是最后一页，显示特殊文本
                    nextMeta.setDisplayName(plugin.getMessage("backpack.page_next_last", "§7最后一页"));
                } else {
                    nextMeta.setDisplayName(plugin.getMessage("backpack.page_next",
                            "page", String.valueOf(page + 2),
                            "total", String.valueOf(totalPages)));
                }
                nextButton.setItemMeta(nextMeta);
            }
            inventory.setItem(53, nextButton); // 右下角

            // 显示当前页信息
            ItemStack infoButton = new ItemStack(Material.PAPER);
            ItemMeta infoMeta = infoButton.getItemMeta();
            if (infoMeta != null) {
                infoMeta.setDisplayName(plugin.getMessage("backpack.info_title", "§e背包信息"));
                infoMeta.setLore(Arrays.asList(
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

    /**
     * 处理背包界面中的控制按钮点击
     *
     * @param player       玩家
     * @param slot         点击的槽位
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

            String backpackId = playerCurrentBackpack.get(player.getUniqueId());
            if (backpackId == null) return false;

            TeamBackpack backpack = getBackpack(backpackId);
            if (backpack == null) return false;

            // 计算总页数（基于实际背包大小）
            int totalPages = (int) Math.ceil((double) backpackSize / 45);

            // 检查是否点击了控制按钮
            if (slot == 45) { // 上一页
                if (currentPage > 0) {
                    openBackpackPage(player, backpackId, currentPage - 1);
                    return true;
                }
            } else if (slot == 53) { // 下一页
                // 仅允许翻到已解锁的页面
                // 只能访问已存在的页面（0 到 totalPages-1）
                if (currentPage < totalPages - 1) {
                    openBackpackPage(player, backpackId, currentPage + 1);
                    return true;
                }
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
     * 检查指定的Inventory是否为团队背包界面
     *
     * @param inventory 要检查的Inventory
     * @return 是否为团队背包界面
     */
    public boolean isTeamBackpackInventory(Inventory inventory) {
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
     * 从背包界面更新团队背包中的物品
     *
     * @param player     玩家
     * @param inventory  背包界面
     */
    public void updateBackpackFromInventory(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            plugin.getLogger().warning("更新团队背包时参数为空: player=" + player + ", inventory=" + inventory);
            return;
        }

        try {
            String backpackId = playerCurrentBackpack.get(player.getUniqueId());
            if (backpackId == null) return;

            TeamBackpack backpack = getBackpack(backpackId);
            if (backpack == null) return;

            Integer page = playerPages.get(player.getUniqueId());
            if (page == null) page = 0;

            // 检查玩家是否有权限修改此背包（只有所有者才能修改）
            if (!backpack.isOwner(player.getUniqueId()) && !player.hasPermission("xibackpack.admin")) {
                // 普通成员只能查看，不能修改
                return;
            }

            // 计算页面范围
            int startSlot = page * 45;
            int endSlot = startSlot + 45;

            // 不再自动扩展背包大小，只允许在已有的背包范围内更新物品
            // 确保不会超出实际背包大小
            endSlot = Math.min(endSlot, backpack.getSize());

            // 更新背包中的物品
            for (int i = 0; i < 45 && (i + startSlot) < backpack.getSize(); i++) { // 只处理前5行的物品格
                ItemStack item = inventory.getItem(i);
                // 只更新有效的槽位（非屏障方块槽位）
                if (item != null && item.getType() != Material.BARRIER) {
                    backpack.setItem(i + startSlot, item);
                } else if (item == null || item.getType().isAir()) {
                    backpack.setItem(i + startSlot, null);
                }
            }

            // 保存到数据库
            saveBackpack(backpack);
            
            // 同步更新给其他正在查看此背包的玩家
            syncBackpackToViewers(backpackId, inventory, page);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "从背包界面更新团队背包时出错", e);
        }
    }
    
    /**
     * 同步背包更新给其他正在查看此背包的玩家
     * 
     * @param backpackId 背包ID
     * @param sourceInventory 源背包界面
     * @param page 当前页面
     */
    private void syncBackpackToViewers(String backpackId, Inventory sourceInventory, int page) {
        TeamBackpack backpack = getBackpack(backpackId);
        if (backpack == null) return;
        
        Set<UUID> viewers = backpackViewers.get(backpackId);
        if (viewers == null || viewers.isEmpty()) return;
        
        // 计算页面范围
        int startSlot = page * 45;
        int endSlot = Math.min(startSlot + 45, backpack.getSize());
        
        // 为每个查看者更新界面
        Iterator<UUID> iterator = viewers.iterator();
        while (iterator.hasNext()) {
            UUID viewerId = iterator.next();
            Player viewer = Bukkit.getPlayer(viewerId);
            
            // 检查玩家是否在线并且仍在查看此背包
            if (viewer == null || !viewer.isOnline()) {
                iterator.remove(); // 移除离线玩家
                continue;
            }
            
            // 检查玩家是否仍在查看同一背包的同一页面
            String currentBackpackId = playerCurrentBackpack.get(viewerId);
            Integer currentPage = playerPages.get(viewerId);
            
            if (backpackId.equals(currentBackpackId) && page == (currentPage != null ? currentPage : 0)) {
                Inventory viewerInventory = viewer.getOpenInventory().getTopInventory();
                if (isTeamBackpackInventory(viewerInventory)) {
                    // 更新物品显示
                    for (int i = 0; i < 45 && (i + startSlot) < backpack.getSize(); i++) {
                        ItemStack item = sourceInventory.getItem(i);
                        viewerInventory.setItem(i, item);
                    }
                }
            }
        }
    }

    /**
     * 获取玩家当前查看的背包页
     *
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
     * 获取玩家当前查看的背包ID
     *
     * @param player 玩家
     * @return 背包ID
     */
    public String getPlayerCurrentBackpackId(Player player) {
        if (player == null) {
            return null;
        }

        return playerCurrentBackpack.get(player.getUniqueId());
    }
    
    /**
     * 当玩家关闭背包时，从观察者列表中移除
     * 
     * @param player 玩家
     */
    public void onPlayerCloseBackpack(Player player) {
        UUID playerId = player.getUniqueId();
        String backpackId = playerCurrentBackpack.remove(playerId);
        playerPages.remove(playerId);
        
        if (backpackId != null) {
            Set<UUID> viewers = backpackViewers.get(backpackId);
            if (viewers != null) {
                viewers.remove(playerId);
                if (viewers.isEmpty()) {
                    backpackViewers.remove(backpackId);
                }
            }
        }
    }
}