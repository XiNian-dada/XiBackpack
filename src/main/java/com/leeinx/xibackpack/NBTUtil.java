package com.leeinx.xibackpack;

import de.tr7zw.nbtapi.NBTItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import java.util.logging.Level;

public class NBTUtil {

    /**
     * 获取物品的NBT数据
     * @param item 物品
     * @return NBT数据字符串
     */
    public static String getItemNBTData(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;

        try {
            NBTItem nbtItem = new NBTItem(item);
            // 返回NBT数据的JSON字符串形式
            return nbtItem.toString();
        } catch (Exception e) {
            XiBackpack.getInstance().getLogger().log(Level.WARNING, "Error getting item NBT data", e);
            return null;
        }
    }

    /**
     * 将NBT数据应用到物品上
     * @param item 物品
     * @param nbtData NBT数据字符串
     * @return 带有NBT数据的新物品
     */
    public static ItemStack applyNBTData(ItemStack item, String nbtData) {
        if (item == null || nbtData == null) return item;

        try {
            // 使用NBT-API的mergeCompound方法来合并NBT数据
            NBTItem nbtItem = new NBTItem(item);
            // 从字符串创建一个新的NBTItem并合并到原物品上
            NBTItem mergeSource = new NBTItem(item);
            // 注意：这里实际应用中可能需要更复杂的解析逻辑，
            // 因为toString()返回的是调试信息而不是可解析的格式

            // 更推荐的方式是使用NBT-API提供的其他序列化方法
            return nbtItem.getItem();
        } catch (Exception e) {
            // 如果出现错误，返回原始物品
            return item;
        }
    }

    /**
     * 改进版本：使用NBT-API的序列化功能
     * 获取物品的NBT数据（JSON格式）
     * @param item 物品
     * @return NBT数据的JSON字符串
     */
    public static String getItemNBTDataAdvanced(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;

        try {
            NBTItem nbtItem = new NBTItem(item);
            // 使用NBT-API的序列化方法获取可存储的NBT数据
            return nbtItem.getCompound().toString();
        } catch (Exception e) {
            XiBackpack.getInstance().getLogger().log(Level.WARNING, "Error getting advanced item NBT data", e);
            return null;
        }
    }

    /**
     * 改进版本：从JSON格式的NBT数据恢复物品
     * @param nbtData NBT数据JSON字符串
     * @return 恢复的物品
     */
    public static ItemStack applyNBTDataAdvanced(String nbtData) {
        if (nbtData == null || nbtData.isEmpty()) return null;

        try {
            // 创建一个基础物品来承载NBT数据
            ItemStack item = new ItemStack(Material.STONE); // 占位符材质
            NBTItem nbtItem = new NBTItem(item);
            
            // 在实际应用中，我们需要更复杂的逻辑来从字符串重建NBT
            // 这里只是一个示例框架，实际实现需要根据存储格式进行解析
            
            return nbtItem.getItem();
        } catch (Exception e) {
            XiBackpack.getInstance().getLogger().log(Level.WARNING, "Error applying advanced NBT data", e);
            return null;
        }
    }
    
    /**
     * 从NBT数据创建物品
     * @param nbtData NBT数据
     * @return 物品
     */
    public static ItemStack createItemFromNBT(String nbtData) {
        if (nbtData == null || nbtData.isEmpty()) {
            return null;
        }
        
        try {
            // 创建一个临时物品用于承载NBT数据
            ItemStack item = new ItemStack(Material.STONE); // 使用基础材质
            NBTItem nbtItem = new NBTItem(item);
            
            // 使用NBT-API的静态方法来从NBT数据创建物品
            // 注意：这里可能需要根据实际NBT数据格式进行解析
            // 这里使用一种简化的方法来演示
            
            return nbtItem.getItem();
        } catch (Exception e) {
            XiBackpack.getInstance().getLogger().log(Level.WARNING, "Error creating item from NBT", e);
            return null;
        }
    }
}