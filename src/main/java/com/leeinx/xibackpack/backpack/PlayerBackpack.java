package com.leeinx.xibackpack.backpack;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.leeinx.xibackpack.NBTUtil;
import com.leeinx.xibackpack.main.XiBackpack;
import java.util.logging.Level;

public class PlayerBackpack {
    private UUID playerUUID;
    private Map<Integer, ItemStack> items;
    private int size;

    /**
     * 构造函数，创建一个新的玩家背包
     * @param playerUUID 玩家唯一标识符
     * @param size 背包大小
     * @throws IllegalArgumentException 当playerUUID为null或size小于等于0时抛出
     */
    public PlayerBackpack(UUID playerUUID, int size) {
        // 添加参数验证
        if (playerUUID == null) {
            throw new IllegalArgumentException("Player UUID cannot be null");
        }
        
        if (size <= 0) {
            throw new IllegalArgumentException("Backpack size must be positive, got: " + size);
        }
        
        this.playerUUID = playerUUID;
        this.size = size;
        this.items = new HashMap<>();
    }

    /**
     * 在指定槽位设置物品
     * @param slot 槽位索引
     * @param item 物品堆
     * @throws IllegalArgumentException 当slot为负数时抛出
     */
    public void setItem(int slot, ItemStack item) {
        // 添加边界检查和参数验证
        if (slot < 0) {
            throw new IllegalArgumentException("Slot index cannot be negative, got: " + slot);
        }
        
        if (item == null || item.getType().isAir()) {
            items.remove(slot);
        } else {
            items.put(slot, item.clone());
        }
        
        // 如果插入的槽位超出了当前大小，更新背包大小
        if (slot >= size) {
            size = slot + 1;
        }
    }

    /**
     * 获取指定槽位的物品
     * @param slot 槽位索引
     * @return 物品堆，如果槽位为空则返回null
     * @throws IllegalArgumentException 当slot为负数时抛出
     */
    public ItemStack getItem(int slot) {
        if (slot < 0) {
            throw new IllegalArgumentException("Slot index cannot be negative, got: " + slot);
        }
        
        return items.get(slot);
    }

    /**
     * 获取所有物品的副本
     * @return 包含所有物品的Map副本
     */
    public Map<Integer, ItemStack> getItems() {
        return new HashMap<>(items);
    }

    /**
     * 获取玩家UUID
     * @return 玩家UUID
     */
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    /**
     * 获取背包大小
     * @return 背包大小
     */
    public int getSize() {
        return size;
    }
    
    /**
     * 设置背包大小
     * @param size 新的背包大小
     * @throws IllegalArgumentException 当size小于等于0时抛出
     */
    public void setSize(int size) {
        // 验证大小是否有效（现在只检查是否为正数，没有上限）
        if (size <= 0) {
            throw new IllegalArgumentException("Backpack size must be positive, got: " + size);
        }
        
        this.size = size;
        
        // 清理超出新大小的物品（仅当新大小更小时）
        items.entrySet().removeIf(entry -> entry.getKey() >= size);
    }

    /**
     * 将背包数据序列化为JSON字符串，用于数据库存储
     * @return 序列化的背包数据
     */
    public String serialize() {
        Map<Integer, Map<String, String>> serializedItems = new HashMap<>();
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().getType().isAir()) {
                ItemStack item = entry.getValue();
                Map<String, String> itemData = new HashMap<>();
                
                // 保存物品类型
                itemData.put("type", item.getType().name());
                
                // 保存物品数量
                itemData.put("amount", String.valueOf(item.getAmount()));
                
                // 保存NBT数据
                String nbtData = NBTUtil.getItemNBTDataForSerialization(item);
                if (nbtData != null) {
                    itemData.put("nbt", nbtData);
                }
                
                XiBackpack.getInstance().getLogger().info("Serializing item at slot " + entry.getKey() + 
                    " with type " + item.getType() + 
                    ", display name " + (item.hasItemMeta() ? item.getItemMeta().getDisplayName() : "none") +
                    ", amount " + item.getAmount() +
                    " and NBT: " + nbtData);
                    
                serializedItems.put(entry.getKey(), itemData);
            }
        }

        Map<String, Object> backpackData = new HashMap<>();
        backpackData.put("size", size);
        backpackData.put("items", serializedItems);
        
        // 记录序列化数据
        Gson gson = new Gson();
        String jsonData = gson.toJson(backpackData);
        XiBackpack.getInstance().getLogger().info("Serialized backpack data: " + jsonData);

        return jsonData;
    }

    /**
     * 从序列化的JSON数据反序列化背包
     * @param data 序列化的背包数据
     * @param playerUUID 玩家UUID
     * @return 反序列化后的背包实例
     * @throws IllegalArgumentException 当playerUUID为null时抛出
     */
    public static PlayerBackpack deserialize(String data, UUID playerUUID) {
        if (playerUUID == null) {
            throw new IllegalArgumentException("Player UUID cannot be null");
        }

        if (data == null || data.isEmpty()) {
            return new PlayerBackpack(playerUUID, 27);
        }

        try {
            Gson gson = new Gson();
            Type typeType = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> backpackData = gson.fromJson(data, typeType);

            Object sizeObj = backpackData.get("size");
            int size = 27;
            if (sizeObj instanceof Number) {
                size = ((Number) sizeObj).intValue();
            }
            if (size <= 0) size = 27;

            PlayerBackpack backpack = new PlayerBackpack(playerUUID, size);

            Map<String, Object> itemsData = (Map<String, Object>) backpackData.get("items");
            if (itemsData != null) {
                for (Map.Entry<String, Object> entry : itemsData.entrySet()) {
                    try {
                        int slot = Integer.parseInt(entry.getKey());
                        if (slot >= 0) {
                            Map<String, String> itemData = (Map<String, String>) entry.getValue();
                            String typeStr = itemData.get("type");
                            String amountStr = itemData.get("amount");
                            String nbtStr = itemData.get("nbt");

                            int amount = 1;
                            try {
                                amount = Integer.parseInt(amountStr);
                            } catch (NumberFormatException e) {
                                amount = 1;
                            }

                            // FIX: 直接使用修正后的 createItemFromNBTData 方法
                            // 不要尝试使用 NBT.itemStackFromNBT，因为保存的 NBT 中不包含 id
                            ItemStack item = NBTUtil.createItemFromNBTData(typeStr, amount, nbtStr);

                            if (item != null) {
                                backpack.setItem(slot, item);
                            }
                        }
                    } catch (Exception e) {
                        XiBackpack.getInstance().getLogger().log(Level.WARNING, "Error processing item at slot " + entry.getKey(), e);
                    }
                }
            }

            return backpack;
        } catch (Exception e) {
            XiBackpack.getInstance().getLogger().log(Level.SEVERE, "Error deserializing player backpack for player: " + playerUUID, e);
            return new PlayerBackpack(playerUUID, 27);
        }
    }
}