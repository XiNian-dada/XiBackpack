package com.leeinx.xibackpack.util;

import com.leeinx.xibackpack.main.XiBackpack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Date;
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
     * @throws IllegalArgumentException 当plugin为null时抛出
     */
    public static void initialize(XiBackpack plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        ExceptionHandler.plugin = plugin;
        LogManager.info("异常处理器初始化成功");
    }

    /**
     * 处理命令执行过程中的异常
     * @param sender 命令发送者
     * @param action 操作描述
     * @param e 异常对象
     * @throws IllegalArgumentException 当sender或action为null时抛出
     */
    public static void handleCommandException(CommandSender sender, String action, Exception e) {
        if (sender == null) {
            throw new IllegalArgumentException("Sender cannot be null");
        }
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
        
        logException(action, e);
        
        String errorMessage = plugin.getMessage("error.command_failed", "§c执行命令时发生错误，请联系管理员");
        
        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.sendMessage(errorMessage);
            
            // 如果是管理员，发送详细错误信息
            if (player.hasPermission("xibackpack.admin")) {
                player.sendMessage("§7错误详情: " + e.getMessage());
                player.sendMessage("§7错误时间: " + new Date());
            }
        } else {
            sender.sendMessage(errorMessage);
            sender.sendMessage("错误详情: " + e.getMessage());
            sender.sendMessage("错误时间: " + new Date());
        }
    }

    /**
     * 处理背包操作过程中的异常
     * @param player 玩家对象
     * @param action 操作描述
     * @param e 异常对象
     * @throws IllegalArgumentException 当player或action为null时抛出
     */
    public static void handleBackpackException(Player player, String action, Exception e) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
        
        logException(action, e);
        
        String errorMessage = plugin.getMessage("error.backpack_operation_failed", "§c背包操作失败，请联系管理员");
        player.sendMessage(errorMessage);
        
        // 如果是管理员，发送详细错误信息
        if (player.hasPermission("xibackpack.admin")) {
            player.sendMessage("§7错误详情: " + e.getMessage());
            player.sendMessage("§7错误时间: " + new Date());
        }
    }

    /**
     * 处理数据库操作过程中的异常
     * @param action 操作描述
     * @param e 异常对象
     * @return 是否处理成功
     * @throws IllegalArgumentException 当action为null时抛出
     */
    public static boolean handleDatabaseException(String action, Exception e) {
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
        
        logException(action, e);
        return false;
    }

    /**
     * 处理异步任务中的异常
     * @param action 操作描述
     * @param e 异常对象
     * @throws IllegalArgumentException 当action为null时抛出
     */
    public static void handleAsyncException(String action, Exception e) {
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
        
        logException(action, e);
    }

    /**
     * 处理备份操作过程中的异常
     * @param player 玩家对象
     * @param action 操作描述
     * @param e 异常对象
     * @throws IllegalArgumentException 当player或action为null时抛出
     */
    public static void handleBackupException(Player player, String action, Exception e) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
        
        logException(action, e);
        
        String errorMessage = plugin.getMessage("error.backup_operation_failed", "§c备份操作失败，请联系管理员");
        player.sendMessage(errorMessage);
        
        // 如果是管理员，发送详细错误信息
        if (player.hasPermission("xibackpack.admin")) {
            player.sendMessage("§7错误详情: " + e.getMessage());
            player.sendMessage("§7错误时间: " + new Date());
        }
    }

    /**
     * 记录异常信息
     * @param action 操作描述
     * @param e 异常对象
     * @throws IllegalArgumentException 当action为null时抛出
     */
    private static void logException(String action, Exception e) {
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
        if (e == null) {
            throw new IllegalArgumentException("Exception cannot be null");
        }
        
        if (plugin == null) {
            System.err.println("[XiBackpack] ExceptionHandler not initialized: " + action);
            e.printStackTrace();
            return;
        }
        
        String logMessage = "操作失败: " + action;
        LogManager.severe(logMessage, e);
        plugin.getLogger().log(Level.SEVERE, logMessage, e);
    }

    /**
     * 安全执行可能抛出异常的操作（用于同步操作）
     * @param action 操作描述
     * @param task 要执行的任务
     * @param <T> 返回类型
     * @return 任务执行结果，失败返回null
     * @throws IllegalArgumentException 当action或task为null时抛出
     */
    public static <T> T safelyExecute(String action, SafeTask<T> task) {
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        
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
     * @throws IllegalArgumentException 当action或task为null时抛出
     */
    public static void safelyExecuteAsync(String action, Runnable task) {
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        
        if (plugin == null || !plugin.isEnabled()) {
            LogManager.warning("插件未初始化或已禁用，无法执行异步任务: " + action);
            return;
        }
        
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
