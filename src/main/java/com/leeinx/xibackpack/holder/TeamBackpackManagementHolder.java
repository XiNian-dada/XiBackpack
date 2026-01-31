package com.leeinx.xibackpack.holder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * 团队背包管理界面的Holder类
 * 用于标识和区分不同的Inventory实例
 */
public class TeamBackpackManagementHolder implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        // 这个方法在Bukkit中主要用于某些内部操作，我们可以返回null
        return null;
    }
}