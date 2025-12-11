package com.leeinx.xibackpack;

import com.leeinx.xibackpack.XiBackpack;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class DatabaseManager {
    private XiBackpack plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(XiBackpack plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            HikariConfig config = new HikariConfig();

            // 从配置文件读取数据库配置
            String dbType = plugin.getConfig().getString("database.type", "mysql");
            String host = plugin.getConfig().getString("database.host", "localhost");
            int port = plugin.getConfig().getInt("database.port", 3306);
            String database = plugin.getConfig().getString("database.database", "xibackpack");
            String username = plugin.getConfig().getString("database.username", "");
            String password = plugin.getConfig().getString("database.password", "");

            // 根据配置设置数据库连接信息
            switch (dbType.toLowerCase()) {
                case "mysql":
                    config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC");
                    break;
                case "postgresql":
                    config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
                    break;
                case "mongodb":
                    config.setJdbcUrl("mongodb://" + host + ":" + port + "/" + database);
                    break;
                default:
                    config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC");
            }
            
            config.setUsername(username);
            config.setPassword(password);

            // 连接池配置
            config.setMaximumPoolSize(plugin.getConfig().getInt("database.max-pool-size", 10));
            config.setMinimumIdle(plugin.getConfig().getInt("database.min-idle", 2));
            config.setConnectionTimeout(plugin.getConfig().getLong("database.connection-timeout", 30000));
            config.setIdleTimeout(plugin.getConfig().getLong("database.idle-timeout", 600000));
            config.setMaxLifetime(plugin.getConfig().getLong("database.max-lifetime", 1800000));

            // MySQL 特定配置
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");

            dataSource = new HikariDataSource(config);

            // 初始化数据库表
            initializeTables();

            plugin.getLogger().info(plugin.getMessage("database.init_success"));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getMessage("database.init_failed", "error", e.getMessage()), e);
        }
    }

    private void initializeTables() {
        Connection connection = null;
        try {
            connection = getConnection();
            
            // 创建背包表
            String createTableSQL = "CREATE TABLE IF NOT EXISTS player_backpacks (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "player_uuid VARCHAR(36) NOT NULL UNIQUE, " +
                    "backpack_data LONGTEXT, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ")";

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createTableSQL);
            }
            
            // 创建背包备份表
            String createBackupTableSQL = "CREATE TABLE IF NOT EXISTS player_backpack_backups (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "backup_id VARCHAR(100) NOT NULL, " +
                    "backpack_data LONGTEXT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE KEY unique_backup (player_uuid, backup_id)," +
                    "INDEX idx_player_uuid (player_uuid)," +
                    "INDEX idx_created_at (created_at)" +
                    ")";

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createBackupTableSQL);
            }

            plugin.getLogger().info(plugin.getMessage("database.table_init_success"));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getMessage("database.table_init_failed", "error", e.getMessage()), e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "关闭数据库连接时出错: " + e.getMessage(), e);
                }
            }
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("数据库连接池未初始化");
        }
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("数据库连接池已关闭");
        }
    }

    /**
     * 保存玩家背包数据到数据库
     * @param playerUUID 玩家UUID
     * @param backpackData 背包数据（JSON格式）
     * @return 是否保存成功
     */
    public boolean savePlayerBackpack(UUID playerUUID, String backpackData) {
        if (playerUUID == null || backpackData == null) {
            plugin.getLogger().warning("保存背包数据时参数为空: playerUUID=" + playerUUID + ", backpackData=" + (backpackData != null ? "length=" + backpackData.length() : "null"));
            return false;
        }
        
        Connection connection = null;
        try {
            connection = getConnection();
            String sql = "INSERT INTO player_backpacks (player_uuid, backpack_data) VALUES (?, ?) " +
                         "ON DUPLICATE KEY UPDATE backpack_data = VALUES(backpack_data), updated_at = CURRENT_TIMESTAMP";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, backpackData);
                stmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getMessage("database.save_failed", "error", e.getMessage()), e);
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "关闭数据库连接时出错: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 从数据库加载玩家背包数据
     * @param playerUUID 玩家UUID
     * @return 背包数据（JSON格式），如果不存在则返回null
     */
    public String loadPlayerBackpack(UUID playerUUID) {
        if (playerUUID == null) {
            plugin.getLogger().warning("加载背包数据时playerUUID为空");
            return null;
        }
        
        Connection connection = null;
        try {
            connection = getConnection();
            String sql = "SELECT backpack_data FROM player_backpacks WHERE player_uuid = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("backpack_data");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getMessage("database.load_failed", "error", e.getMessage()), e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "关闭数据库连接时出错: " + e.getMessage(), e);
                }
            }
        }
        return null;
    }
    
    /**
     * 保存玩家背包备份数据
     * @param playerUUID 玩家UUID
     * @param backupId 备份ID
     * @param backpackData 背包数据（JSON格式）
     * @return 是否保存成功
     */
    public boolean savePlayerBackpackBackup(UUID playerUUID, String backupId, String backpackData) {
        if (playerUUID == null || backupId == null || backpackData == null) {
            plugin.getLogger().warning("保存背包备份数据时参数为空: playerUUID=" + playerUUID + 
                                     ", backupId=" + backupId);
            return false;
        }
        
        Connection connection = null;
        try {
            connection = getConnection();
            
            // 检查备份数量限制
            if (getBackupCount(playerUUID) >= plugin.getConfig().getInt("backpack.backup.max-count", 10)) {
                // 删除最旧的备份
                deleteOldestBackup(playerUUID);
            }
            
            String sql = "INSERT INTO player_backpack_backups (player_uuid, backup_id, backpack_data) VALUES (?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE backpack_data = VALUES(backpack_data)";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, backupId);
                stmt.setString(3, backpackData);
                stmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "保存玩家背包备份数据失败: " + e.getMessage(), e);
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "关闭数据库连接时出错: " + e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * 从数据库加载玩家背包备份数据
     * @param playerUUID 玩家UUID
     * @param backupId 备份ID
     * @return 背包数据（JSON格式），如果不存在则返回null
     */
    public String loadPlayerBackpackBackup(UUID playerUUID, String backupId) {
        if (playerUUID == null || backupId == null) {
            plugin.getLogger().warning("加载背包备份数据时参数为空: playerUUID=" + playerUUID + ", backupId=" + backupId);
            return null;
        }
        
        Connection connection = null;
        try {
            connection = getConnection();
            String sql = "SELECT backpack_data FROM player_backpack_backups WHERE player_uuid = ? AND backup_id = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, backupId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("backpack_data");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "加载玩家背包备份数据失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "关闭数据库连接时出错: " + e.getMessage(), e);
                }
            }
        }
        return null;
    }
    
    /**
     * 获取玩家备份数量
     * @param playerUUID 玩家UUID
     * @return 备份数量
     */
    private int getBackupCount(UUID playerUUID) {
        if (playerUUID == null) {
            plugin.getLogger().warning("获取备份数量时playerUUID为空");
            return 0;
        }
        
        Connection connection = null;
        try {
            connection = getConnection();
            String sql = "SELECT COUNT(*) as count FROM player_backpack_backups WHERE player_uuid = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("count");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "获取玩家备份数量失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "关闭数据库连接时出错: " + e.getMessage(), e);
                }
            }
        }
        return 0;
    }
    
    /**
     * 删除最旧的备份
     * @param playerUUID 玩家UUID
     */
    private void deleteOldestBackup(UUID playerUUID) {
        if (playerUUID == null) {
            plugin.getLogger().warning("删除最旧备份时playerUUID为空");
            return;
        }
        
        Connection connection = null;
        try {
            connection = getConnection();
            String sql = "DELETE FROM player_backpack_backups WHERE player_uuid = ? ORDER BY created_at ASC LIMIT 1";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                int deleted = stmt.executeUpdate();
                if (deleted > 0) {
                    plugin.getLogger().info("已删除玩家 " + playerUUID + " 的最旧备份");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "删除最旧备份失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "关闭数据库连接时出错: " + e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * 获取玩家所有备份ID
     * @param playerUUID 玩家UUID
     * @return 备份ID列表
     */
    public List<String> getPlayerBackupIds(UUID playerUUID) {
        List<String> backupIds = new ArrayList<>();
        if (playerUUID == null) {
            plugin.getLogger().warning("获取玩家备份ID列表时playerUUID为空");
            return backupIds;
        }
        
        Connection connection = null;
        try {
            connection = getConnection();
            String sql = "SELECT backup_id FROM player_backpack_backups WHERE player_uuid = ? ORDER BY created_at DESC";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        backupIds.add(rs.getString("backup_id"));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "获取玩家备份ID列表失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "关闭数据库连接时出错: " + e.getMessage(), e);
                }
            }
        }
        return backupIds;
    }
}