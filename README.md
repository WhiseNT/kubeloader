# KUBELOADER - KubeJS 内容包加载器使用指南

## 零、测试中更新摘要

 - 去掉了 ContentPack 的 namespace，全部用 metadata 中的 id
 - 提供"type": "MOD" 被分离到了 DependencySource ，依赖模组与依赖内容包因此都可以使用 5 种依赖类型
     - "source": "PACK"：不填写时的默认值，表明这项依赖关系是对另一个ContentPack的关系
     - "source": "MOD"：表明这项依赖关系是对模组的依赖关系
 - contentpack.json 的读取提前，metadata有效成为ContentPack有效的先决条件，比加载ContentPack的内容更早
     - 对于每种 ContentPack ，其 contentpack.json 的路径都假定只有一处，预防扫描全部文件的性能问题
 - ZipContentPack 每次重载都会重新在路径里扫描，因此可以在游戏运行时加减 ContentPack
 - zip形式内容包现在需要把文件放在根目录。一个zip内含一个内容包是之前已经定下来的，所以除了少一层文件夹之外没有什么功能变化
     - namespace 先前是由文件夹名提供，现在标准化为 metadata 中的 id。




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
    "id": "依赖目标内容包ID",
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
      "id": "star_lib",
      "versionRange": "[6.0.0,)"
    },
    {
      "type": "RECOMMENDED",
      "id": "mystic_mobs",
      "versionRange": "[10.0.0]",
      "reason": "增加了新的怪物"
    }
  ]
}
```

## 五、JavaScript功能

### 1. 内容包间通信

通过以下字段实现：
`ContentPacks`
```JavaScript
ContentPacks.isLoaded(id) //确定某个id的ContentPack是否记载
ContentPacks.getMetaData(id) //获取某个id的ContentPack的元数据，没有则为null
ContentPacks.putShared(id, o) //将 o 放入“全局”数据，同脚本类型的ContentPack可以根据 id 获取
ContentPacks.getShared(id) //读取"全局"数据，本质上是同脚本类型的ContentPack先前放入的数据
ContentPacks.getAllSharedFor(scriptType)
ContentPacks.getShared(type, id) //跨脚本类型读取，但是不允许跨脚本类型写入
```
可在ContentPacks中访问PackMetaData数据，以及通过put和get信息来实现内容包之间的通信。

### 2. KubeJS增强功能

#### NBT物品注册
```javascript
event.create("nbt_item", 'nbt').nbt({custom_data: true})
```

#### 自定义弓注册
```javascript
event.create('magic_bow','bow')
    .callSuper("onCustomArrow", false) //阻止调用弓的super方法
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
