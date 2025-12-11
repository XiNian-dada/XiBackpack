package com.leeinx.xibackpack;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.inventory.ItemStack;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerBackpack {
    private UUID playerUUID;
    private Map<Integer, ItemStack> items;
    private int size;

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

    public ItemStack getItem(int slot) {
        if (slot < 0) {
            throw new IllegalArgumentException("Slot index cannot be negative, got: " + slot);
        }
        
        return items.get(slot);
    }

    public Map<Integer, ItemStack> getItems() {
        return new HashMap<>(items);
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public int getSize() {
        return size;
    }
    
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
        Map<Integer, String> serializedItems = new HashMap<>();
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().getType().isAir()) {
                String nbtData = NBTUtil.getItemNBTDataAdvanced(entry.getValue());
                if (nbtData != null) {
                    serializedItems.put(entry.getKey(), nbtData);
                }
            }
        }

        Map<String, Object> backpackData = new HashMap<>();
        backpackData.put("size", size);
        backpackData.put("items", serializedItems);

        Gson gson = new Gson();
        return gson.toJson(backpackData);
    }

    /**
     * 从序列化的JSON数据反序列化背包
     * @param data 序列化的数据
     * @param playerUUID 玩家UUID
     * @return 反序列化的背包对象
     */
    public static PlayerBackpack deserialize(String data, UUID playerUUID) {
        // 参数验证
        if (playerUUID == null) {
            throw new IllegalArgumentException("Player UUID cannot be null");
        }
        
        if (data == null || data.isEmpty()) {
            // 返回默认大小的空背包
            return new PlayerBackpack(playerUUID, 27); // 默认大小
        }

        try {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> backpackData = gson.fromJson(data, type);
            
            Object sizeObj = backpackData.get("size");
            int size = 27; // 默认大小
            if (sizeObj instanceof Number) {
                size = ((Number) sizeObj).intValue();
            }
            
            // 验证大小有效性
            if (size <= 0) {
                size = 27; // 使用默认大小
            }

            PlayerBackpack backpack = new PlayerBackpack(playerUUID, size);

            Map<String, String> itemsData = (Map<String, String>) backpackData.get("items");
            if (itemsData != null) {
                for (Map.Entry<String, String> entry : itemsData.entrySet()) {
                    try {
                        int slot = Integer.parseInt(entry.getKey());
                        if (slot >= 0) { // 确保槽位索引有效
                            ItemStack item = NBTUtil.createItemFromNBT(entry.getValue());
                            if (item != null && !item.getType().isAir()) {
                                backpack.setItem(slot, item);
                            }
                        }
                    } catch (NumberFormatException e) {
                        // 忽略无效的槽位索引
                        XiBackpack.getInstance().getLogger().log(Level.WARNING, "Invalid slot index in backpack data: " + entry.getKey());
                    }
                }
            }

            return backpack;
        } catch (Exception e) {
            XiBackpack.getInstance().getLogger().log(Level.SEVERE, "Error deserializing player backpack", e);
            // 解析失败时返回默认背包
            return new PlayerBackpack(playerUUID, 27);
        }
    }
}