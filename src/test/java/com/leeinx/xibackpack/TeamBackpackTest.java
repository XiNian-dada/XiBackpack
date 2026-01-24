package com.leeinx.xibackpack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TeamBackpackTest extends TestBase {

    @Test
    public void testCreateTeamBackpack() {
        // 测试创建团队背包
        server.dispatchCommand(player1, "backpack team create testbackpack");
        waitForAsyncTasks();
        
        // 验证背包创建成功
        assertTrue(plugin.getTeamBackpackManager().backpackExists("testbackpack"), "团队背包应该创建成功");
    }

    @Test
    public void testCreateTeamBackpackWithInvalidName() {
        // 测试使用非法名称创建团队背包
        server.dispatchCommand(player1, "backpack team create test-backpack");
        waitForAsyncTasks();
        
        // 验证背包创建失败
        assertFalse(plugin.getTeamBackpackManager().backpackExists("test-backpack"), "使用非法名称应该创建失败");
    }

    @Test
    public void testOpenTeamBackpack() {
        // 创建团队背包
        server.dispatchCommand(player1, "backpack team create testbackpack");
        waitForAsyncTasks();
        
        // 打开团队背包
        server.dispatchCommand(player1, "backpack team open testbackpack");
        waitForAsyncTasks();
        
        // 验证背包打开成功
        assertNotNull(player1.getOpenInventory(), "团队背包应该打开成功");
    }

    @Test
    public void testOwnerCanManageBackpack() {
        // 创建团队背包
        server.dispatchCommand(player1, "backpack team create testbackpack");
        waitForAsyncTasks();
        
        // 测试所有者可以删除背包
        server.dispatchCommand(player1, "backpack team delete testbackpack");
        waitForAsyncTasks();
        
        // 验证背包删除成功
        assertFalse(plugin.getTeamBackpackManager().backpackExists("testbackpack"), "所有者应该可以删除背包");
    }

    @Test
    public void testMultipleTeamBackpacks() {
        // 创建多个团队背包
        server.dispatchCommand(player1, "backpack team create backpack1");
        waitForAsyncTasks();
        
        server.dispatchCommand(player1, "backpack team create backpack2");
        waitForAsyncTasks();
        
        // 验证两个背包都存在
        assertTrue(plugin.getTeamBackpackManager().backpackExists("backpack1"), "第一个团队背包应该存在");
        assertTrue(plugin.getTeamBackpackManager().backpackExists("backpack2"), "第二个团队背包应该存在");
        
        // 验证背包数量
        assertEquals(2, plugin.getTeamBackpackManager().getAllBackpacks().size(), "应该有两个团队背包");
    }
}