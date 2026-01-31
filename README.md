# XiBackpack 云背包插件

XiBackpack 是一个功能强大的 Minecraft 服务器插件，为玩家提供云端背包存储功能。玩家的背包数据存储在数据库中，可以在不同服务器之间同步，并支持多种高级功能。

## 功能特性

### 核心功能
- **云背包存储**：玩家背包数据存储在数据库中，不会因服务器重启而丢失
- **跨服务器同步**：玩家可以在任何连接到同一数据库的服务器上访问自己的背包
- **分页界面**：支持大容量背包，通过分页界面进行管理
- **经济系统集成**：通过 Vault 插件集成服务器经济系统，支持背包升级

### 高级功能
- **背包升级**：玩家可以消耗金币升级背包容量（支持段式费用和权限控制）
- **数据备份**：支持手动创建和恢复背包备份
- **团队背包**：允许多个玩家共享同一个背包（实时同步更新）
- **多语言支持**：支持中文和英文界面
- **权限控制**：细粒度的权限控制，确保插件安全使用
- **冷却时间**：防止玩家频繁打开背包造成服务器压力
- **异步处理**：数据库操作异步执行，避免阻塞主线程
- **加载优化**：背包加载过程优化，减少玩家等待时间

## 命令列表

### 基本命令
- `/backpack` 或 `/bp` 或 `/bag` - 打开个人云背包
- `/xibackpack open` 或 `/xbp open` 或 `/cloudpack open` - 打开个人云背包
- `/xibackpack upgrade` 或 `/xbp upgrade` 或 `/cloudpack upgrade` - 升级背包容量
- `/xibackpack help` 或 `/xbp help` 或 `/cloudpack help` - 显示帮助信息

### 团队背包命令
- `/xibackpack team create <名称>` - 创建团队背包（需要权限：xibackpack.team.create）
- `/xibackpack team open <ID>` - 打开指定团队背包
- `/xibackpack team addmember <背包ID> <玩家名>` - 添加成员到团队背包（待实现）
- `/xibackpack team removemember <背包ID> <玩家名>` - 从团队背包移除成员（待实现）
- `/xibackpack team list` - 列出可访问的团队背包（待实现）

### 备份命令（需要管理员权限）
- `/xibackpack backup create` - 创建背包备份
- `/xibackpack backup restore <ID>` - 恢复指定备份

## 权限节点

### 基本权限
- `xibackpack.use` - 允许使用基本背包功能（默认：所有玩家）
- `xibackpack.admin` - 允许使用备份功能和全局管理权限（默认：操作员）
- `xibackpack.bypass.cooldown` - 允许绕过背包冷却时间（默认：操作员）
- `xibackpack.team.create` - 允许创建团队背包（默认：所有玩家）

### 团队背包特定权限
每个团队背包都有独立的权限节点，格式为：`xibackpack.team.<背包名称>.admin`

- `xibackpack.team.<背包名称>.admin` - 允许管理特定团队背包的成员和内容（默认：无）

**示例**：
- 如果你创建了一个名为 "生存小队" 的团队背包，那么对应的管理员权限是：`xibackpack.team.生存小队.admin`
- 如果你创建了一个名为 "建筑团队" 的团队背包，那么对应的管理员权限是：`xibackpack.team.建筑团队.admin`

**权限继承**：
- 背包所有者自动拥有该背包的管理员权限
- 全局管理员 (`xibackpack.admin`) 自动拥有所有团队背包的管理员权限

### 权限说明
| 权限节点 | 描述 | 默认值 |
|---------|------|--------|
| xibackpack.use | 允许使用基本背包功能 | 所有玩家 |
| xibackpack.admin | 全局管理权限 | 操作员 |
| xibackpack.bypass.cooldown | 绕过背包冷却时间 | 操作员 |
| xibackpack.team.create | 创建团队背包 | 所有玩家 |
| xibackpack.team.<背包名称>.admin | 特定团队背包的管理员权限 | 无 |

## 配置文件

### config.yml
```yaml
# 数据库配置
database:
  type: "sqlite" # 数据库类型（sqlite, mysql, postgresql, mongodb）
  host: "localhost" # 数据库主机（SQLite忽略）
  port: 3306 # 数据库端口（SQLite忽略）
  database: "xibackpack" # 数据库名称（SQLite为文件名）
  username: "" # 用户名（SQLite忽略）
  password: "" # 密码（SQLite忽略）
  # 连接池配置
  max-pool-size: 10
  min-idle: 2
  connection-timeout: 30000
  idle-timeout: 600000
  max-lifetime: 1800000

# 背包配置
backpack:
  size: 27 # 初始背包大小
  name: "§e§l云上背包"
  upgrade-cost: 1000 # 默认升级费用
  # 按段设置升级费用，例如36表示从36格开始到下一个设定点之间每9格的费用
  upgrade-costs:
    27: 1000   # 从27格开始到下一个设定点之间每9格的费用
    36: 1500   # 从36格开始到下一个设定点之间每9格的费用
    45: 2000   # 从45格开始到下一个设定点之间每9格的费用
    54: 2500   # 从54格开始到下一个设定点之间每9格的费用
    90: 3000   # 从90格开始到下一个设定点之间每9格的费用
    180: 5000  # 从180格开始到下一个设定点之间每9格的费用
    270: 8000  # 从270格开始到下一个设定点之间每9格的费用
    360: 10000 # 从360格开始到下一个设定点之间每9格的费用
  # 自定义每个容量级别所需的权限
  size-permissions:
    27: "xibackpack.upgrade.36"   # 从27格升级到36格需要权限
    36: "xibackpack.upgrade.45"   # 从36格升级到45格需要权限
    45: "xibackpack.upgrade.54"   # 从45格升级到54格需要权限
  # 冷却时间设置（毫秒）
  cooldown: 1000
  # 备份设置
  backup:
    max-count: 10 # 最大备份数量

# 团队背包配置
team-backpack:
  create-cost: 5000 # 创建团队背包所需费用
  default-size: 27 # 团队背包默认大小
  max-members: 10 # 团队背包最大成员数
```

## 更新日志

### v1.2.0（最新版本）
- 添加背包和团队背包的异步加载功能，显著改善用户体验
- 实现加载界面，防止玩家在数据加载过程中进行操作
- 优化数据库访问性能，减少主线程阻塞
- 改进背包界面交互体验
- 修复了团队背包覆盖个人背包的BUG

### v1.1.0
- 添加团队背包功能，支持多人共享背包
- 实现实时同步机制，防止多人操作时的数据不一致问题
- 改进背包升级系统，支持段式费用和权限控制
- 设置最大背包容量限制（10页，共450格）
- 添加创建团队背包的费用配置

### v1.0.2
- 修复一些小bug
- 优化数据库连接池配置

### v1.0.1
- 添加背包冷却时间机制
- 优化背包界面交互体验

### v1.0.0
- 初始版本发布
- 实现基本云背包功能
- 添加分页界面和背包升级功能
- 集成经济系统和数据持久化

## 许可证

本项目采用 MIT 许可证，详情请见 [LICENSE](LICENSE) 文件。

## 支持与反馈

如有任何问题或建议，请通过以下方式联系我们：
- 提交 GitHub Issue
- 联系插件开发者