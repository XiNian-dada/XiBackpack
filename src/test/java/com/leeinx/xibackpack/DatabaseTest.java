package com.leeinx.xibackpack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.leeinx.xibackpack.handler.DatabaseManager;
import com.leeinx.xibackpack.backpack.PlayerBackpack;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class DatabaseTest extends TestBase {

    @Test
    public void testDatabaseInitialization() {
        // 测试数据库初始化
        DatabaseManager databaseManager = plugin.getDatabaseManager();
        assertNotNull(databaseManager, "数据库管理器应该初始化成功");
    }

    @Test
    public void testPlayerBackpackWithItemsPersistence() {
        // 测试玩家背包物品的持久化
        PlayerBackpack backpack = plugin.getBackpackManager().getBackpack(player1);
        UUID playerUUID = player1.getUniqueId();
        
        // 向背包中添加物品
        ItemStack item1 = new ItemStack(Material.DIAMOND, 5);
        ItemStack item2 = new ItemStack(Material.GOLD_INGOT, 10);
        backpack.setItem(0, item1);
        backpack.setItem(1, item2);
        
        // 保存背包数据到数据库
        plugin.getBackpackManager().saveBackpack(backpack);
        // 等待异步操作完成
        waitForAsyncTasks();
        
        // 清除缓存，模拟重新加载
        plugin.getBackpackManager().getBackpack(player1); // 先获取一次确保在缓存中
        // 这里我们通过直接从数据库加载来测试，而不是依赖缓存
        String serializedData = plugin.getDatabaseManager().loadPlayerBackpack(playerUUID);
        assertNotNull(serializedData, "从数据库加载的背包数据不应为null");
        
        // 从数据库数据反序列化背包
        PlayerBackpack reloadedBackpack = PlayerBackpack.deserialize(serializedData, playerUUID);
        assertNotNull(reloadedBackpack, "背包应该从数据库数据反序列化成功");
        
        // 验证物品是否正确加载
        ItemStack loadedItem1 = reloadedBackpack.getItem(0);
        ItemStack loadedItem2 = reloadedBackpack.getItem(1);
        assertNotNull(loadedItem1, "第一个物品应该加载成功");
        assertNotNull(loadedItem2, "第二个物品应该加载成功");
        assertEquals(Material.DIAMOND, loadedItem1.getType(), "第一个物品类型应该正确");
        assertEquals(5, loadedItem1.getAmount(), "第一个物品数量应该正确");
        assertEquals(Material.GOLD_INGOT, loadedItem2.getType(), "第二个物品类型应该正确");
        assertEquals(10, loadedItem2.getAmount(), "第二个物品数量应该正确");
    }

    @Test
    public void testBackpackUpgradePersistence() {
        // 测试背包升级后的数据持久化
        PlayerBackpack backpack = plugin.getBackpackManager().getBackpack(player1);
        UUID playerUUID = player1.getUniqueId();
        int initialSize = backpack.getSize();
        
        // 向背包中添加物品
        ItemStack item = new ItemStack(Material.DIAMOND, 5);
        backpack.setItem(0, item);
        
        // 保存初始状态
        plugin.getBackpackManager().saveBackpack(backpack);
        waitForAsyncTasks();
        
        // 手动设置背包大小来模拟升级
        int newSize = initialSize + 9; // 增加9格
        backpack.setSize(newSize);
        
        // 保存升级后的状态
        plugin.getBackpackManager().saveBackpack(backpack);
        waitForAsyncTasks();
        
        // 从数据库加载验证
        String serializedData = plugin.getDatabaseManager().loadPlayerBackpack(playerUUID);
        assertNotNull(serializedData, "从数据库加载的背包数据不应为null");
        
        // 反序列化验证
        PlayerBackpack reloadedBackpack = PlayerBackpack.deserialize(serializedData, playerUUID);
        assertNotNull(reloadedBackpack, "背包应该从数据库数据反序列化成功");
        assertEquals(newSize, reloadedBackpack.getSize(), "加载的背包大小应该与升级后的大小一致");
        
        // 验证物品是否仍然存在
        ItemStack loadedItem = reloadedBackpack.getItem(0);
        assertNotNull(loadedItem, "物品应该在升级后仍然存在");
        assertEquals(Material.DIAMOND, loadedItem.getType(), "物品类型应该正确");
        assertEquals(5, loadedItem.getAmount(), "物品数量应该正确");
    }

    @Test
    public void testMultiplePlayersDataIsolation() {
        // 测试多个玩家数据的隔离性
        UUID player1UUID = player1.getUniqueId();
        UUID player2UUID = player2.getUniqueId();
        
        // Player1的背包
        PlayerBackpack backpack1 = plugin.getBackpackManager().getBackpack(player1);
        ItemStack item1 = new ItemStack(Material.DIAMOND, 5);
        backpack1.setItem(0, item1);
        plugin.getBackpackManager().saveBackpack(backpack1);
        waitForAsyncTasks();
        
        // Player2的背包
        PlayerBackpack backpack2 = plugin.getBackpackManager().getBackpack(player2);
        ItemStack item2 = new ItemStack(Material.GOLD_INGOT, 10);
        backpack2.setItem(0, item2);
        plugin.getBackpackManager().saveBackpack(backpack2);
        waitForAsyncTasks();
        
        // 从数据库直接加载验证
        String data1 = plugin.getDatabaseManager().loadPlayerBackpack(player1UUID);
        String data2 = plugin.getDatabaseManager().loadPlayerBackpack(player2UUID);
        assertNotNull(data1, "Player1的背包数据应该加载成功");
        assertNotNull(data2, "Player2的背包数据应该加载成功");
        
        // 反序列化验证
        PlayerBackpack reloadedBackpack1 = PlayerBackpack.deserialize(data1, player1UUID);
        PlayerBackpack reloadedBackpack2 = PlayerBackpack.deserialize(data2, player2UUID);
        
        // 验证Player1的物品
        ItemStack loadedItem1 = reloadedBackpack1.getItem(0);
        assertNotNull(loadedItem1, "Player1的物品应该加载成功");
        assertEquals(Material.DIAMOND, loadedItem1.getType(), "Player1的物品类型应该正确");
        
        // 验证Player2的物品
        ItemStack loadedItem2 = reloadedBackpack2.getItem(0);
        assertNotNull(loadedItem2, "Player2的物品应该加载成功");
        assertEquals(Material.GOLD_INGOT, loadedItem2.getType(), "Player2的物品类型应该正确");
        
        // 验证数据隔离
        assertNotEquals(loadedItem1.getType(), loadedItem2.getType(), "两个玩家的物品应该不同，确保数据隔离");
    }

    @Test
    public void testPlayerBackpackBackupAndRestore() {
        // 测试玩家背包备份和恢复功能
        PlayerBackpack backpack = plugin.getBackpackManager().getBackpack(player1);
        UUID playerUUID = player1.getUniqueId();
        
        // 向背包中添加物品
        ItemStack item = new ItemStack(Material.DIAMOND, 5);
        backpack.setItem(0, item);
        plugin.getBackpackManager().saveBackpack(backpack);
        waitForAsyncTasks();
        
        // 手动创建备份
        String backupId = "test-backup-" + System.currentTimeMillis();
        String originalData = backpack.serialize();
        boolean backupResult = plugin.getDatabaseManager().savePlayerBackpackBackup(playerUUID, backupId, originalData);
        assertTrue(backupResult, "背包备份应该创建成功");
        
        // 修改背包内容
        backpack.setItem(0, new ItemStack(Material.GOLD_INGOT, 10));
        plugin.getBackpackManager().saveBackpack(backpack);
        waitForAsyncTasks();
        
        // 从备份恢复
        String backupData = plugin.getDatabaseManager().loadPlayerBackpackBackup(playerUUID, backupId);
        assertNotNull(backupData, "备份数据应该加载成功");
        
        // 手动从备份数据创建背包
        PlayerBackpack restoredBackpack = PlayerBackpack.deserialize(backupData, playerUUID);
        assertNotNull(restoredBackpack, "背包应该从备份数据恢复成功");
        
        // 验证物品是否恢复
        ItemStack restoredItem = restoredBackpack.getItem(0);
        assertNotNull(restoredItem, "物品应该从备份中恢复成功");
        assertEquals(Material.DIAMOND, restoredItem.getType(), "恢复的物品类型应该正确");
        assertEquals(5, restoredItem.getAmount(), "恢复的物品数量应该正确");
    }

    @Test
    public void testDatabaseClose() {
        // 测试数据库连接关闭
        DatabaseManager databaseManager = plugin.getDatabaseManager();
        // 关闭数据库连接
        databaseManager.close();
        // 测试关闭后是否能重新初始化（在实际使用中，插件会处理重新初始化）
        // 这里主要测试关闭方法不会抛出异常
    }
}
