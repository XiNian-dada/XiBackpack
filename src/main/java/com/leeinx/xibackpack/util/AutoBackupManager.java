package com.leeinx.xibackpack.util;

import com.leeinx.xibackpack.main.XiBackpack;
import com.leeinx.xibackpack.backpack.PlayerBackpack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * 自动备份管理器
 * 负责处理背包的自动备份功能，包括定时备份和触发式备份
 */
public class AutoBackupManager {
    private XiBackpack plugin;
    private BukkitTask autoBackupTask;
    private boolean enabled;
    private int interval;
    private boolean notify;
    private boolean onQuitTrigger;
    private boolean onSaveTrigger;
    private ConcurrentMap<UUID, Long> lastBackupTime; // 记录每个玩家的最后备份时间

    /**
     * 构造函数，初始化自动备份管理器
     * @param plugin 插件主类实例
     */
    public AutoBackupManager(XiBackpack plugin) {
        this.plugin = plugin;
        this.lastBackupTime = new ConcurrentHashMap<>();
        loadConfig();
        initializeTasks();
    }

    /**
     * 加载配置
     */
    private void loadConfig() {
        this.enabled = ConfigManager.getBoolean("backpack.backup.auto-backup.enabled", true);
        this.interval = ConfigManager.getInt("backpack.backup.auto-backup.interval", 3600);
        this.notify = ConfigManager.getBoolean("backpack.backup.auto-backup.notify", true);
        this.onQuitTrigger = ConfigManager.getBoolean("backpack.backup.auto-backup.triggers.on-quit", true);
        this.onSaveTrigger = ConfigManager.getBoolean("backpack.backup.auto-backup.triggers.on-save", false);
    }

    /**
     * 初始化自动备份任务
     */
    private void initializeTasks() {
        if (enabled && interval > 0) {
            startAutoBackupTask();
        }
    }

    /**
     * 启动自动备份任务
     */
    private void startAutoBackupTask() {
        if (autoBackupTask != null) {
            autoBackupTask.cancel();
        }

        autoBackupTask = new BukkitRunnable() {
            @Override
            public void run() {
                performAutoBackup();
            }
        }.runTaskTimerAsynchronously(plugin, 0, interval * 20L); // 转换为ticks

        LogManager.info("自动备份任务已启动，间隔: " + interval + "秒");
    }

    /**
     * 执行自动备份
     */
    private void performAutoBackup() {
        LogManager.info("开始执行自动备份...");
        int backedUpCount = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                UUID playerUUID = player.getUniqueId();
                long currentTime = System.currentTimeMillis();
                long lastBackup = lastBackupTime.getOrDefault(playerUUID, 0L);

                // 检查是否需要备份（避免过于频繁的备份）
                if (currentTime - lastBackup > interval * 1000L) {
                    if (createBackupForPlayer(player)) {
                        lastBackupTime.put(playerUUID, currentTime);
                        backedUpCount++;

                        // 发送备份完成通知
                        if (notify) {
                            player.sendMessage("§a您的背包已自动备份完成");
                        }
                    }
                }
            } catch (Exception e) {
                LogManager.severe("自动备份玩家 " + player.getName() + " 的背包时出错", e);
            }
        }

        LogManager.info("自动备份完成，已备份 " + backedUpCount + " 个玩家背包");
    }

    /**
     * 为指定玩家创建备份
     * @param player 玩家
     * @return 是否备份成功
     */
    public boolean createBackupForPlayer(Player player) {
        try {
            PlayerBackpack backpack = plugin.getBackpackManager().getBackpack(player);
            if (backpack == null) {
                LogManager.warning("无法为玩家 " + player.getName() + " 创建备份，背包未加载");
                return false;
            }

            // 生成备份ID
            String backupId = "auto_backup_" + System.currentTimeMillis() + "_" + player.getUniqueId().toString().substring(0, 8);

            // 序列化背包数据
            String backpackData = backpack.serialize();

            // 保存到数据库
            boolean success = plugin.getDatabaseManager().savePlayerBackpackBackup(
                    player.getUniqueId(),
                    backupId,
                    backpackData
            );

            if (success) {
                LogManager.info("成功为玩家 " + player.getName() + " 创建自动备份: " + backupId);
            } else {
                LogManager.warning("为玩家 " + player.getName() + " 创建自动备份失败");
            }

            return success;
        } catch (Exception e) {
            LogManager.severe("为玩家 " + player.getName() + " 创建备份时出错", e);
            return false;
        }
    }

    /**
     * 处理玩家退出时的自动备份
     * @param player 玩家
     */
    public void handlePlayerQuit(Player player) {
        if (enabled && onQuitTrigger) {
            // 异步执行备份操作，避免阻塞主线程和连接竞争
            new BukkitRunnable() {
                @Override
                public void run() {
                    createBackupForPlayer(player);
                }
            }.runTaskAsynchronously(plugin);
        }
    }

    /**
     * 处理手动保存时的自动备份
     * @param player 玩家
     */
    public void handlePlayerSave(Player player) {
        if (enabled && onSaveTrigger) {
            createBackupForPlayer(player);
        }
    }

    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        loadConfig();
        if (enabled && interval > 0) {
            startAutoBackupTask();
        } else if (autoBackupTask != null) {
            autoBackupTask.cancel();
            autoBackupTask = null;
            LogManager.info("自动备份任务已停止");
        }
    }

    /**
     * 关闭自动备份管理器
     */
    public void shutdown() {
        if (autoBackupTask != null) {
            autoBackupTask.cancel();
            autoBackupTask = null;
        }
        LogManager.info("自动备份管理器已关闭");
    }

    /**
     * 获取是否启用自动备份
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取自动备份间隔
     * @return 间隔时间（秒）
     */
    public int getInterval() {
        return interval;
    }

    /**
     * 获取是否发送通知
     * @return 是否发送通知
     */
    public boolean isNotify() {
        return notify;
    }
}
