package com.leeinx.xibackpack;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.leeinx.xibackpack.TeamBackpackManagementHolder;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Level;

public class TeamBackpackManager {
    private XiBackpack plugin;
    private Map<String, TeamBackpack> loadedBackpacks;
    private Map<UUID, Integer> playerPages;
    private Map<UUID, String> playerCurrentBackpack; // 记录玩家当前查看的团队背包ID
    // 记录正在查看特定团队背包的所有玩家
    private Map<String, Set<UUID>> backpackViewers;

    /**
     * 构造函数，初始化团队背包管理器
     * @param plugin 插件主类实例
     * @throws IllegalArgumentException 当plugin为null时抛出
     */
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
     * 添加成员到团队背包
     *
     * @param requester 请求玩家（必须是背包所有者或管理员）
     * @param backpackId 背包ID
     * @param targetPlayer 目标玩家
     * @return 是否添加成功
     */
    public boolean addMemberToBackpack(Player requester, String backpackId, Player targetPlayer) {
        if (requester == null || backpackId == null || targetPlayer == null) {
            plugin.getLogger().warning("添加成员到团队背包时参数为空");
            return false;
        }

        try {
            TeamBackpack backpack = getBackpack(backpackId);
            if (backpack == null) {
                requester.sendMessage("§c找不到指定的团队背包");
                return false;
            }

            // 检查请求者是否有权限（必须是所有者或管理员）
            if (!backpack.isOwner(requester.getUniqueId()) && !requester.hasPermission("xibackpack.admin")) {
                requester.sendMessage("§c您没有权限添加成员到此团队背包");
                return false;
            }

            // 检查目标玩家是否已经是成员
            if (backpack.isMember(targetPlayer.getUniqueId())) {
                requester.sendMessage("§c玩家 " + targetPlayer.getName() + " 已经是此团队背包的成员");
                return false;
            }

            // 添加成员
            backpack.addMember(targetPlayer.getUniqueId());

            // 保存到数据库
            saveBackpack(backpack);

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "添加成员到团队背包时出错", e);
            requester.sendMessage("§c添加成员时发生错误，请联系管理员");
            return false;
        }
    }

    /**
     * 从团队背包移除成员
     *
     * @param requester 请求玩家（必须是背包所有者或管理员）
     * @param backpackId 背包ID
     * @param targetUUID 目标玩家UUID
     * @return 是否移除成功
     */
    public boolean removeMemberFromBackpack(Player requester, String backpackId, UUID targetUUID) {
        if (requester == null || backpackId == null || targetUUID == null) {
            plugin.getLogger().warning("从团队背包移除成员时参数为空");
            return false;
        }

        try {
            TeamBackpack backpack = getBackpack(backpackId);
            if (backpack == null) {
                requester.sendMessage("§c找不到指定的团队背包");
                return false;
            }

            // 检查请求者是否有权限（必须是所有者或管理员）
            if (!backpack.isOwner(requester.getUniqueId()) && !requester.hasPermission("xibackpack.admin")) {
                requester.sendMessage("§c您没有权限从此团队背包移除成员");
                return false;
            }

            // 检查目标玩家是否是成员
            if (!backpack.isMember(targetUUID)) {
                requester.sendMessage("§c该玩家不是此团队背包的成员");
                return false;
            }

            // 检查是否尝试移除所有者（不允许）
            if (backpack.isOwner(targetUUID)) {
                requester.sendMessage("§c不能移除团队背包的所有者");
                return false;
            }

            // 移除成员
            backpack.removeMember(targetUUID);

            // 保存到数据库
            saveBackpack(backpack);

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "从团队背包移除成员时出错", e);
            requester.sendMessage("§c移除成员时发生错误，请联系管理员");
            return false;
        }
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
        return plugin.getDatabaseManager().loadTeamBackpack(backpackId);
    }

    /**
     * 保存背包到数据库 (异步 + 线程安全)
     * @param backpack 团队背包
     */
    @Deprecated // 标记为过时，因为它不应该直接被 onDisable 调用，但其他地方需要使用异步保存
    public void saveBackpack(TeamBackpack backpack) {
        if (backpack == null) return;

        // ========================================================
        // 步骤 1: 主线程 "快照" (Snapshot)
        // 必须在主线程获取所有数据，防止异步运行时数据发生变化
        // ========================================================

        // 1. 获取不可变的基础信息
        final String id = backpack.getId();
        final String name = backpack.getName();
        final UUID owner = backpack.getOwner();

        // 2. 序列化物品数据 (耗时较少，必须在主线程做以防 HashMap 报错)
        final String serializedData = backpack.serialize();

        // 3. 克隆成员列表 (防止异步保存时成员列表发生变化)
        final Set<UUID> membersSnapshot = new HashSet<>(backpack.getMembers());

        // ========================================================
        // 步骤 2: 异步线程 IO 操作
        // ========================================================
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // 调用 DatabaseManager 中专门为异步保存设计的方法
                    plugin.getDatabaseManager().saveTeamBackpackData(
                            id,
                            name,
                            owner,
                            serializedData,
                            membersSnapshot
                    );

                    plugin.getLogger().info("团队背包 " + id + " 已异步保存。");

                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "异步保存团队背包失败", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * 为玩家打开团队背包 (已优化性能)
     * @param player 玩家
     * @param backpackId 背包ID
     */
    public void openBackpack(Player player, String backpackId) {
        openBackpackPage(player, backpackId, 0); // 默认打开第一页
    }


    /**
     * 为玩家打开团队背包的指定页面 (已优化性能)
     * 如果背包未加载，先显示加载动画，后台加载完成后再打开。
     * @param player 玩家
     * @param backpackId 背包ID
     * @param page 页面编号
     */
    public void openBackpackPage(Player player, String backpackId, int page) {
        if (player == null) {
            plugin.getLogger().warning("尝试为null玩家打开团队背包页面");
            return;
        }
        UUID playerUUID = player.getUniqueId();

        // 1. 检查缓存
        if (loadedBackpacks.containsKey(backpackId)) {
            TeamBackpack backpack = loadedBackpacks.get(backpackId);
            if (backpack != null) {
                // 已加载，检查权限后直接打开
                if (!backpack.isMember(playerUUID) && !player.hasPermission("xibackpack.admin")) {
                    player.sendMessage(plugin.getMessage("team-backpack.no_permission", "§c您没有权限访问此团队背包"));
                    return;
                }
                openTeamBackpackGuiInternal(player, backpack, page);
            } else {
                // 缓存中是null，可能加载失败过，强制重新加载
                plugin.getLogger().warning("团队背包 " + backpackId + " 缓存为null，尝试重新异步加载。");
                startAsyncTeamBackpackLoad(player, backpackId, page);
            }
            return;
        }

        // 2. 未加载，开始异步加载流程
        startAsyncTeamBackpackLoad(player, backpackId, page);
    }
    /**
     * 启动异步团队背包加载流程
     */
    private void startAsyncTeamBackpackLoad(Player player, String backpackId, int page) {
        openLoadingGui(player); // 先显示加载动画
        player.sendMessage(plugin.getMessage("team-backpack.loading_message", "§d正在加载团队仓库，请稍候..."));

        new BukkitRunnable() {
            @Override
            public void run() {
                // [异步线程] 加载数据 (同步调用数据库管理器，但此Runnable本身是异步运行的)
                final TeamBackpack backpack = loadBackpackFromDatabase(backpackId);

                // 回到 [主线程] 处理UI
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!player.isOnline()) return;

                        // 检查玩家当前是否还开着Loading界面 (防止玩家关闭界面后被强行打开)
                        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof LoadingHolder)) {
                            // 如果玩家已经关掉了加载界面，或者打开了其他界面，就不再强制打开背包
                            return;
                        }

                        if (backpack != null) {
                            loadedBackpacks.put(backpackId, backpack);
                            // 再次检查权限，因为加载过程中权限可能变化
                            if (!backpack.isMember(player.getUniqueId()) && !player.hasPermission("xibackpack.admin")) {
                                player.sendMessage(plugin.getMessage("team-backpack.no_permission", "§c您没有权限访问此团队背包"));
                                player.closeInventory();
                                return;
                            }
                            openTeamBackpackGuiInternal(player, backpack, page);
                        } else {
                            player.sendMessage(plugin.getMessage("team-backpack.load_failed", "§c团队背包不存在或加载失败。"));
                            player.closeInventory();
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }
    /**
     * 内部方法：构建并打开真正的团队背包GUI
     * @param player 玩家
     * @param backpack 团队背包实例
     * @param page 页面索引
     */
    private void openTeamBackpackGuiInternal(Player player, TeamBackpack backpack, int page) {
        try {
            // 确保玩家有权限访问 (双重检查)
            if (!backpack.isMember(player.getUniqueId()) && !player.hasPermission("xibackpack.admin")) {
                player.sendMessage(plugin.getMessage("team-backpack.no_permission", "§c您没有权限访问此团队背包"));
                return;
            }

            playerPages.put(player.getUniqueId(), page);
            playerCurrentBackpack.put(player.getUniqueId(), backpack.getId());
            backpackViewers.computeIfAbsent(backpack.getId(), k -> new HashSet<>()).add(player.getUniqueId());

            Inventory inventory = createBackpackInventory(backpack, page);
            int startSlot = page * 45;
            int endSlot = Math.min(startSlot + 45, backpack.getSize());

            for (int i = startSlot; i < endSlot; i++) {
                ItemStack item = backpack.getItem(i);
                if (item != null) {
                    inventory.setItem(i - startSlot, item);
                }
            }

            addBarrierBlocks(inventory, backpack, startSlot, endSlot);
            addControlButtons(inventory, page, backpack.getSize());

            player.openInventory(inventory);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "打开团队背包页面时出错", e);
            player.sendMessage(plugin.getMessage("team-backpack.open_error", "§c打开团队背包页面时发生错误，请联系管理员"));
        }
    }
    /**
     * 显示一个加载中动画界面 (团队背包专用)
     * @param player 玩家对象
     */
    private void openLoadingGui(Player player) {
        Inventory loadingInv = Bukkit.createInventory(new LoadingHolder(), 9, plugin.getMessage("team-backpack.loading_gui_title", "§0团队仓库加载中..."));
        ItemStack item = new ItemStack(Material.ENDER_CHEST); // 团队背包用末影箱图标
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getMessage("team-backpack.loading_item_name", "§d团队数据同步中..."));
            meta.setLore(Arrays.asList(plugin.getMessage("team-backpack.loading_item_lore_line1", "§7正在从云端拉取共享仓库"), plugin.getMessage("team-backpack.loading_item_lore_line2", "§7请稍候...")));
            item.setItemMeta(meta);
        }
        loadingInv.setItem(4, item);
        player.openInventory(loadingInv);
    }

    /**
     * 创建背包界面
     * @param backpack 团队背包
     * @param page 页面编号
     * @return Inventory对象
     */
    private Inventory createBackpackInventory(TeamBackpack backpack, int page) {
        String backpackName = backpack.getName() != null ? backpack.getName() : "团队背包";
        return Bukkit.createInventory(new TeamBackpackPageHolder(backpack.getId(), page), 54,
                "§0团队背包: " + backpackName + " §7(第" + (page + 1) + "页)");
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
     * @param inventory 背包界面
     * @param backpack 团队背包
     * @param startSlot 起始槽位（当前页面的起始位置）
     * @param endSlot 结束槽位（当前页面的结束位置）
     */
    private void addBarrierBlocks(Inventory inventory, TeamBackpack backpack, int startSlot, int endSlot) {
        int maxAllowedSlot = Math.min(backpack.getSize() - startSlot, 45);
        for (int i = 0; i < 45; i++) {
            if (i >= maxAllowedSlot) {
                ItemStack barrier = new ItemStack(Material.BARRIER);
                ItemMeta meta = barrier.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(plugin.getMessage("backpack.slot_locked", "§c锁定槽位"));
                    barrier.setItemMeta(meta);
                }
                inventory.setItem(i, barrier);
            }
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
                    // 使用 page 而不是 page + 1，因为 message 中的 page 是当前页码
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
                // 如果已经是最后一页，显示特殊文本
                if (page >= totalPages - 1) {
                    nextMeta.setDisplayName(plugin.getMessage("backpack.page_next_last", "§7最后一页"));
                } else {
                    // 使用 page + 2 因为下一页是当前页+1，显示时页码从1开始计数
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
                        getLocalizedCurrentPageText(page + 1) // 显示当前页码 (从1开始)
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
            Inventory openInventory = player.getOpenInventory().getTopInventory();
            if (!(openInventory.getHolder() instanceof TeamBackpackPageHolder)) {
                return false;
            }

            TeamBackpackPageHolder holder = (TeamBackpackPageHolder) openInventory.getHolder();
            String backpackId = holder.getBackpackId();
            int currentPage = holder.getPage();

            TeamBackpack backpack = getBackpack(backpackId);
            if (backpack == null) {
                plugin.getLogger().warning("处理团队背包控制按钮时找不到背包: " + backpackId);
                return false;
            }

            // 计算总页数（基于实际背包大小）
            int totalPages = (int) Math.ceil((double) backpackSize / 45);

            plugin.getLogger().info("处理团队背包控制按钮，当前页面: " + currentPage + "，总页面: " + totalPages + "，点击槽位: " + slot + "，背包ID: " + backpackId);

            // 检查是否点击了控制按钮
            if (slot == 45) { // 上一页
                if (currentPage > 0) {
                    openBackpackPage(player, backpackId, currentPage - 1);
                    return true;
                }
            } else if (slot == 53) { // 下一页
                // 仅允许翻到已解锁的页面
                if (currentPage < totalPages - 1) {
                    openBackpackPage(player, backpackId, currentPage + 1);
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
     * 检查指定的Inventory是否为团队背包界面
     *
     * @param inventory 要检查的Inventory
     * @return 是否为团队背包界面
     */
    public boolean isTeamBackpackInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }

        // 【关键修复】只检查 Holder 是否为 TeamBackpackPageHolder
        return inventory.getHolder() instanceof TeamBackpackPageHolder;
    }

    /**
     * 从背包界面更新团队背包中的物品
     * @param player 玩家
     * @param inventory 背包界面
     */
    public void updateBackpackFromInventory(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            plugin.getLogger().warning("更新团队背包时参数为空: player=" + player + ", inventory=" + inventory);
            return;
        }

        try {
            if (!(inventory.getHolder() instanceof TeamBackpackPageHolder)) {
                plugin.getLogger().warning("团队背包界面缺少正确的Holder");
                return;
            }

            TeamBackpackPageHolder holder = (TeamBackpackPageHolder) inventory.getHolder();
            String backpackId = holder.getBackpackId();
            int page = holder.getPage();

            TeamBackpack backpack = getBackpack(backpackId);
            if (backpack == null) {
                plugin.getLogger().warning("无法找到团队背包: " + backpackId);
                return;
            }

            // 检查玩家是否有权限修改此背包（只有所有者才能修改）
            if (!backpack.isOwner(player.getUniqueId()) && !player.hasPermission("xibackpack.admin")) {
                // 普通成员只能查看，不能修改
                plugin.getLogger().info("玩家 " + player.getName() + " 尝试修改团队背包 " + backpackId + " 但没有权限");
                return;
            }

            // 计算页面范围
            int startSlot = page * 45;
            int endSlot = startSlot + 45;

            // 确保不会超出实际背包大小
            endSlot = Math.min(endSlot, backpack.getSize());

            plugin.getLogger().info("更新团队背包 " + backpackId + "，页面 " + page + "，槽位范围 " + startSlot + "-" + endSlot);

            // 更新背包中的物品，确保使用正确的槽位计算
            for (int i = 0; i < 45 && (i + startSlot) < backpack.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                int actualSlot = i + startSlot;
                // 只更新有效的槽位（非屏障方块槽位）
                if (item != null && item.getType() != Material.BARRIER) {
                    plugin.getLogger().info("设置物品到槽位 " + actualSlot + ": " + (item.getType() != null ? item.getType().name() : "null"));
                    backpack.setItem(actualSlot, item);
                } else if (item == null || item.getType().isAir()) {
                    plugin.getLogger().info("清空槽位 " + actualSlot);
                    backpack.setItem(actualSlot, null);
                }
            }

            // 保存到数据库
            saveBackpack(backpack);
            plugin.getLogger().info("团队背包 " + backpackId + " 已保存到数据库");

            // 同步更新给其他正在查看此背包的玩家
            syncBackpackToViewers(backpackId, inventory, page);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "从背包界面更新团队背包时出错", e);
        }
    }

    /**
     * 同步背包更新给其他正在查看此背包的玩家
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
            Inventory viewerInventory = viewer.getOpenInventory().getTopInventory();
            if (viewerInventory.getHolder() instanceof TeamBackpackPageHolder) {
                TeamBackpackPageHolder holder = (TeamBackpackPageHolder) viewerInventory.getHolder();
                if (backpackId.equals(holder.getBackpackId()) && page == holder.getPage()) {
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

        // 首先尝试从当前打开的界面获取
        Inventory openInventory = player.getOpenInventory().getTopInventory();
        if (openInventory.getHolder() instanceof TeamBackpackPageHolder) {
            return ((TeamBackpackPageHolder) openInventory.getHolder()).getPage();
        }

        // 后备：从Map获取
        return playerPages.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * 获取玩家当前查看的背包ID
     * @param player 玩家
     * @return 背包ID
     */
    public String getPlayerCurrentBackpackId(Player player) {
        if (player == null) {
            return null;
        }

        // 首先尝试从当前打开的界面获取
        Inventory openInventory = player.getOpenInventory().getTopInventory();
        if (openInventory.getHolder() instanceof TeamBackpackPageHolder) {
            return ((TeamBackpackPageHolder) openInventory.getHolder()).getBackpackId();
        }

        // 后备：从Map获取
        return playerCurrentBackpack.get(player.getUniqueId());
    }

    /**
     * 当玩家关闭背包时，从观察者列表中移除
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
            
            // 当玩家关闭背包时，保存背包数据
            TeamBackpack backpack = getBackpack(backpackId);
            if (backpack != null) {
                saveBackpack(backpack);
            }
        }
    }

    /**
     * 为玩家打开团队背包管理界面
     * @param player 玩家
     */
    public void openManagementGUI(Player player) {
        if (player == null) {
            plugin.getLogger().warning("尝试为null玩家打开团队背包管理界面");
            return;
        }

        try {
            // 创建GUI界面 (6行 x 9列)，使用Holder来标识这个Inventory
            Inventory inventory = Bukkit.createInventory(new TeamBackpackManagementHolder(), 54, "§0团队背包管理");

            // 用黑色玻璃板填充边缘
            ItemStack borderItem = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta borderMeta = borderItem.getItemMeta();
            if (borderMeta != null) {
                borderMeta.setDisplayName(" ");
                borderItem.setItemMeta(borderMeta);
            }

            // 填充顶部和底部边缘
            for (int i = 0; i < 9; i++) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, borderItem.clone());
                }
                if (inventory.getItem(45 + i) == null) {
                    inventory.setItem(45 + i, borderItem.clone());
                }
            }

            // 填充左右边缘
            for (int i = 1; i < 5; i++) {
                if (inventory.getItem(i * 9) == null) {
                    inventory.setItem(i * 9, borderItem.clone());
                }
                if (inventory.getItem(i * 9 + 8) == null) {
                    inventory.setItem(i * 9 + 8, borderItem.clone());
                }
            }

            // 添加"创建团队背包"按钮
            ItemStack createButton = new ItemStack(Material.CHEST);
            ItemMeta createMeta = createButton.getItemMeta();
            if (createMeta != null) {
                createMeta.setDisplayName("§a创建团队背包");
                List<String> lore = new ArrayList<>();
                lore.add("§7点击创建一个新的团队背包");
                lore.add("§7创建后需要在聊天中输入背包名称");
                createMeta.setLore(lore);
                createButton.setItemMeta(createMeta);
            }
            inventory.setItem(49, createButton); // 最下面一行的中间位置

            // 加载玩家的团队背包并显示
            loadPlayerTeamBackpacks(player, inventory);

            player.openInventory(inventory);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "打开团队背包管理界面时出错", e);
            player.sendMessage("§c打开团队背包管理界面时发生错误，请联系管理员");
        }
    }

    /**
     * 加载玩家的团队背包并在GUI中显示
     * @param player 玩家
     * @param inventory GUI界面
     */
    private void loadPlayerTeamBackpacks(Player player, Inventory inventory) {
        if (player == null || inventory == null) return;

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    UUID playerUUID = player.getUniqueId();

                    // 1. 异步只负责查询 ID 列表 (只读操作)
                    List<String> joinedBackpacks = plugin.getDatabaseManager().getPlayerJoinedTeamBackpacks(playerUUID);
                    List<String> ownedBackpacks = plugin.getDatabaseManager().getPlayerOwnedTeamBackpacks(playerUUID);

                    // 合并所有需要显示的ID
                    Set<String> allIds = new HashSet<>(joinedBackpacks);
                    allIds.addAll(ownedBackpacks);

                    // 2. 回到主线程进行对象的获取和界面的渲染
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!player.isOnline()) return;

                            int slotIndex = 10;
                            // 修正：在主线程调用 getBackpack，保证 HashMap 安全
                            for (String id : allIds) {
                                if (slotIndex >= 44) break;
                                if (slotIndex % 9 == 8 || slotIndex % 9 == 0) {
                                    slotIndex++;
                                    if (slotIndex >= 44) break;
                                }

                                TeamBackpack backpack = getBackpack(id);
                                if (backpack == null) continue; // 如果加载失败则跳过

                                ItemStack backpackItem = new ItemStack(Material.CHEST);
                                ItemMeta backpackMeta = backpackItem.getItemMeta();
                                if (backpackMeta != null) {
                                    // ... 这里保留你原本的 ItemMeta 设置逻辑 ...
                                    String bId = backpack.getId();
                                    backpackMeta.setDisplayName((ownedBackpacks.contains(bId) ? "§b§o" : "§7") + backpack.getName());

                                    List<String> lore = new ArrayList<>();
                                    lore.add("§7ID: " + bId);
                                    lore.add("§7所有者: " + Bukkit.getOfflinePlayer(backpack.getOwner()).getName());
                                    lore.add("§7成员数量: " + backpack.getMembers().size());
                                    lore.add("");
                                    lore.add("§e左键点击打开背包");
                                    lore.add("§e右键点击查看详情");

                                    if (ownedBackpacks.contains(bId)) {
                                        backpackMeta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                                        lore.add("§6你是此背包的所有者");
                                    }
                                    backpackMeta.setLore(lore);
                                    backpackItem.setItemMeta(backpackMeta);
                                }
                                inventory.setItem(slotIndex, backpackItem);
                                slotIndex++;
                            }
                        }
                    }.runTask(plugin);

                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "异步加载团队背包数据时出错", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * 处理管理界面中的点击事件
     * @param player 玩家
     * @param slot 点击的槽位
     * @param clickType 点击类型
     * @return 是否处理了点击事件
     */
    public boolean handleManagementGUIClick(Player player, int slot, org.bukkit.event.inventory.ClickType clickType) {
        if (player == null) {
            return false;
        }

        try {
            // 检查是否点击了"创建团队背包"按钮
            if (slot == 49) {
                player.closeInventory();
                player.sendMessage("§a请输入你想要创建的团队背包名称:");
                // 这里应该设置一个状态，表示玩家下一步需要输入背包名称
                // 我们可以在主类中添加一个Map来跟踪这些状态
                plugin.setPlayerCreatingTeamBackpack(player.getUniqueId(), true);
                return true;
            }

            // 检查是否点击了背包项目
            if (slot > 9 && slot < 45 && slot % 9 != 0 && slot % 9 != 8) {
                // 获取点击的背包项目
                Inventory inventory = player.getOpenInventory().getTopInventory();
                ItemStack item = inventory.getItem(slot);

                if (item != null && item.getType() == Material.CHEST) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.hasDisplayName()) {
                        String displayName = meta.getDisplayName();
                        // 从显示名称中提取背包ID（存储在Lore中）
                        if (meta.hasLore()) {
                            List<String> lore = meta.getLore();
                            if (lore != null && !lore.isEmpty()) {
                                String idLine = lore.get(0);
                                if (idLine.startsWith("§7ID: ")) {
                                    String backpackId = idLine.substring(6); // 移除"§7ID: "前缀

                                    if (clickType == org.bukkit.event.inventory.ClickType.LEFT) {
                                        // 左键点击打开背包
                                        openBackpack(player, backpackId);
                                        return true;
                                    } else if (clickType == org.bukkit.event.inventory.ClickType.RIGHT) {
                                        // 右键点击查看详细信息（可以在此处添加更多功能）
                                        TeamBackpack backpack = getBackpack(backpackId);
                                        if (backpack != null) {
                                            player.sendMessage("§b===== 团队背包详情 =====");
                                            player.sendMessage("§f名称: " + backpack.getName());
                                            player.sendMessage("§fID: " + backpack.getId());
                                            player.sendMessage("§f所有者: " + Bukkit.getOfflinePlayer(backpack.getOwner()).getName());
                                            player.sendMessage("§f成员数量: " + backpack.getMembers().size());
                                            player.sendMessage("§f背包大小: " + backpack.getSize() + " 格");
                                            player.sendMessage("§b========================");
                                        }
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "处理管理界面点击事件时出错", e);
        }

        return false;
    }
    /**
     * 保存所有已加载的团队背包 (关服时同步保存)
     */
    public void saveAllBackpacks() {
        if (loadedBackpacks == null || loadedBackpacks.isEmpty()) {
            plugin.getLogger().info("关服保存: 没有需要保存的团队背包数据。");
            return;
        }

        plugin.getLogger().info("关服保存: 正在同步保存所有团队背包数据...");
        int count = 0;

        for (TeamBackpack backpack : loadedBackpacks.values()) {
            try {
                // 1. 获取数据快照
                String id = backpack.getId();
                String name = backpack.getName();
                UUID owner = backpack.getOwner();
                String serializedData = backpack.serialize();
                Set<UUID> membersSnapshot = new HashSet<>(backpack.getMembers());

                // 2. 直接同步调用数据库保存 (不使用 runTaskAsynchronously)
                plugin.getDatabaseManager().saveTeamBackpackData(
                        id,
                        name,
                        owner,
                        serializedData,
                        membersSnapshot
                );
                count++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "关服保存: 团队背包 " + backpack.getId() + " 时出错", e);
            }
        }
        plugin.getLogger().info("关服保存: 已同步保存 " + count + " 个团队背包数据");
    }
    // 在 TeamBackpackManager 类文件的最末尾添加
    public static class TeamBackpackPageHolder implements org.bukkit.inventory.InventoryHolder {
        private final String backpackId;
        private final int page;

        public TeamBackpackPageHolder(String backpackId, int page) {
            this.backpackId = backpackId;
            this.page = page;
        }

        public String getBackpackId() { return backpackId; }
        public int getPage() { return page; }

        @Override
        public org.bukkit.inventory.Inventory getInventory() { return null; }
    }
}