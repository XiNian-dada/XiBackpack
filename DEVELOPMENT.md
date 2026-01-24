# XiBackpack 开发文档

## 项目概述

XiBackpack 是一个功能强大的 Minecraft 服务器插件，为玩家提供云端背包存储功能。玩家的背包数据存储在数据库中，可以在不同服务器之间同步，并支持多种高级功能。

## 项目架构

### 核心组件

1. **XiBackpack** - 插件主类，负责初始化和管理各个组件
2. **BackpackManager** - 个人背包管理器，处理个人背包的加载、保存和界面
3. **TeamBackpackManager** - 团队背包管理器，处理团队背包的创建、管理和界面
4. **DatabaseManager** - 数据库管理器，负责与数据库交互
5. **CommandHandler** - 命令处理器，处理插件的各种命令
6. **NBTUtil** - NBT工具类，处理物品的NBT数据
7. **LoadingHolder** - 加载界面处理器，显示加载动画
8. **TeamBackpackManagementHolder** - 团队背包管理界面处理器

### 数据流向

1. 玩家发送命令或点击界面
2. 命令或事件被处理
3. 调用相应的管理器处理业务逻辑
4. 管理器与数据库交互
5. 数据库操作完成后更新界面或返回结果

## 代码结构

```
src/main/java/com/leeinx/xibackpack/
├── BackpackManager.java        # 个人背包管理器
├── CommandHandler.java         # 命令处理器
├── DatabaseManager.java        # 数据库管理器
├── LoadingHolder.java          # 加载界面处理器
├── NBTUtil.java                # NBT工具类
├── PlayerBackpack.java         # 个人背包数据类
├── TeamBackpack.java           # 团队背包数据类
├── TeamBackpackManagementHolder.java  # 团队背包管理界面
├── TeamBackpackManager.java    # 团队背包管理器
├── XiBackpack.java             # 插件主类
└── XiBackpackExpansion.java    # PlaceholderAPI扩展
```

## 权限系统

### 权限设计

XiBackpack 采用了分层的权限系统，包括基本权限和团队背包特定权限。

### 基本权限

- `xibackpack.use` - 允许使用基本背包功能
- `xibackpack.admin` - 全局管理权限
- `xibackpack.bypass.cooldown` - 绕过背包冷却时间
- `xibackpack.team.create` - 允许创建团队背包

### 团队背包特定权限

每个团队背包都有独立的权限节点，格式为：`xibackpack.team.<背包名称>.admin`

- **动态生成**：基于背包名称动态生成权限节点
- **权限继承**：背包所有者和全局管理员自动拥有该背包的管理员权限
- **细粒度控制**：每个背包的权限独立，便于管理

### 权限检查流程

1. 获取背包对象
2. 生成背包特定权限节点
3. 检查权限：
   - 如果是背包所有者，允许操作
   - 否则检查是否有背包特定权限
   - 否则检查是否有全局管理员权限
   - 否则拒绝操作

## 数据库设计

### 主要表结构

1. **player_backpacks** - 存储个人背包数据
   - `player_uuid` - 玩家UUID
   - `backpack_data` - 序列化的背包数据
   - `updated_at` - 更新时间

2. **team_backpacks** - 存储团队背包数据
   - `backpack_id` - 背包唯一标识符
   - `name` - 背包名称
   - `owner_uuid` - 所有者UUID
   - `backpack_data` - 序列化的背包数据
   - `created_at` - 创建时间
   - `updated_at` - 更新时间

3. **team_backpack_members** - 存储团队背包成员
   - `backpack_id` - 背包唯一标识符
   - `player_uuid` - 成员UUID
   - `role` - 成员角色（OWNER/MEMBER）
   - `joined_at` - 加入时间

4. **player_backpack_backups** - 存储个人背包备份
   - `player_uuid` - 玩家UUID
   - `backup_id` - 备份唯一标识符
   - `backpack_data` - 序列化的背包数据
   - `created_at` - 备份时间

## 开发流程

### 1. 环境搭建

1. 克隆代码仓库
2. 使用 IDE（如 IntelliJ IDEA）打开项目
3. 安装依赖（Maven 会自动处理）
4. 配置 Minecraft 开发环境

### 2. 代码规范

- 使用 Java 8 语法
- 遵循 Google Java 代码规范
- 每个方法都要有 Javadoc 注释
- 关键逻辑要有详细注释
- 异常处理要完善

### 3. 开发步骤

1. **需求分析**：明确功能需求和设计思路
2. **代码实现**：按照架构设计实现功能
3. **测试**：在本地服务器上测试功能
4. **代码审查**：检查代码质量和安全性
5. **提交**：提交代码到版本控制系统

### 4. 测试流程

1. **本地测试**：在本地 Minecraft 服务器上测试
2. **集成测试**：与其他插件一起测试
3. **性能测试**：测试高负载下的表现
4. **安全测试**：测试权限控制和数据安全

## 常见问题

### 权限问题

- **问题**：玩家无法访问团队背包
  **解决方案**：检查玩家是否有 `xibackpack.use` 权限和相应的团队背包权限

- **问题**：玩家无法管理团队背包
  **解决方案**：检查玩家是否有背包特定权限或全局管理员权限

### 数据库问题

- **问题**：数据库连接失败
  **解决方案**：检查配置文件中的数据库设置和数据库服务状态

- **问题**：背包数据丢失
  **解决方案**：检查数据库备份和恢复机制

## 扩展开发

### 添加新功能

1. 分析需求，确定所需的组件
2. 实现相应的类和方法
3. 更新配置文件和消息文件
4. 添加命令和权限
5. 编写测试用例

### 与其他插件集成

1. 了解目标插件的 API
2. 实现集成接口
3. 测试集成功能
4. 编写集成文档

## 版本管理

### 版本号规则

- **主版本号**：重大功能更新或架构变更
- **次版本号**：新增功能或重要改进
- **修订号**：bug修复或小改进

### 发布流程

1. 更新版本号
2. 更新 CHANGELOG
3. 编译和打包
4. 发布到插件平台
5. 编写发布说明

## 联系方式

如有问题或建议，请通过以下方式联系：

- GitHub Issues：[https://github.com/yourusername/XiBackpack/issues](https://github.com/yourusername/XiBackpack/issues)
- 邮件：your@email.com

## 贡献指南

欢迎提交 Pull Request 和 Issue，我们会尽快处理。

1. Fork 项目
2. 创建功能分支
3. 提交更改
4. 创建 Pull Request

## 许可证

本项目采用 MIT 许可证，详情请见 [LICENSE](LICENSE) 文件。
