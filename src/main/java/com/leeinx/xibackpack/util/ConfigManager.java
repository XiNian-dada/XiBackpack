package com.leeinx.xibackpack.util;

import com.leeinx.xibackpack.main.XiBackpack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * 配置管理工具类
 * 提供类型安全的配置访问、配置验证和动态配置加载功能
 */
public class ConfigManager {

    private static XiBackpack plugin;
    private static FileConfiguration config;
    private static final Map<String, Object> cachedValues = new HashMap<>();
    private static final Map<String, Supplier<?>> defaultSuppliers = new HashMap<>();
    private static ExecutorService fileWatcherExecutor;
    private static WatchService watchService;

    /**
     * 初始化配置管理器
     * @param plugin 插件实例
     */
    public static void initialize(XiBackpack plugin) {
        ConfigManager.plugin = plugin;
        ConfigManager.config = plugin.getConfig();
        registerDefaultSuppliers();
        validateConfig();
        cacheConfigValues();
        startFileWatcher();
    }

    /**
     * 启动配置文件监视器
     */
    private static void startFileWatcher() {
        if (plugin == null) return;
        
        try {
            watchService = FileSystems.getDefault().newWatchService();
            File configFile = plugin.getDataFolder();
            if (configFile.exists() && configFile.isDirectory()) {
                Path configPath = configFile.toPath();
                configPath.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE
                );
                
                fileWatcherExecutor = Executors.newSingleThreadExecutor();
                fileWatcherExecutor.submit(() -> {
                    while (!Thread.interrupted()) {
                        try {
                            WatchKey key = watchService.take();
                            for (WatchEvent<?> event : key.pollEvents()) {
                                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                                    continue;
                                }
                                
                                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                                Path fileName = ev.context();
                                if (fileName.toString().equals("config.yml")) {
                                    LogManager.info("检测到配置文件变化，正在重新加载...");
                                    reloadConfig();
                                }
                            }
                            key.reset();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Exception e) {
                            LogManager.warning("配置文件监视器错误: %s", e.getMessage());
                        }
                    }
                });
                
                LogManager.info("配置文件监视器已启动");
            }
        } catch (Exception e) {
            LogManager.warning("启动配置文件监视器失败: %s", e.getMessage());
        }
    }

    /**
     * 关闭配置文件监视器
     */
    public static void shutdown() {
        if (fileWatcherExecutor != null) {
            fileWatcherExecutor.shutdownNow();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (Exception e) {
                LogManager.warning("关闭配置文件监视器失败: %s", e.getMessage());
            }
        }
    }

    /**
     * 注册默认值供应商
     */
    private static void registerDefaultSuppliers() {
        defaultSuppliers.put("database.type", () -> "mysql");
        defaultSuppliers.put("database.host", () -> "localhost");
        defaultSuppliers.put("database.port", () -> 3306);
        defaultSuppliers.put("database.database", () -> "xibackpack");
        defaultSuppliers.put("backpack.size", () -> 27);
        defaultSuppliers.put("backpack.cooldown", () -> 1000L);
        defaultSuppliers.put("backpack.backup.max-count", () -> 10);
        defaultSuppliers.put("language", () -> "zh_cn");
        defaultSuppliers.put("debug", () -> false);
    }

    /**
     * 验证配置文件
     */
    public static void validateConfig() {
        if (config == null) return;

        // 验证数据库配置
        String dbType = getString("database.type");
        if (!dbType.matches("mysql|postgresql|mongodb|sqlite")) {
            LogManager.warning("数据库类型配置无效: %s，使用默认值 sqlite", dbType);
            set("database.type", "sqlite");
        }

        // 验证背包配置
        int backpackSize = getInt("backpack.size");
        if (backpackSize <= 0) {
            LogManager.warning("背包大小配置无效: %d，使用默认值 27", backpackSize);
            set("backpack.size", 27);
        }

        // 验证冷却时间配置
        long cooldown = getLong("backpack.cooldown");
        if (cooldown < 0) {
            LogManager.warning("冷却时间配置无效: %d，使用默认值 1000", cooldown);
            set("backpack.cooldown", 1000L);
        }

        // 验证备份数量配置
        int maxBackups = getInt("backpack.backup.max-count");
        if (maxBackups <= 0) {
            LogManager.warning("最大备份数量配置无效: %d，使用默认值 10", maxBackups);
            set("backpack.backup.max-count", 10);
        }

        // 验证经验升级配置
        boolean expUpgradeEnabled = getBoolean("backpack.exp-upgrade.enabled", true);
        if (!expUpgradeEnabled) {
            LogManager.info("经验升级功能已禁用");
        }

        // 确保经验升级费用配置存在
        if (config.getConfigurationSection("backpack.exp-upgrade.exp-costs") == null) {
            LogManager.warning("经验升级费用配置不存在，创建默认配置");
            ConfigurationSection expCosts = config.createSection("backpack.exp-upgrade.exp-costs");
            expCosts.set("27", 1000);
            expCosts.set("36", 1500);
            expCosts.set("45", 2000);
            saveConfig();
        }
    }

    /**
     * 缓存配置值
     */
    private static void cacheConfigValues() {
        cachedValues.clear();
        cachedValues.put("database.type", getString("database.type"));
        cachedValues.put("database.host", getString("database.host"));
        cachedValues.put("database.port", getInt("database.port"));
        cachedValues.put("database.database", getString("database.database"));
        cachedValues.put("backpack.size", getInt("backpack.size"));
        cachedValues.put("backpack.cooldown", getLong("backpack.cooldown"));
        cachedValues.put("language", getString("language"));
        cachedValues.put("debug", getBoolean("debug"));
    }

    /**
     * 重新加载配置文件
     */
    public static void reloadConfig() {
        if (plugin == null) return;
        
        plugin.reloadConfig();
        config = plugin.getConfig();
        validateConfig();
        cacheConfigValues();
        LogManager.info("配置文件已重新加载");
    }

    /**
     * 保存配置文件
     */
    public static void saveConfig() {
        if (plugin == null) return;
        
        plugin.saveConfig();
        LogManager.info("配置文件已保存");
    }

    /**
     * 获取字符串配置值
     * @param path 配置路径
     * @return 配置值
     */
    public static String getString(String path) {
        return getString(path, null);
    }

    /**
     * 获取字符串配置值
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static String getString(String path, String defaultValue) {
        // 测试环境优先使用系统属性
        String testProperty = "test." + path;
        if (System.getProperty(testProperty) != null) {
            return System.getProperty(testProperty);
        }
        
        if (cachedValues.containsKey(path)) {
            return (String) cachedValues.get(path);
        }

        String value = config.getString(path);
        if (value == null) {
            if (defaultValue != null) {
                return defaultValue;
            }
            if (defaultSuppliers.containsKey(path)) {
                return (String) defaultSuppliers.get(path).get();
            }
            return null;
        }
        return value;
    }

    /**
     * 获取整数配置值
     * @param path 配置路径
     * @return 配置值
     */
    public static int getInt(String path) {
        return getInt(path, 0);
    }

    /**
     * 获取整数配置值
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static int getInt(String path, int defaultValue) {
        // 测试环境优先使用系统属性
        String testProperty = "test." + path;
        if (System.getProperty(testProperty) != null) {
            try {
                return Integer.parseInt(System.getProperty(testProperty));
            } catch (NumberFormatException e) {
                LogManager.warning("测试环境系统属性转换失败: %s = %s", testProperty, System.getProperty(testProperty));
            }
        }
        
        if (cachedValues.containsKey(path)) {
            Object value = cachedValues.get(path);
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }

        if (config.contains(path)) {
            return config.getInt(path);
        }
        if (defaultSuppliers.containsKey(path)) {
            Object value = defaultSuppliers.get(path).get();
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return defaultValue;
    }

    /**
     * 获取长整型配置值
     * @param path 配置路径
     * @return 配置值
     */
    public static long getLong(String path) {
        return getLong(path, 0L);
    }

    /**
     * 获取长整型配置值
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static long getLong(String path, long defaultValue) {
        // 测试环境优先使用系统属性
        String testProperty = "test." + path;
        if (System.getProperty(testProperty) != null) {
            try {
                return Long.parseLong(System.getProperty(testProperty));
            } catch (NumberFormatException e) {
                LogManager.warning("测试环境系统属性转换失败: %s = %s", testProperty, System.getProperty(testProperty));
            }
        }
        
        if (cachedValues.containsKey(path)) {
            Object value = cachedValues.get(path);
            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        }

        if (config.contains(path)) {
            return config.getLong(path);
        }
        if (defaultSuppliers.containsKey(path)) {
            Object value = defaultSuppliers.get(path).get();
            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        }
        return defaultValue;
    }

    /**
     * 获取布尔型配置值
     * @param path 配置路径
     * @return 配置值
     */
    public static boolean getBoolean(String path) {
        return getBoolean(path, false);
    }

    /**
     * 获取布尔型配置值
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static boolean getBoolean(String path, boolean defaultValue) {
        if (cachedValues.containsKey(path)) {
            Object value = cachedValues.get(path);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        }

        if (config.contains(path)) {
            return config.getBoolean(path);
        }
        if (defaultSuppliers.containsKey(path)) {
            Object value = defaultSuppliers.get(path).get();
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        }
        return defaultValue;
    }

    /**
     * 获取配置节
     * @param path 配置路径
     * @return 配置节
     */
    public static ConfigurationSection getConfigurationSection(String path) {
        return config.getConfigurationSection(path);
    }

    /**
     * 设置配置值
     * @param path 配置路径
     * @param value 配置值
     */
    public static void set(String path, Object value) {
        if (config == null) return;
        
        config.set(path, value);
        cachedValues.put(path, value);
        LogManager.debug("配置已更新: %s = %s", path, value);
    }

    /**
     * 检查配置是否包含指定路径
     * @param path 配置路径
     * @return 是否包含
     */
    public static boolean contains(String path) {
        return config.contains(path);
    }

    /**
     * 获取配置文件实例
     * @return 配置文件实例
     */
    public static FileConfiguration getConfig() {
        return config;
    }
}
