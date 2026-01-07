package com.leeinx.xibackpack;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.Collections;

public class BackpackManager {
    private XiBackpack plugin;
    private Map<UUID, PlayerBackpack> loadedBackpacks;
    // 存储玩家当前查看的背包页
    private Map<UUID, Integer> playerPages;

    /**
     * 构造函数，初始化背包管理器
     * @param plugin 插件主类实例
     * @throws IllegalArgumentException 当plugin为null时抛出
     */
    public BackpackManager(XiBackpack plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        this.plugin = plugin;
        this.loadedBackpacks = new HashMap<>();
        this.playerPages = new HashMap<>();
    }

    /**
     * 获取玩家的背包实例（同步方法）
     * 注意：此方法用于从缓存中获取背包。如果缓存中没有，它将【同步】从数据库加载。
     * 优化后的 openBackpack 会预先异步加载，所以正常游戏流程中，此方法应该很少触发阻塞加载。
     * @param player 玩家对象
     * @return 玩家的背包实例
     * @throws IllegalArgumentException 当player为null时抛出
     */
    public PlayerBackpack getBackpack(Player player) {
        if (player == null) {
            plugin.getLogger().warning("尝试获取null玩家的背包");
            return null; // 或者抛出 IllegalArgumentException
        }

        UUID playerUUID = player.getUniqueId();
        if (!loadedBackpacks.containsKey(playerUUID)) {
            // 作为兜底方案，如果缓存中没有，仍然需要同步加载。
            // 但在优化后的打开流程中，这一步应该由异步加载完成。
            plugin.getLogger().info("同步加载玩家 " + player.getName() + " 的背包 (缓存未命中)");
            PlayerBackpack backpack = loadBackpackDataSynchronously(playerUUID);
            if (backpack != null) {
                loadedBackpacks.put(playerUUID, backpack);
            }
            return backpack;
        }

        return loadedBackpacks.get(playerUUID);
    }

    /**
     * 为玩家打开背包，默认打开第一页 (已优化性能)
     * @param player 要打开背包的玩家
     */
    public void openBackpack(Player player) {
        openBackpackPage(player, 0); // 默认打开第一页
    }

    /**
     * 为玩家打开背包的指定页面 (已优化性能)
     * 如果背包未加载，先显示加载动画，后台加载完成后再打开。
     * @param player 要打开背包的玩家
     * @param page 要打开的页面索引（从0开始）
     */
    public void openBackpackPage(Player player, int page) {
        if (player == null) {
            plugin.getLogger().warning("尝试为null玩家打开背包页面");
            return;
        }
        UUID uuid = player.getUniqueId();

        // 1. 如果数据已经在缓存中，直接打开（无卡顿）
        if (loadedBackpacks.containsKey(uuid)) {
            openBackpackGuiInternal(player, loadedBackpacks.get(uuid), page);
            return;
        }

        // 2. 如果数据不在缓存中，先打开加载界面
        openLoadingGui(player);
        player.sendMessage(plugin.getMessage("backpack.loading_message", "§e正在加载您的个人背包，请稍候..."));

        // 3. 开启异步任务去加载数据
        new BukkitRunnable() {
            @Override
            public void run() {
                // [异步线程] 读取数据库（同步调用数据库管理器，但此Runnable本身是异步运行的）
                final PlayerBackpack backpack = loadBackpackDataSynchronously(uuid);

                // 回到 [主线程] 处理UI
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // 玩家可能在加载过程中下线了
                        if (!player.isOnline()) return;

                        // 验证玩家当前是否还开着Loading界面 (防止玩家关闭界面后被强行打开)
                        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof LoadingHolder)) {
                            // 如果玩家已经关掉了加载界面，或者打开了其他界面，就不再强制打开背包
                            return;
                        }

                        if (backpack != null) {
                            // 存入缓存
                            loadedBackpacks.put(uuid, backpack);
                            // 打开真正的背包
                            openBackpackGuiInternal(player, backpack, page);
                        } else {
                            player.sendMessage(plugin.getMessage("backpack.load_failed", "§c加载个人背包数据失败，请联系管理员。"));
                            player.closeInventory();
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * 内部方法：构建并打开真正的背包GUI
     * @param player 玩家对象
     * @param backpack 玩家背包实例
     * @param page 要打开的页面索引
     */
    private void openBackpackGuiInternal(Player player, PlayerBackpack backpack, int page) {
        try {
            playerPages.put(player.getUniqueId(), page);
            Inventory inventory = createBackpackInventory(backpack, page);

            // 计算页面范围
            int startSlot = page * 45; // 前5行用于物品显示（45格），最后一行用于控制按钮
            int endSlot = Math.min(startSlot + 45, backpack.getSize());

            // 将物品放入GUI，确保正确的槽位映射
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
            plugin.getLogger().log(Level.SEVERE, "打开个人背包页面时出错", e);
            player.sendMessage("§c打开个人背包页面时发生错误，请联系管理员");
        }
    }

    /**
     * 显示一个加载中动画界面
     * @param player 玩家对象
     */
    private void openLoadingGui(Player player) {
        // 创建一个小的界面，显示加载中图标
        Inventory loadingInv = Bukkit.createInventory(new LoadingHolder(), 9, plugin.getMessage("backpack.loading_gui_title", "§0正在加载数据..."));

        // 制作一个简单的加载动画项
        ItemStack item = new ItemStack(Material.CLOCK); // 或者其他你喜欢的图标，如 COMPASS, PAPER
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getMessage("backpack.loading_item_name", "§e数据加载中..."));
            meta.setLore(Collections.singletonList(plugin.getMessage("backpack.loading_item_lore", "§7请稍候，正在同步数据库...")));
            item.setItemMeta(meta);
        }
        loadingInv.setItem(4, item); // 放在中间
        player.openInventory(loadingInv);
    }

