package com.leeinx.xibackpack.handler;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import com.leeinx.xibackpack.main.XiBackpack;
import com.leeinx.xibackpack.backpack.PlayerBackpack;
import com.leeinx.xibackpack.holder.LoadingHolder;

public class BackpackManager extends BaseBackpackManager {
    private Map<UUID, PlayerBackpack> loadedBackpacks;

    /**
     * 构造函数，初始化背包管理器
     * @param plugin 插件主类实例
     * @throws IllegalArgumentException 当plugin为null时抛出
     */
    public BackpackManager(XiBackpack plugin) {
        super(plugin);
        this.loadedBackpacks = new ConcurrentHashMap<>();
    }

    /**
     * 获取玩家的背包实例
     * 注意：此方法首先从缓存中获取背包。如果缓存中没有，会同步加载背包数据。
     * 正常游戏流程中，背包应该已经通过异步加载预先放入缓存。
     * @param player 玩家对象
     * @return 玩家的背包实例，永远不会返回null
     */
    public PlayerBackpack getBackpack(Player player) {
        if (player == null) {
            com.leeinx.xibackpack.util.LogManager.warning("尝试获取null玩家的背包");
            return new PlayerBackpack(UUID.randomUUID(), plugin.getConfig().getInt("backpack.size", 27));
        }

        UUID playerUUID = player.getUniqueId();
        PlayerBackpack backpack = loadedBackpacks.get(playerUUID);
        
        // 如果缓存中没有背包，同步加载
        if (backpack == null) {
            backpack = loadBackpackData(playerUUID);
            loadedBackpacks.put(playerUUID, backpack);
        }
        
        return backpack;
    }
    
