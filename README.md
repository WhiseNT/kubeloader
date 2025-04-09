# KUBELOADER - KubeJS 内容包加载器使用指南

## 一、概述

KubeLoader 是 KubeJS 的附属模组，当前支持 Forge 1.20.1。它提供了"内容包"(ContentPack)机制，用于组织和加载 KubeJS 脚本及资源集合。

## 二、安装与配置

### 1. 内容包存放位置

- **文件夹形式**：`kubejs/contentpacks/` 目录下
  - 游戏启动时会自动创建目录（如不存在）
  - 支持文件夹或 ZIP 压缩包格式

- **模组内置**：打包在模组资源的 `contentpacks/` 目录下

## 三、内容包结构规范

### 1. 文件夹类型结构

```
[命名空间]/  ← 必须使用唯一的命名空间名称
    ├── server_scripts/    # 服务端脚本
    ├── client_scripts/    # 客户端脚本
    ├── startup_scripts/   # 启动脚本
    ├── assets/            # 资源文件
    ├── data/              # 数据文件
    └── contentpack.json   # PackMetaData文件（必需）
```

### 2. ZIP压缩包类型结构

```
[自定义名称].zip
    └── [命名空间]/        # 必须包含且只能包含一个命名空间目录
            ├── ...        # 同上文件夹结构
            └── contentpack.json
```
### 3. 模组类型结构

```
Resources
    ├── assets/
    ├── data/
    └── contentpacks/        # 在模组Resource文件夹下
            ├── server_scripts/    # 服务端脚本
            ├── client_scripts/    # 客户端脚本
            ├── startup_scripts/   # 启动脚本
            └── contentpack.json 
```

## 四、PackMetaData文件配置

### 1. 基础字段

```json
{
  "id": "包标识符",
  "name": "显示名称",
  "description": "描述信息",
  "version": "版本号",
  "authors": ["作者1", "作者2"]
}
```

- `id`：内容包唯一标识（小写字母、数字、下划线）
- `version`：推荐使用语义化版本（如 1.0.0）

### 2. 依赖管理

```json
"dependencies": [
  {
    "type": "依赖类型",
    "id": "依赖目标ID",
    "versionRange": "版本范围",
    "reason": "说明文字（可选）"
  }
]
```

#### 依赖类型说明表

| 类型 | 说明 | 加载行为 |
|------|------|---------|
| REQUIRED | 必需依赖 | 缺失则阻止加载 |
| OPTIONAL | 可选依赖 | 缺失仍可运行 |
| RECOMMENDED | 推荐依赖 | 缺失仍可运行 |
| DISCOURAGED | 不推荐依赖 | 输出警告 |
| INCOMPATIBLE | 不兼容依赖 | 存在则阻止加载 |
| MOD | 模组依赖 | 需要对应模组前置 |

#### 版本范围语法

| 语法 | 示例 | 说明 |
|------|------|------|
| 精确匹配 | [1.0.0] | 必须1.0.0版本 |
| 范围匹配 | [1.0.0,2.0.0) | 1.0.0(含)到2.0.0(不含) |
| 最低版本 | [1.0.0,) | 1.0.0及以上 |
| 任意版本 | * | 不限制版本 |

### 3. 完整示例

```json
{
  "id": "rpg_expansion",
  "name": "RPG扩展包",
  "description": "添加魔法武器和怪物",
  "version": "1.2.0",
  "authors": ["开发者A"],
  "dependencies": [
    {
      "type": "REQUIRED",
      "id": "kubejs",
      "versionRange": "[6.0.0,)"
    },
    {
      "type": "RECOMMENDED",
      "id": "jei",
      "versionRange": "[10.0.0]",
      "reason": "提供物品查看支持"
    }
  ]
}
```

## 五、JavaScript功能

### 1. 内容包间通信

通过以下字段实现：
- `startupField`：启动时通信
- `serverField`：服务端通信
- `clientField`：客户端通信

可在Field中访问PackMetaData数据

### 2. KubeJS增强功能

#### NBT物品注册
```javascript
event.create("nbt_item", 'nbt').nbt({custom_data: true})
```

#### 自定义弓注册
```javascript
event.create('magic_bow','bow')
    .callSuper("onCustomArrow", false)
    .onCustomArrow(proj => {
        proj.setNoGravity(true)
        return proj
    })
```

## 六、注意事项

1. **命名空间规范**：
   - 必须唯一且不能使用"kubejs"
   - 建议使用小写字母和下划线

2. **ZIP打包要求**：
   - 根目录必须且只能包含一个命名空间文件夹
   - 不能有其他无关文件

3. **资源路径**：
   - 所有资源自动存放在`pack_resources`文件夹

## 七、问题排查

若加载失败请检查：
1. 命名空间是否符合规范
2. ZIP压缩包结构是否正确
3. PackMetaData文件是否存在且格式正确
4. 依赖是否满足要求
