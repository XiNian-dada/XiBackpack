package com.leeinx.xibackpack.task;

import com.leeinx.xibackpack.main.XiBackpack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务管理器
 * 负责管理插件中的各种定时任务，提供统一的任务管理接口
 */
public class TaskManager {
    private XiBackpack plugin;
    private Map<String, BukkitTask> tasks;

    /**
     * 构造函数
     * @param plugin 插件主类实例
     */
    public TaskManager(XiBackpack plugin) {
        this.plugin = plugin;
        this.tasks = new ConcurrentHashMap<>();
    }

    /**
     * 注册任务
     * @param taskId 任务ID
     * @param task 任务实例
     */
    public void registerTask(String taskId, BukkitTask task) {
        // 先取消已存在的同名任务
        cancelTask(taskId);
        // 注册新任务
        tasks.put(taskId, task);
    }

    /**
     * 取消任务
     * @param taskId 任务ID
     */
    public void cancelTask(String taskId) {
        BukkitTask task = tasks.remove(taskId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * 取消所有任务
     */
    public void cancelAllTasks() {
        for (Map.Entry<String, BukkitTask> entry : tasks.entrySet()) {
            BukkitTask task = entry.getValue();
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        tasks.clear();
    }

    /**
     * 检查任务是否存在
     * @param taskId 任务ID
     * @return 是否存在
     */
    public boolean hasTask(String taskId) {
        return tasks.containsKey(taskId);
    }

    /**
     * 获取任务
     * @param taskId 任务ID
     * @return 任务实例
     */
    public BukkitTask getTask(String taskId) {
        return tasks.get(taskId);
    }
}
