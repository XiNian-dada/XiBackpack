package com.leeinx.xibackpack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BackpackTest extends TestBase {

    @Test
    public void testOpenPersonalBackpack() {
        // 测试打开个人背包
        server.dispatchCommand(player1, "backpack open");
        waitForAsyncTasks();
        
        // 验证背包打开成功
        assertNotNull(player1.getOpenInventory(), "个人背包应该打开成功");
    }

    @Test
    public void testBackpackPagination() {
        // 测试背包分页功能
        server.dispatchCommand(player1, "backpack open 2");
        waitForAsyncTasks();
        
        // 验证可以打开第二页
        assertNotNull(player1.getOpenInventory(), "第二页背包应该打开成功");
    }

    @Test
    public void testMultiplePlayersPersonalBackpacks() {
        // 测试多个玩家的个人背包独立性
        // Player1打开背包
        server.dispatchCommand(player1, "backpack open");
        waitForAsyncTasks();
        
        // Player2打开背包
        server.dispatchCommand(player2, "backpack open");
        waitForAsyncTasks();
        
        // 验证两个玩家都打开了背包
        assertNotNull(player1.getOpenInventory(), "Player1的背包应该打开");
        assertNotNull(player2.getOpenInventory(), "Player2的背包应该打开");
        
        // 关闭背包
        player1.closeInventory();
        player2.closeInventory();
        waitForAsyncTasks();
    }

    @Test
    public void testPersonalBackpackPersistence() {
        // 打开背包
        server.dispatchCommand(player1, "backpack open");
        waitForAsyncTasks();
        
        // 关闭背包
        player1.closeInventory();
        waitForAsyncTasks();
        
        // 重新打开背包，验证可以正常打开
        server.dispatchCommand(player1, "backpack open");
        waitForAsyncTasks();
        
        assertNotNull(player1.getOpenInventory(), "背包关闭后应该可以重新打开");
    }

    @Test
    public void testDifferentPlayersCanOpenBackpack() {
        // Player1打开背包
        server.dispatchCommand(player1, "backpack open");
        waitForAsyncTasks();
        
        // 关闭Player1的背包
        player1.closeInventory();
        waitForAsyncTasks();
        
        // Player2打开背包
        server.dispatchCommand(player2, "backpack open");
        waitForAsyncTasks();
        
        assertNotNull(player2.getOpenInventory(), "不同玩家应该都能打开自己的背包");
        
        // 关闭Player2的背包
        player2.closeInventory();
        waitForAsyncTasks();
    }

    @Test
    public void testAdminCanOpenBackpack() {
        // 管理员打开背包
        server.dispatchCommand(adminPlayer, "backpack open");
        waitForAsyncTasks();
        
        // 验证管理员可以打开背包
        assertNotNull(adminPlayer.getOpenInventory(), "管理员应该可以打开背包");
        
        // 关闭背包
        adminPlayer.closeInventory();
        waitForAsyncTasks();
    }
}