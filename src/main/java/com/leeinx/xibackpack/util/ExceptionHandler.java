package com.leeinx.xibackpack.util;

import com.leeinx.xibackpack.main.XiBackpack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Level;

/**
 * 统一的异常处理机制
 * 提供标准化的异常捕获、日志记录和错误提示功能
 */
public class ExceptionHandler {

    private static XiBackpack plugin;

    /**
     * 初始化异常处理器
     * @param plugin 插件实例
     */
    public static void initialize(XiBackpack plugin) {
        ExceptionHandler.plugin = plugin;
    }

    /**
     * 处理命令执行过程中的异常
     * @param sender 命令发送者
     * @param action 操作描述
     * @param e 异常对象
     */
    public static void handleCommandException(CommandSender sender, String action, Exception e) {
        logException(action, e);
        
        String errorMessage = plugin.getMessage("error.command_failed", "§c执行命令时发生错误，请联系管理员");
        
        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.sendMessage(errorMessage);
            
            // 如果是管理员，发送详细错误信息
            if (player.hasPermission("xibackpack.admin")) {
                player.sendMessage("§7错误详情: " + e.getMessage());
            }
        } else {
            sender.sendMessage(errorMessage);
            sender.sendMessage("错误详情: " + e.getMessage());
        }
    }

    /**
     * 处理背包操作过程中的异常
     * @param player 玩家对象
     * @param action 操作描述
     * @param e 异常对象
     */
    public static void handleBackpackException(Player player, String action, Exception e) {
        logException(action, e);
        
        String errorMessage = plugin.getMessage("error.backpack_operation_failed", "§c背包操作失败，请联系管理员");
        player.sendMessage(errorMessage);
        
        // 如果是管理员，发送详细错误信息
        if (player.hasPermission("xibackpack.admin")) {
            player.sendMessage("§7错误详情: " + e.getMessage());
        }
    }

    /**
     * 处理数据库操作过程中的异常
     * @param action 操作描述
     * @param e 异常对象
     * @return 是否处理成功
     */
    public static boolean handleDatabaseException(String action, Exception e) {
        logException(action, e);
        return false;
    }

    /**
     * 处理异步任务中的异常
     * @param action 操作描述
     * @param e 异常对象
     */
    public static void handleAsyncException(String action, Exception e) {
        logException(action, e);
    }

    /**
     * 记录异常信息
     * @param action 操作描述
     * @param e 异常对象
     */
    private static void logException(String action, Exception e) {
        if (plugin == null) {
            System.err.println("ExceptionHandler not initialized: " + action);
            e.printStackTrace();
            return;
        }
        
        plugin.getLogger().log(Level.SEVERE, "操作失败: " + action, e);
    }

    /**
     * 安全执行可能抛出异常的操作（用于同步操作）
     * @param action 操作描述
     * @param task 要执行的任务
     * @param <T> 返回类型
     * @return 任务执行结果，失败返回null
     */
    public static <T> T safelyExecute(String action, SafeTask<T> task) {
        try {
            return task.execute();
        } catch (Exception e) {
            logException(action, e);
            return null;
        }
    }

    /**
     * 安全执行可能抛出异常的操作（用于异步操作）
     * @param action 操作描述
     * @param task 要执行的任务
     */
    public static void safelyExecuteAsync(String action, Runnable task) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    logException(action, e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * 安全任务接口
     * @param <T> 返回类型
     */
    @FunctionalInterface
    public interface SafeTask<T> {
        T execute() throws Exception;
    }
}
