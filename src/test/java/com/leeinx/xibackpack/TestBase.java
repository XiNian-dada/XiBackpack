package com.leeinx.xibackpack;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

public abstract class TestBase {
    protected ServerMock server;
    protected XiBackpack plugin;
    protected PlayerMock player1;
    protected PlayerMock player2;
    protected PlayerMock adminPlayer;

    @BeforeEach
    public void setUp() {
        // 初始化模拟环境
        MockBukkit.mock();
        // 创建模拟服务器
        server = MockBukkit.getMock();
        // 加载插件
        plugin = MockBukkit.load(XiBackpack.class);
        // 创建测试玩家
        player1 = server.addPlayer("Player1");
        player2 = server.addPlayer("Player2");
        adminPlayer = server.addPlayer("Admin");
        // 给管理员玩家添加op权限
        adminPlayer.setOp(true);
        // 给测试玩家添加必要的权限
        player1.addAttachment(plugin, "xibackpack.use", true);
        player1.addAttachment(plugin, "xibackpack.team.create", true);
        player2.addAttachment(plugin, "xibackpack.use", true);
        player2.addAttachment(plugin, "xibackpack.team.create", true);
    }

    @AfterEach
    public void tearDown() {
        // 卸载插件
        MockBukkit.unmock();
    }

    /**
     * 创建测试物品
     */
    protected ItemStack createTestItem(Material material, int amount, String name) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 模拟延迟执行
     */
    protected void waitForAsyncTasks() {
        // 在测试中等待异步任务完成，添加try-catch避免没有任务时的NullPointerException
        try {
            server.getScheduler().performOneTick();
        } catch (NullPointerException e) {
            // 没有异步任务时忽略异常
        }
    }
}