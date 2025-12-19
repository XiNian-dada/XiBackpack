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

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getOwner() {
        return owner;
    }

    public Set<UUID> getMembers() {
        return new HashSet<>(members);
    }

    public boolean isMember(UUID playerUUID) {
        return members.contains(playerUUID);
    }

    public boolean isOwner(UUID playerUUID) {
        return owner.equals(playerUUID);
    }

    public void addMember(UUID playerUUID) {
        members.add(playerUUID);
    }

    public void removeMember(UUID playerUUID) {
        if (!owner.equals(playerUUID)) { // 不能移除所有者
            members.remove(playerUUID);
        }
    }

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

    public ItemStack getItem(int slot) {
        if (slot < 0) {
            throw new IllegalArgumentException("Slot index cannot be negative, got: " + slot);
        }

        return items.get(slot);
    }

    public Map<Integer, ItemStack> getItems() {
        return new HashMap<>(items);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Backpack size must be positive, got: " + size);
        }

        this.size = size;

        // 清理超出新大小的物品（仅当新大小更小时）
        items.entrySet().removeIf(entry -> entry.getKey() >= size);
    }
}