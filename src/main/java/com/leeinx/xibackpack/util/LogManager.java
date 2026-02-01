package com.leeinx.xibackpack.util;

import com.leeinx.xibackpack.main.XiBackpack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.*;

/**
 * 日志管理工具类
 * 提供结构化日志、分级日志和玩家消息管理功能
 */
public class LogManager {

    private static XiBackpack plugin;
    private static final Map<String, Integer> logCounters = new HashMap<>();
    private static FileHandler fileHandler;
    private static Logger fileLogger;

    /**
     * 初始化日志管理器
     * @param plugin 插件实例
     */
    public static void initialize(XiBackpack plugin) {
        LogManager.plugin = plugin;
        initializeFileLogger();
    }

    /**
     * 初始化文件日志处理器
     */
    private static void initializeFileLogger() {
        if (plugin == null) return;

        boolean debugEnabled = plugin.getConfig().getBoolean("debug.enabled", false);
        if (!debugEnabled) return;

        String logFile = plugin.getConfig().getString("debug.log-file", "logs/xibackpack-debug.log");
        Path logPath = Paths.get(plugin.getDataFolder().getParent(), logFile);

        try {
            // 创建日志目录
            Files.createDirectories(logPath.getParent());

            // 配置文件处理器
            fileHandler = new FileHandler(logPath.toString(), 
                plugin.getConfig().getInt("debug.max-log-size", 10485760), 
                plugin.getConfig().getInt("debug.log-rotation", 5), 
                true);

            // 配置日志格式化器
            SimpleFormatter formatter = new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL [%2$s] %3$s %4$s%n",
                        System.currentTimeMillis(),
                        record.getLevel().getName(),
                        Thread.currentThread().getName(),
                        record.getMessage());
                }
            };
            fileHandler.setFormatter(formatter);

            // 创建文件日志记录器
            fileLogger = Logger.getLogger("XiBackpackFileLogger");
            fileLogger.setLevel(Level.parse(plugin.getConfig().getString("debug.log-level", "FINE")));
            fileLogger.addHandler(fileHandler);
            fileLogger.setUseParentHandlers(false);

            plugin.getLogger().info("文件日志已初始化: " + logPath);
        } catch (IOException e) {
            plugin.getLogger().severe("初始化文件日志失败: " + e.getMessage());
        }
    }

    /**
     * 关闭日志处理器
     */
    public static void shutdown() {
        if (fileHandler != null) {
            fileHandler.close();
        }
    }

    /**
     * 记录DEBUG级别的日志
     * @param message 日志消息
     * @param params 日志参数
     */
    public static void debug(String message, Object... params) {
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            log(Level.FINE, message, params);
        }
    }

    /**
     * 记录INFO级别的日志
     * @param message 日志消息
     * @param params 日志参数
     */
    public static void info(String message, Object... params) {
        log(Level.INFO, message, params);
    }

    /**
     * 记录WARNING级别的日志
     * @param message 日志消息
     * @param params 日志参数
     */
    public static void warning(String message, Object... params) {
        log(Level.WARNING, message, params);
    }

    /**
     * 记录SEVERE级别的日志
     * @param message 日志消息
     * @param params 日志参数
     */
    public static void severe(String message, Object... params) {
        log(Level.SEVERE, message, params);
    }

    /**
     * 记录指定级别的日志
     * @param level 日志级别
     * @param message 日志消息
     * @param params 日志参数
     */
    private static void log(Level level, String message, Object... params) {
        if (plugin == null) {
            System.out.println(String.format(message, params));
            return;
        }

        String formattedMessage = String.format(message, params);
        plugin.getLogger().log(level, formattedMessage);

        // 同时记录到文件
        if (fileLogger != null && fileLogger.isLoggable(level)) {
            fileLogger.log(level, formattedMessage);
        }
    }

    /**
     * 记录结构化日志
     * @param action 操作类型
     * @param details 详细信息
     * @param success 是否成功
     */
    public static void logAction(String action, Map<String, Object> details, boolean success) {
        StringBuilder message = new StringBuilder();
        message.append("[Action] ").append(action).append(" - " + (success ? "SUCCESS" : "FAILED"));

        if (details != null && !details.isEmpty()) {
            message.append(" | Details: ");
            details.forEach((key, value) -> {
                message.append(key).append("='").append(value).append("' ");
            });
        }

        log(success ? Level.INFO : Level.WARNING, message.toString());
        
        // 增加操作计数
        String counterKey = action + (success ? ".success" : ".failure");
        logCounters.put(counterKey, logCounters.getOrDefault(counterKey, 0) + 1);
    }

    /**
     * 向玩家发送消息
     * @param player 玩家对象
     * @param messageKey 消息键
     * @param params 消息参数
     */
    public static void sendMessage(Player player, String messageKey, Object... params) {
        if (player == null) return;
        
        String message = plugin.getMessage(messageKey, "§c消息未找到: " + messageKey);
        
        if (params.length > 0) {
            message = String.format(message, params);
        }
        
        player.sendMessage(message);
    }

    /**
     * 向命令发送者发送消息
     * @param sender 命令发送者
     * @param messageKey 消息键
     * @param params 消息参数
     */
    public static void sendMessage(CommandSender sender, String messageKey, Object... params) {
        if (sender == null) return;
        
        String message = plugin.getMessage(messageKey, "§c消息未找到: " + messageKey);
        
        if (params.length > 0) {
            message = String.format(message, params);
        }
        
        sender.sendMessage(message);
    }

    /**
     * 获取操作统计信息
     * @return 操作统计映射
     */
    public static Map<String, Integer> getLogCounters() {
        return new HashMap<>(logCounters);
    }

    /**
     * 重置操作统计信息
     */
    public static void resetLogCounters() {
        logCounters.clear();
    }

    /**
     * 输出操作统计摘要
     */
    public static void logStatistics() {
        if (logCounters.isEmpty()) {
            info("没有操作统计数据");
            return;
        }

        info("=== 操作统计摘要 ===");
        logCounters.forEach((key, count) -> {
            info("%s: %d", key, count);
        });
        info("==================");
    }
}