    /**
     * 在未解锁的槽位中添加屏障方块
     * @param inventory 背包界面
     * @param backpack 玩家背包
     * @param startSlot 起始槽位（当前页面的起始位置）
     * @param endSlot 结束槽位（当前页面的结束位置）
     */
    private void addBarrierBlocks(Inventory inventory, PlayerBackpack backpack, int startSlot, int endSlot) {
        // 计算当前页面中已解锁的最大槽位（相对于当前页面）
        int maxAllowedSlot = Math.min(backpack.getSize() - startSlot, 45); // 当前页面最大可用槽位数

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

    /**
     * 同步从数据库加载玩家背包数据
     * 注意：此方法是【同步阻塞】的，只应在异步线程中调用，或作为极少数情况下的兜底方案。
     * @param playerUUID 玩家唯一标识符
     * @return 玩家背包实例
     */
    private PlayerBackpack loadBackpackDataSynchronously(UUID playerUUID) {
        if (playerUUID == null) {
            plugin.getLogger().warning("尝试加载null UUID的背包数据");
            return new PlayerBackpack(UUID.randomUUID(), plugin.getConfig().getInt("backpack.size", 27));
        }

        try {
            String backpackData = plugin.getDatabaseManager().loadPlayerBackpack(playerUUID);
            if (backpackData != null) {
                try {
                    return PlayerBackpack.deserialize(backpackData, playerUUID);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "反序列化个人背包数据时出错: " + playerUUID, e);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "从数据库加载个人背包数据时出错: " + playerUUID, e);
        }
        // 默认背包大小从配置文件读取
        int defaultSize = plugin.getConfig().getInt("backpack.size", 27);
        return new PlayerBackpack(playerUUID, defaultSize);
    }

    /**
     * 创建背包界面
     * @param backpack 玩家背包
     * @param page 页面索引
     * @return 创建的Inventory对象
     */
    private Inventory createBackpackInventory(PlayerBackpack backpack, int page) {
        return Bukkit.createInventory(new BackpackPageHolder(page), 54,
                plugin.getMessage("backpack.name") + " §7(第" + (page + 1) + "页)");
    }

    /**
     * 获取本地化页面文本
     * @param page 页面索引
     * @return 本地化后的页面文本
     */
    private String getLocalizedPageText(int page) {
        if ("zh".equals(plugin.getLanguage())) {
            return "第" + (page + 1) + "页";
        } else {
            return "Page " + (page + 1);
        }
    }

    /**
     * 向背包界面添加控制按钮（上一页、下一页等）
     * @param inventory 背包界面
     * @param page 当前页面索引
     * @param backpackSize 背包总大小
     */
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

            // 填充按钮
            ItemStack fillButton = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta fillMeta = fillButton.getItemMeta();
            if(fillMeta != null){
                fillMeta.setDisplayName(plugin.getMessage("backpack.fill_button", " "));
                fillMeta.addEnchant(Enchantment.LUCK, 1, false);
                fillButton.setItemMeta(fillMeta);
            }
            for(int index = 46; index < 53; index++){
                inventory.setItem(index, fillButton);
            }

            // 显示当前页信息
            ItemStack infoButton = new ItemStack(Material.PAPER);
            ItemMeta infoMeta = infoButton.getItemMeta();
            if (infoMeta != null) {
                infoMeta.setDisplayName(plugin.getMessage("backpack.info_title", "§e背包信息"));
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

    /**
     * 保存玩家背包数据 (异步 + 线程安全)
     * @param backpack 要保存的背包实例
     */
    public void saveBackpack(PlayerBackpack backpack) {
        if (backpack == null) {
            plugin.getLogger().warning("尝试保存null背包");
            return;
        }

        // 异步保存背包数据
        // 主线程快照 -> 异步线程 IO
        final String serializedData = backpack.serialize();
        final UUID uuid = backpack.getPlayerUUID();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (!plugin.isEnabled()) return;

                    boolean success = plugin.getDatabaseManager().savePlayerBackpack(uuid, serializedData);
                    plugin.incrementDatabaseOperations();
                    if (!success) {
                        plugin.getLogger().warning("保存玩家 " + uuid + " 的背包数据失败");
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "异步保存个人背包数据时出错", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * 从背包界面更新玩家背包数据
     * @param player 玩家对象
     * @param inventory 背包界面
     */
    public void updateBackpackFromInventory(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            plugin.getLogger().warning("更新背包时参数为空: player=" + player + ", inventory=" + inventory);
            return;
        }

        try {
            PlayerBackpack backpack = getBackpack(player); // 此时背包应已在缓存中
            if (backpack == null) {
                plugin.getLogger().warning("更新个人背包时未找到背包数据: " + player.getName());
                return;
            }

            int page;
            if (inventory.getHolder() instanceof BackpackPageHolder) {
                page = ((BackpackPageHolder) inventory.getHolder()).getPage();
            } else {
                page = playerPages.getOrDefault(player.getUniqueId(), 0); // 兜底
            }

            // 计算页面范围
            int startSlot = page * 45;
            // 确保不会超出实际背包大小
            int endSlot = Math.min(startSlot + 45, backpack.getSize());

            plugin.getLogger().info("更新个人背包 " + player.getName() + "，页面 " + page + "，槽位范围 " + startSlot + "-" + endSlot);

            // 更新背包中的物品
            for (int i = 0; i < 45 && (i + startSlot) < backpack.getSize(); i++) { // 只处理前5行的物品格
                ItemStack item = inventory.getItem(i);
                int actualSlot = i + startSlot;
                // 只更新有效的槽位（非屏障方块槽位）
                if (item != null && item.getType() != Material.BARRIER) {
                    backpack.setItem(actualSlot, item);
                } else if (item == null || item.getType().isAir()) {
                    backpack.setItem(actualSlot, null);
                }
            }

            // 保存到数据库
            saveBackpack(backpack); // 调用异步保存
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "从背包界面更新个人背包时出错", e);
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

            // 计算总页数（基于实际背包大小）
            int totalPages = (int) Math.ceil((double) backpackSize / 45);

            plugin.getLogger().info("处理个人背包控制按钮，当前页面: " + currentPage + "，总页面: " + totalPages + "，点击槽位: " + slot);

            // 检查是否点击了控制按钮
            if (slot == 45) { // 上一页
                if (currentPage > 0) {
                    openBackpackPage(player, currentPage - 1);
                    return true;
                }
            } else if (slot == 53) { // 下一页
                // 仅允许翻到已解锁的页面
                // 只能访问已存在的页面（0 到 totalPages-1）
                if (currentPage < totalPages - 1) {
                    openBackpackPage(player, currentPage + 1);
                    return true;
                }
            } else if (slot == 49) { // 信息按钮
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "处理控制按钮点击时出错", e);
        }

        return false;
    }


    /**
     * 检查指定的Inventory是否为云背包界面
     * 修复：仅通过 InventoryHolder 进行严格判断，防止与团队背包混淆
     * @param inventory 要检查的Inventory
     * @return 是否为云背包界面
     */
    public boolean isCloudBackpackInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }

        // 【关键修复】只认身份证 (Holder)，不看长相。
        // 只有持有 BackpackPageHolder 的才是个人背包。
        // 任何其他界面（包括团队背包）都会返回 false。
        return inventory.getHolder() instanceof BackpackPageHolder;
    }
    
