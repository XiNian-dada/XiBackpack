package com.leeinx.xibackpack;

import org.bukkit.inventory.ItemStack;
import java.util.*;

public class TeamBackpack {
    private String id;
    private String name;
    private UUID owner;
    private Set<UUID> members;
    private Map<Integer, ItemStack> items;
    private int size;

    /**
     * 构造函数，创建一个新的团队背包
     * @param id 背包唯一标识符
     * @param owner 背包所有者UUID
     * @param name 背包名称
     */
    public TeamBackpack(String id, UUID owner, String name) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.members = new HashSet<>();
        this.items = new HashMap<>();
        this.size = 27; // 默认大小与个人背包相同

        // 添加创建者为成员
        this.members.add(owner);
    }

    /**
     * 获取背包ID
     * @return 背包ID
     */
    public String getId() {
        return id;
    }

    /**
     * 获取背包名称
     * @return 背包名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置背包名称
     * @param name 新的背包名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取背包所有者UUID
     * @return 背包所有者UUID
     */
    public UUID getOwner() {
        return owner;
    }

    /**
     * 获取所有成员UUID集合的副本
     * @return 所有成员UUID集合的副本
     */
    public Set<UUID> getMembers() {
        return new HashSet<>(members);
    }

    /**
     * 检查指定玩家是否为背包成员
     * @param playerUUID 玩家UUID
     * @return 如果是成员则返回true，否则返回false
     */
    public boolean isMember(UUID playerUUID) {
        return members.contains(playerUUID);
    }

    /**
     * 检查指定玩家是否为背包所有者
     * @param playerUUID 玩家UUID
     * @return 如果是所有者则返回true，否则返回false
     */
    public boolean isOwner(UUID playerUUID) {
        return owner.equals(playerUUID);
    }

    /**
     * 添加成员到背包
     * @param playerUUID 玩家UUID
     */
    public void addMember(UUID playerUUID) {
        members.add(playerUUID);
    }

    /**
     * 从背包中移除成员（不能移除所有者）
     * @param playerUUID 玩家UUID
     */
    public void removeMember(UUID playerUUID) {
        if (!owner.equals(playerUUID)) { // 不能移除所有者
            members.remove(playerUUID);
        }
    }

    /**
     * 在指定槽位设置物品
     * @param slot 槽位索引
     * @param item 物品堆
     * @throws IllegalArgumentException 当slot为负数时抛出
     */
    public void setItem(int slot, ItemStack item) {
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
     * 设置整个物品集合（用于数据库加载）
     * @param items 物品集合
     */
    public void setItems(Map<Integer, ItemStack> items) {
        this.items.clear();
        if (items != null) {
            this.items.putAll(items);
            // 只有当items不为空时才更新背包大小
            if (!items.isEmpty()) {
                // 查找最大的槽位索引
                int maxSlot = Collections.max(items.keySet());
                // 确保背包大小至少比最大槽位大1，但不低于默认大小27
                this.size = Math.max(maxSlot + 1, Math.max(this.size, 27));
            }
        }
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
        if (size <= 0) {
            throw new IllegalArgumentException("Backpack size must be positive, got: " + size);
        }

        this.size = size;

        // 清理超出新大小的物品（仅当新大小更小时）
        items.entrySet().removeIf(entry -> entry.getKey() >= size);
    }

    /**
     * 将团队背包数据序列化为JSON字符串，用于数据库存储
     * @return 序列化的背包数据
     */
    public String serialize() {
        try {
            Map<Integer, Map<String, String>> serializedItems = new HashMap<>();
            // 遍历背包里所有的物品
            for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                ItemStack item = entry.getValue();
                if (item != null && !item.getType().isAir()) {
                    Map<String, String> itemData = new HashMap<>();
                    itemData.put("type", item.getType().name());
                    itemData.put("amount", String.valueOf(item.getAmount()));

                    // 关键：调用 NBTUtil 处理 NBT 数据
                    String nbtData = NBTUtil.getItemNBTDataForSerialization(item);
                    if (nbtData != null && !nbtData.isEmpty() && !nbtData.equals("{}")) {
                        itemData.put("nbt", nbtData);
                    }
                    serializedItems.put(entry.getKey(), itemData);
                }
            }

            Map<String, Object> root = new HashMap<>();
            root.put("size", size);
            root.put("items", serializedItems);

            return new com.google.gson.Gson().toJson(root);
        } catch (Exception e) {
            XiBackpack.getInstance().getLogger().severe("序列化团队背包失败! " + e.getMessage());
            return "{\"size\":" + size + ",\"items\":{}}";
        }
    }

    /**
     * 修改后的反序列化方法
     * @param data 序列化的背包数据
     * @param id 背包ID
     * @param name 背包名称
     * @param owner 背包所有者UUID
     * @return 反序列化后的团队背包实例
     */
    public static TeamBackpack deserialize(String data, String id, String name, UUID owner) {
        TeamBackpack backpack = new TeamBackpack(id, owner, name);
        if (data == null || data.isEmpty() || "{}".equals(data)) {
            return backpack;
        }

        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            Map<String, Object> root = gson.fromJson(data, new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());

            if (root.containsKey("size")) {
                backpack.setSize(((Number) root.get("size")).intValue());
            }

            Map<String, Object> itemsData = (Map<String, Object>) root.get("items");
            if (itemsData != null) {
                for (Map.Entry<String, Object> entry : itemsData.entrySet()) {
                    int slot = Integer.parseInt(entry.getKey());
                    Map<String, String> itemData = (Map<String, String>) entry.getValue();

                    // 使用 NBTUtil 创建物品
                    ItemStack item = NBTUtil.createItemFromNBTData(
                            itemData.get("type"),
                            Integer.parseInt(itemData.getOrDefault("amount", "1")),
                            itemData.get("nbt")
                    );
                    if (item != null) {
                        backpack.setItem(slot, item);
                    }
                }
            }
        } catch (Exception e) {
            XiBackpack.getInstance().getLogger().severe("反序列化团队背包失败! " + e.getMessage());
            e.printStackTrace();
        }
        return backpack;
    }
}