    /**
     * 异步加载玩家背包数据并放入缓存
     * @param playerUUID 玩家唯一标识符
     * @return 玩家背包实例的CompletableFuture
     */
    public CompletableFuture<PlayerBackpack> loadAndCacheBackpackAsync(UUID playerUUID) {
        return loadBackpackDataAsync(playerUUID)
            .thenApply(backpack -> {
                if (backpack != null) {
                    loadedBackpacks.put(playerUUID, backpack);
                }
                return backpack;
            });
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
            com.leeinx.xibackpack.util.LogManager.warning("尝试为null玩家打开背包页面");
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

        // 3. 使用异步加载方法加载数据
        loadAndCacheBackpackAsync(uuid)
            .thenAcceptAsync(backpack -> {
                // 玩家可能在加载过程中下线了
                if (!player.isOnline()) return;

                // 在主线程上执行界面操作
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // 验证玩家当前是否还开着Loading界面 (防止玩家关闭界面后被强行打开)
                    if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof LoadingHolder)) {
                        // 如果玩家已经关掉了加载界面，或者打开了其他界面，就不再强制打开背包
                        return;
                    }

                    if (backpack != null) {
                        // 打开真正的背包
                        openBackpackGuiInternal(player, backpack, page);
                    } else {
                        player.sendMessage(plugin.getMessage("backpack.load_failed", "§c加载个人背包数据失败，请联系管理员。"));
                        player.closeInventory();
                    }
                });
            })
            .exceptionally(ex -> {
                com.leeinx.xibackpack.util.ExceptionHandler.handleAsyncException("异步加载个人背包", ex);
                return null;
            });
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
            com.leeinx.xibackpack.util.ExceptionHandler.handleBackpackException(player, "打开个人背包页面", e);
        }
    }

    /**
     * 在未解锁的槽位中添加屏障方块
     * @param inventory 背包界面
     * @param backpack 玩家背包
     * @param startSlot 起始槽位（当前页面的起始位置）
     * @param endSlot 结束槽位（当前页面的结束位置）
     */
    private void addBarrierBlocks(Inventory inventory, PlayerBackpack backpack, int startSlot, int endSlot) {
        // 使用基类方法
        super.addBarrierBlocks(inventory, backpack.getSize(), startSlot, endSlot);
    }

    /**
     * 从数据库加载玩家背包数据（支持异步）
     * @param playerUUID 玩家唯一标识符
     * @return 玩家背包实例
     */
    private PlayerBackpack loadBackpackData(UUID playerUUID) {
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
     * 异步从数据库加载玩家背包数据
     * @param playerUUID 玩家唯一标识符
     * @return 玩家背包实例的CompletableFuture
     */
    private CompletableFuture<PlayerBackpack> loadBackpackDataAsync(UUID playerUUID) {
        return plugin.getDatabaseManager().loadPlayerBackpackAsync(playerUUID)
            .thenApply(backpackData -> {
                if (backpackData != null) {
                    try {
                        return PlayerBackpack.deserialize(backpackData, playerUUID);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "反序列化个人背包数据时出错: " + playerUUID, e);
                    }
                }
                // 默认背包大小从配置文件读取
                int defaultSize = plugin.getConfig().getInt("backpack.size", 27);
                return new PlayerBackpack(playerUUID, defaultSize);
            })
            .exceptionally(e -> {
                plugin.getLogger().log(Level.SEVERE, "从数据库加载个人背包数据时出错: " + playerUUID, e);
                int defaultSize = plugin.getConfig().getInt("backpack.size", 27);
                return new PlayerBackpack(playerUUID, defaultSize);
            });
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
            com.leeinx.xibackpack.util.LogManager.warning("尝试保存null背包");
            return;
        }

        // 异步保存背包数据
        // 主线程快照 -> 异步线程 IO
        final String serializedData = backpack.serialize();
        final UUID uuid = backpack.getPlayerUUID();

        plugin.getDatabaseManager().savePlayerBackpackAsync(uuid, serializedData)
            .thenAcceptAsync(success -> {
                // 在主线程上执行插件操作
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.incrementDatabaseOperations();
                    if (!success) {
                        com.leeinx.xibackpack.util.LogManager.warning("保存玩家 %s 的背包数据失败", uuid);
                    }
                });
            })
            .exceptionally(ex -> {
                com.leeinx.xibackpack.util.ExceptionHandler.handleAsyncException("异步保存个人背包数据", ex);
                return null;
            });
    }

    /**
     * 从背包界面更新玩家背包数据
     * @param player 玩家对象
     * @param inventory 背包界面
     */
    public void updateBackpackFromInventory(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            com.leeinx.xibackpack.util.LogManager.warning("更新背包时参数为空: player=%s, inventory=%s", player, inventory);
            return;
        }

        try {
            PlayerBackpack backpack = getBackpack(player); // 此时背包应已在缓存中
            if (backpack == null) {
                com.leeinx.xibackpack.util.LogManager.warning("更新个人背包时未找到背包数据: %s", player.getName());
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

            com.leeinx.xibackpack.util.LogManager.info("更新个人背包 %s，页面 %d，槽位范围 %d-%d", player.getName(), page, startSlot, endSlot);

            // 更新背包中的物品
            for (int i = 0; i < 45 && (i + startSlot) < backpack.getSize(); i++) { // 只处理前5行的物品格
                ItemStack item = inventory.getItem(i);
                int actualSlot = i + startSlot;
                // 只更新有效的槽位
                // 修复：移除对 BARRIER 的检查，允许玩家保存屏障方块
                // 锁定槽位的屏障方块位于 backpack.getSize() 之外，不会被此循环处理
                if (item != null) {
                    backpack.setItem(actualSlot, item);
                } else {
                    backpack.setItem(actualSlot, null);
                }
            }

            // 保存到数据库
            saveBackpack(backpack); // 调用异步保存
        } catch (Exception e) {
            com.leeinx.xibackpack.util.ExceptionHandler.handleAsyncException("从背包界面更新个人背包", e);
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
            com.leeinx.xibackpack.util.LogManager.warning("处理控制按钮时玩家为空");
            return false;
        }

        try {
            Integer currentPage = playerPages.get(player.getUniqueId());
            if (currentPage == null) currentPage = 0;

            // 计算总页数（基于实际背包大小）
            int totalPages = (int) Math.ceil((double) backpackSize / 45);

            com.leeinx.xibackpack.util.LogManager.info("处理个人背包控制按钮，当前页面: %d，总页面: %d，点击槽位: %d", currentPage, totalPages, slot);

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
            com.leeinx.xibackpack.util.ExceptionHandler.handleAsyncException("处理控制按钮点击", e);
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
            com.leeinx.xibackpack.util.LogManager.info("关服保存: 没有需要保存的个人背包数据。");
            return;
        }

        com.leeinx.xibackpack.util.LogManager.info("关服保存: 正在同步保存所有玩家背包数据...");
        int count = 0;

        for (PlayerBackpack backpack : loadedBackpacks.values()) {
            try {
                // 直接在主线程执行序列化和保存 (同步)
                String serializedData = backpack.serialize();
                plugin.getDatabaseManager().savePlayerBackpack(backpack.getPlayerUUID(), serializedData);
                count++;
            } catch (Exception e) {
                com.leeinx.xibackpack.util.ExceptionHandler.handleAsyncException("关服保存: 玩家 " + backpack.getPlayerUUID() + " 背包", e);
            }
        }
        com.leeinx.xibackpack.util.LogManager.info("关服保存: 已同步保存 %d 个个人背包数据", count);
    }
    // 新增：用来在 Inventory 中携带页码信息
    public static class BackpackPageHolder implements org.bukkit.inventory.InventoryHolder {
        private final int page;
        public BackpackPageHolder(int page) { this.page = page; }
        @Override public org.bukkit.inventory.Inventory getInventory() { return null; }
        public int getPage() { return page; }
    }
}
