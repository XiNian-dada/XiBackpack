package com.leeinx.xibackpack.handler;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.leeinx.xibackpack.main.XiBackpack;
import com.leeinx.xibackpack.holder.LoadingHolder;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * 背包管理器基类
 * 提取个人背包和团队背包管理器的共同逻辑
 */
public abstract class BaseBackpackManager {
    protected XiBackpack plugin;
    protected Map<UUID, Integer> playerPages;

    /**
     * 构造函数，初始化背包管理器
     * @param plugin 插件主类实例
     * @throws IllegalArgumentException 当plugin为null时抛出
     */
    public BaseBackpackManager(XiBackpack plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;
        this.playerPages = new ConcurrentHashMap<>();
    }

    /**
     * 显示一个加载中动画界面
     * @param player 玩家对象
     */
    protected void openLoadingGui(Player player) {
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
     * 向背包界面添加控制按钮（上一页、下一页等）
     * @param inventory 背包界面
     * @param page 当前页面索引
     * @param backpackSize 背包总大小
     */
    protected void addControlButtons(Inventory inventory, int page, int backpackSize) {
        if (inventory == null) {
            com.leeinx.xibackpack.util.LogManager.warning("尝试向null背包添加控制按钮");
            return;
        }

        int totalPages = (backpackSize + 44) / 45; // 每页45个物品槽，向上取整

        // 计算控制按钮位置
        int nextPageSlot = 53; // 右下角
        int prevPageSlot = 45; // 左下角

        // 添加下一页按钮
        if (page < totalPages - 1) {
            ItemStack nextPageItem = new ItemStack(Material.ARROW);
            ItemMeta meta = nextPageItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a下一页");
                nextPageItem.setItemMeta(meta);
            }
            inventory.setItem(nextPageSlot, nextPageItem);
        }

        // 添加上一页按钮
        if (page > 0) {
            ItemStack prevPageItem = new ItemStack(Material.ARROW);
            ItemMeta meta = prevPageItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a上一页");
                prevPageItem.setItemMeta(meta);
            }
            inventory.setItem(prevPageSlot, prevPageItem);
        }

        // 添加页面信息按钮
        ItemStack pageInfoItem = new ItemStack(Material.PAPER);
        ItemMeta meta = pageInfoItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e页面信息");
            meta.setLore(Collections.singletonList("§7第 " + (page + 1) + " / " + totalPages + " 页"));
            pageInfoItem.setItemMeta(meta);
        }
        inventory.setItem(49, pageInfoItem); // 中间位置
    }

    /**
     * 在未解锁的槽位中添加屏障方块
     * @param inventory 背包界面
     * @param backpackSize 背包总大小
     * @param startSlot 起始槽位（当前页面的起始位置）
     * @param endSlot 结束槽位（当前页面的结束位置）
     */
    protected void addBarrierBlocks(Inventory inventory, int backpackSize, int startSlot, int endSlot) {
        // 计算当前页面中已解锁的最大槽位（相对于当前页面）
        int maxAllowedSlot = Math.min(backpackSize - startSlot, 45); // 当前页面最大可用槽位数

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
     * 获取本地化页面文本
     * @param page 页面索引
     * @return 本地化后的页面文本
     */
    protected String getLocalizedPageText(int page) {
        if ("zh".equals(plugin.getLanguage())) {
            return "第" + (page + 1) + "页";
        } else {
            return "Page " + (page + 1);
        }
    }

    /**
     * 处理背包操作异常
     * @param player 玩家对象
     * @param operation 操作名称
     * @param e 异常对象
     */
    protected void handleBackpackException(Player player, String operation, Exception e) {
        com.leeinx.xibackpack.util.ExceptionHandler.handleBackpackException(player, operation, e);
    }

    /**
     * 记录背包操作错误
     * @param operation 操作名称
     * @param id 背包或玩家ID
     * @param e 异常对象
     */
    protected void logBackpackError(String operation, String id, Exception e) {
        plugin.getLogger().log(Level.SEVERE, operation + "出错: " + id, e);
    }
}
