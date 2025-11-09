# XiBackpack 云背包插件

XiBackpack 是一个功能强大的 Minecraft 服务器插件，为玩家提供云端背包存储功能。玩家的背包数据存储在数据库中，可以在不同服务器之间同步，并支持多种高级功能。

## 功能特性

### 核心功能
- **云背包存储**：玩家背包数据存储在数据库中，不会因服务器重启而丢失
- **跨服务器同步**：玩家可以在任何连接到同一数据库的服务器上访问自己的背包
- **分页界面**：支持大容量背包，通过分页界面进行管理
- **经济系统集成**：通过 Vault 插件集成服务器经济系统，支持背包升级

### 高级功能
- **背包升级**：玩家可以消耗金币升级背包容量
- **数据备份**：支持手动创建和恢复背包备份
- **多语言支持**：支持中文和英文界面
- **权限控制**：细粒度的权限控制，确保插件安全使用
- **冷却时间**：防止玩家频繁打开背包造成服务器压力
- **异步处理**：数据库操作异步执行，避免阻塞主线程

## 命令列表

### 基本命令
- `/backpack` 或 `/bp` 或 `/bag` - 打开个人云背包
- `/xibackpack open` 或 `/xbp open` 或 `/cloudpack open` - 打开个人云背包
- `/xibackpack upgrade` 或 `/xbp upgrade` 或 `/cloudpack upgrade` - 升级背包容量
- `/xibackpack help` 或 `/xbp help` 或 `/cloudpack help` - 显示帮助信息

### 备份命令（需要管理员权限）
- `/xibackpack backup create` - 创建背包备份
- `/xibackpack backup restore <ID>` - 恢复指定备份

## 权限节点

- `xibackpack.use` - 允许使用基本背包功能（默认：所有玩家）
- `xibackpack.admin` - 允许使用备份功能（默认：操作员）
- `xibackpack.bypass.cooldown` - 允许绕过背包冷却时间（默认：操作员）

## 配置文件

### config.yml
```yaml
# 数据库配置
database:
  type: "mysql" # 数据库类型（mysql, postgresql, mongodb）
  host: "localhost" # 数据库主机
  port: 3306 # 数据库端口
  database: "xibackpack" # 数据库名称
  username: "" # 用户名
  password: "" # 密码
  # 连接池配置
  max-pool-size: 10
  min-idle: 2
  connection-timeout: 30000
  idle-timeout: 600000
  max-lifetime: 1800000

# 背包配置
backpack:
  size: 27 # 初始背包大小
  name: "§e§l云上背包" # 背包界面标题
  upgrade-cost: 1000 # 每次升级花费的金币数量
  # 冷却时间设置（毫秒）
  cooldown: 1000
  # 备份设置
  backup:
    max-count: 10 # 最大备份数量

# NBT相关配置
nbt:
  enabled: true
  allow-mod-items: true

# 语言设置
language: "zh" # 支持 "zh" (中文) 或 "en" (英文)
```

### messages.yml
包含所有用户界面消息的配置文件，支持多语言自定义。

## 安装说明

1. 将 XiBackpack.jar 文件放入服务器的 `plugins` 目录
2. 首次运行会自动生成配置文件
3. 根据需要修改 `config.yml` 中的数据库配置
4. 重启服务器使配置生效
5. 确保已安装 Vault 插件以使用背包升级功能

## 依赖插件

- **Vault**：用于经济系统集成（可选，但推荐）

## 数据库支持

- MySQL（推荐）
- PostgreSQL
- MongoDB（实验性支持）

## 性能优化

- 使用 HikariCP 连接池管理数据库连接
- 异步执行数据库操作，避免阻塞主线程
- 冷却时间机制防止滥用
- 自动备份管理，限制备份数量防止存储膨胀

## 故障排除

### 常见问题

1. **无法打开背包**：检查玩家是否拥有 `xibackpack.use` 权限
2. **背包升级功能不可用**：确保已安装并正确配置 Vault 插件
3. **数据库连接失败**：检查数据库配置和网络连接
4. **中文显示异常**：确保服务器支持中文字符集

### 日志查看
插件会在服务器日志中记录详细的操作信息和错误信息，便于问题排查。

## 开发者信息

### 构建要求
- Java 8 或更高版本
- Maven 3.6 或更高版本

### 编译方法
```bash
mvn clean package
```

## 更新日志

### v1.0
- 初始版本发布
- 实现基本云背包功能
- 添加分页界面和背包升级功能
- 集成经济系统
- 实现数据持久化

### v1.1（增强版）
- 添加多语言支持
- 实现背包备份功能
- 增强插件健壮性
- 添加权限控制系统
- 实现冷却时间机制
- 优化数据库操作性能

## 许可证

本项目采用 MIT 许可证，详情请见 [LICENSE](LICENSE) 文件。

## 支持与反馈

如有任何问题或建议，请通过以下方式联系我们：
- 提交 GitHub Issue
- 联系插件开发者