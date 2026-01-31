package com.leeinx.xibackpack;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import com.leeinx.xibackpack.main.XiBackpack;

public class NBTUtil {

    /**
     * 获取物品的NBT数据（序列化用）
     * @param item 物品堆
     * @return 物品的NBT数据字符串，如果物品为空或空气则返回null
     */
    public static String getItemNBTDataForSerialization(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;

        try {
            // 获取只包含额外数据的NBT，不包含物品ID和Count
            // toString() 通常返回完整的NBT复合标签
            return NBT.get(item, Object::toString);
        } catch (Exception e) {
            XiBackpack.getInstance().getLogger().log(Level.WARNING, "Error getting serialization NBT data for item: " + item.getType(), e);
            return null;
        }
    }

    /**
     * 从类型和NBT数据创建物品 (核心修复方法)
     * @param type 物品材质类型 (如 MEKANISM_ROBIT)
     * @param amount 数量
     * @param nbtData NBT JSON字符串
     * @return 恢复后的物品
     */
    public static ItemStack createItemFromNBTData(String type, int amount, String nbtData) {
        try {
            // 1. 首先根据类型创建基础物品
            Material material = Material.getMaterial(type);
            if (material == null) {
                XiBackpack.getInstance().getLogger().warning("Unknown material type: " + type);
                // 尝试作为石头返回，避免崩服，或者返回 null
                return new ItemStack(Material.STONE, amount);
            }

            ItemStack item = new ItemStack(material, amount);

            // 2. 如果有NBT数据，将其合并到刚才创建的物品上
            if (nbtData != null && !nbtData.isEmpty() && !nbtData.equals("{}")) {
                try {
                    // 使用 NBT.modify 直接修改我们在上面创建的 item 对象
                    NBT.modify(item, nbt -> {
                        // 解析保存的 NBT 字符串
                        ReadWriteNBT parsedNBT = NBT.parseNBT(nbtData);
                        // 将解析出的数据 MERGE (合并) 到物品现有的 NBT 中
                        nbt.mergeCompound(parsedNBT);
                    });
                } catch (Exception e) {
                    XiBackpack.getInstance().getLogger().log(Level.WARNING, "Error applying NBT data to item: " + type, e);
                }
            }

            return item;
        } catch (Exception e) {
            XiBackpack.getInstance().getLogger().log(Level.WARNING, "Error creating item from NBT data", e);
            return null;
        }
    }
}