    /**
     * 获取玩家当前查看的背包页
     * @param player 玩家
     * @return 当前页码
     */
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
     * 保存所有已加载的背包 (关服时同步保存)
     */
    public void saveAllBackpacks() {
        if (loadedBackpacks == null || loadedBackpacks.isEmpty()) {
            plugin.getLogger().info("关服保存: 没有需要保存的个人背包数据。");
            return;
        }

        plugin.getLogger().info("关服保存: 正在同步保存所有玩家背包数据...");
        int count = 0;

        for (PlayerBackpack backpack : loadedBackpacks.values()) {
            try {
                // 直接在主线程执行序列化和保存 (同步)
                String serializedData = backpack.serialize();
                plugin.getDatabaseManager().savePlayerBackpack(backpack.getPlayerUUID(), serializedData);
                count++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "关服保存: 玩家 " + backpack.getPlayerUUID() + " 背包时出错", e);
            }
        }
        plugin.getLogger().info("关服保存: 已同步保存 " + count + " 个个人背包数据");
    }
    // 新增：用来在 Inventory 中携带页码信息
    public static class BackpackPageHolder implements org.bukkit.inventory.InventoryHolder {
        private final int page;
        public BackpackPageHolder(int page) { this.page = page; }
        @Override public org.bukkit.inventory.Inventory getInventory() { return null; }
        public int getPage() { return page; }
    }
}
