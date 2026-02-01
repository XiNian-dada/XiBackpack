package com.leeinx.xibackpack;

import com.leeinx.xibackpack.main.XiBackpack;
import com.leeinx.xibackpack.handler.BackpackManager;
import com.leeinx.xibackpack.handler.TeamBackpackManager;
import com.leeinx.xibackpack.handler.DatabaseManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PerformanceTest extends TestBase {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int TEST_ITERATIONS = 10;
    private static final int CONCURRENT_THREADS = 5;

    @Test
    public void testBackpackOpenPerformance() {
        plugin.getLogger().info("开始测试背包打开性能...");

        // 预热
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            plugin.getBackpackManager().openBackpack(player1);
        }

        // 性能测试
        long totalTime = 0;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long startTime = System.currentTimeMillis();
            plugin.getBackpackManager().openBackpack(player1);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            totalTime += duration;
            plugin.getLogger().info("背包打开时间 " + (i + 1) + ": " + duration + "ms");
        }

        long averageTime = totalTime / TEST_ITERATIONS;
        plugin.getLogger().info("背包打开平均时间: " + averageTime + "ms");
        assert averageTime < 500 : "背包打开时间过长: " + averageTime + "ms";
    }

    @Test
    public void testDatabaseOperationPerformance() {
        plugin.getLogger().info("开始测试数据库操作性能...");

        // 预热
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            plugin.getBackpackManager().saveBackpack(plugin.getBackpackManager().getBackpack(player1));
        }

        // 性能测试
        long totalTime = 0;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long startTime = System.currentTimeMillis();
            plugin.getBackpackManager().saveBackpack(plugin.getBackpackManager().getBackpack(player1));
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            totalTime += duration;
            plugin.getLogger().info("数据库操作时间 " + (i + 1) + ": " + duration + "ms");
        }

        long averageTime = totalTime / TEST_ITERATIONS;
        plugin.getLogger().info("数据库操作平均时间: " + averageTime + "ms");
        assert averageTime < 200 : "数据库操作时间过长: " + averageTime + "ms";
    }

    @Test
    public void testTeamBackpackSyncPerformance() {
        plugin.getLogger().info("开始测试团队背包同步性能...");

        // 创建团队背包
        String teamId = plugin.getTeamBackpackManager().createBackpack(player1, "test_team");

        // 预热
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            plugin.getTeamBackpackManager().openBackpack(player1, teamId);
        }

        // 性能测试
        long totalTime = 0;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long startTime = System.currentTimeMillis();
            plugin.getTeamBackpackManager().openBackpack(player1, teamId);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            totalTime += duration;
            plugin.getLogger().info("团队背包同步时间 " + (i + 1) + ": " + duration + "ms");
        }

        long averageTime = totalTime / TEST_ITERATIONS;
        plugin.getLogger().info("团队背包同步平均时间: " + averageTime + "ms");
        assert averageTime < 300 : "团队背包同步时间过长: " + averageTime + "ms";
    }

    @Test
    public void testLargeDataPerformance() {
        plugin.getLogger().info("开始测试大量数据性能...");

        // 填充大量物品
        for (int i = 0; i < 36; i++) {
            ItemStack item = createTestItem(Material.STONE, 64, "测试物品 " + i);
            player1.getInventory().setItem(i, item);
        }

        // 预热
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            plugin.getBackpackManager().saveBackpack(plugin.getBackpackManager().getBackpack(player1));
        }

        // 性能测试
        long totalTime = 0;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long startTime = System.currentTimeMillis();
            plugin.getBackpackManager().saveBackpack(plugin.getBackpackManager().getBackpack(player1));
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            totalTime += duration;
            plugin.getLogger().info("大量数据操作时间 " + (i + 1) + ": " + duration + "ms");
        }

        long averageTime = totalTime / TEST_ITERATIONS;
        plugin.getLogger().info("大量数据操作平均时间: " + averageTime + "ms");
        assert averageTime < 500 : "大量数据操作时间过长: " + averageTime + "ms";
    }

    @Test
    public void testConcurrentPerformance() {
        plugin.getLogger().info("开始测试并发性能...");

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_THREADS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // 每个线程执行多次操作
                    for (int j = 0; j < 5; j++) {
                        plugin.getBackpackManager().openBackpack(player1);
                        Thread.sleep(100); // 模拟玩家操作间隔
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        plugin.getLogger().info("并发操作总时间: " + totalTime + "ms");
        plugin.getLogger().info("每线程平均时间: " + (totalTime / CONCURRENT_THREADS) + "ms");

        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assert totalTime < 5000 : "并发操作时间过长: " + totalTime + "ms";
    }

    @Test
    public void testMemoryUsage() {
        plugin.getLogger().info("开始测试内存使用情况...");

        // 测试前内存
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();

        // 执行操作
        for (int i = 0; i < 10; i++) {
            plugin.getBackpackManager().openBackpack(player1);
            plugin.getBackpackManager().saveBackpack(plugin.getBackpackManager().getBackpack(player1));
        }

        // 测试后内存
        runtime.gc();
        long afterMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = afterMemory - beforeMemory;

        plugin.getLogger().info("内存使用情况: " + (memoryUsed / 1024 / 1024) + "MB");
        assert memoryUsed < 10 * 1024 * 1024 : "内存使用过多: " + (memoryUsed / 1024 / 1024) + "MB";
    }
}
