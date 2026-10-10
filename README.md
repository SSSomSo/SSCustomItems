# SSCustomItems

一个 Paper 26.2 插件：

* 从 `plugins/SSCustomItems/items/物品名.json` 读取配置，新增自定义物品，通过 `/ci give <命名空间:物品名>` 给予；

* 支持给物品绑定各种触发动作（切换主副手、穿戴、点击等）；

* 提供 `damagelook` / `firelook` / `damageln` / `fireln` / `killlook` / `killln` 六个精准战斗指令，支持自定义伤害类型与死亡消息；

* 支持护甲等级系统：低级剑 / 箭矢打高级护甲逐级减伤，抵消伤害转为护甲耐久损耗；

* 支持自定义方块门锁：指定自定义物品作为钥匙，红石默认无法开门；

* 支持 `/ssci reload` 在线热重载，所有补全项带中文注释，体验贴合原版；

* 作为 **ComponentsAPI 的前置插件**：自定义方块/生物模型、基岩版实体模型等客户端内容由 SSCustomItems 提供资源、ComponentsAPI 负责组件基础设施与资源包托管推送。

作者：Mc\_SomSo　许可证：[CC BY-SA 4.0](LICENSE)

## 指令总览

| 指令 | 说明 | 权限 |
| --- | --- | --- |
| `/ci give <命名空间:物品名>` | 给予 1 个对应自定义物品（背包满时掉落在脚下） | `sscustomitems.give`（默认 OP） |
| `/damagelook <伤害类型> [最大距离] [散布范围] [弹道下坠] [伤害大小]` | 精准伤害视角所指实体 | `sscustomitems.combat`（默认 OP） |
| `/firelook <燃烧秒数> [最大距离] [散布范围] [弹道下坠]` | 精准点燃视角所指实体 | 同上 |
| `/damageln <伤害类型> <半径> [散布范围] [弹道下坠] [伤害大小]` | 伤害视角所指点位半径内的实体 | 同上 |
| `/fireln <燃烧秒数> <半径> [散布范围] [弹道下坠]` | 点燃视角所指点位半径内的实体 | 同上 |
| `/cb place <命名空间:id>` | 在视角所指方块面上放置自定义方块 | `sscustomitems.block`（默认 OP） |
| `/cb give <命名空间:id>` | 给予带标记的载体物品（可像原版方块一样放置） | 同上 |
| `/killlook [最大距离] [散布范围] [弹道下坠]` | 直接清除视角所指实体（等同原版 /kill 结算） | `sscustomitems.combat`（默认 OP） |
| `/killln <半径> [散布范围] [弹道下坠]` | 直接清除视角所指点位半径内的实体 | 同上 |
| `/sscustomitems reload <目标>`（别名 `/ssci`） | 在线热重载，无需重启 | `sscustomitems.reload`（默认 OP） |

`/ci give`、`/damagelook`、`/damageln` 的伤害类型参数，以及 `/cb` 的方块 id 参数均支持 Tab 补全。

**所有补全项都带有中文注释**：在补全列表中悬停某一项即可看到参数含义、默认值与基准值；数值参数位会直接给出默认值示例，连续按 Tab 即可快速套用，体验与原版指令一致。

## 在线热重载

```
/ssci reload all          # 重载配置 + 物品 + 伤害类型 + 方块
/ssci reload items        # 仅重载 items/ 自定义物品（含 handed 触发动作）
/ssci reload damagetypes  # 仅重载 damage_types/
/ssci reload blocks       # 仅重载 blocks/ 自定义方块（会关闭已打开的自定义菜单）
/ssci reload config       # 仅重载 config.yml（检测/tick 间隔立即生效）
```

热重载说明：

* 新增、删除、修改 json 后执行对应重载即可生效，无需重启服务器。
* 方块重载会先保存并关闭玩家正在浏览的自定义菜单，再按新定义重建；已放置的方块与位置索引不受影响。
* 物品重载后，已在玩家背包中的旧物品保持原样（其 handed 动作会按新配置执行）。
* 更新 **jar 文件本身**仍需完整重启（或 PlugManX 加载新 jar），热重载只针对配置内容。

## 物品配置格式

在 `plugins/SSCustomItems/items/` 下新建任意 `xxx.json` 文件（文件名即物品名，仅用于识别）：

```json
{
  "vanilla_id": "minecraft:paper",
  "id": "sscustomitems:ruby",
  "enable_model": true,
  "components": {
    "minecraft:custom_name": { "text": "红宝石", "color": "red" },
    "minecraft:enchantment_glint_override": true,
    "minecraft:max_stack_size": 16
  }
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `vanilla_id` | 是 | 作为载体的原版物品 id，可省略 `minecraft:` 前缀 |
| `id` | 是 | 自定义物品 id，格式 `命名空间:名称`，`/ci give` 时使用 |
| `enable_model` | 否 | 为 `true` 时，给予的物品自动附带 `item_model` 数据组件，值就是 `id` |
| `components` | 否 | 原版物品数据组件，写法与原版 `components` 一致 |
| `handed` | 否 | 物品触发动作，见下 |

实际给予物品时，等价于原版 SNBT：

```
{count:1b, id:"minecraft:paper", components:{minecraft:item_model:"sscustomitems:ruby", ...}}
```

## 物品触发动作（handed）

在 json 中加入 `handed` 对象，每个触发对应一个**命令数组**（支持多行，按顺序执行）：

```json
"handed": {
  "offhand":    ["/tellraw @a \"切到了副手\""],
  "inoffhand":  ["/say 正在副手中"],
  "mainhand":   ["/say 切到了主手"],
  "inmainhand": ["/say 正在主手中"],
  "leftclick":  ["/say 左键"],
  "rightclick": ["/say 右键"],
  "wear": {
    "helmet":     ["/say 戴上了头盔"],
    "chestplate": ["/say 穿上了胸甲"],
    "leggings":   ["/say 穿上了护腿"],
    "boots":      ["/say 穿上了靴子"]
  },
  "inhelmet":     ["/say 正戴着头盔"],
  "inchestplate": ["/say 正穿着胸甲"],
  "inleggings":   ["/say 正穿着护腿"],
  "inboots":      ["/say 正穿着靴子"]
}
```

| 触发 key | 时机 |
| --- | --- |
| `offhand` / `mainhand` | 物品**切换到**副手 / 主手的瞬间触发一次 |
| `inoffhand` / `inmainhand` | 物品**只要在**副手 / 主手中，就按间隔反复触发 |
| `leftclick` / `rightclick` | 手持物品时左键 / 右键触发（右键支持副手物品） |
| `wear.helmet` / `wear.chestplate` / `wear.leggings` / `wear.boots` | 穿戴到对应装备槽的瞬间触发一次 |
| `inhelmet` / `inchestplate` / `inleggings` / `inboots` | 只要穿戴在对应槽位，就按间隔反复触发 |

说明：

* 命令以控制台身份执行，可带可不带开头的 `/`；支持占位符 `%player%`（玩家名）。
* 玩家上线时已在身上的物品不会触发 "切换 / 穿戴"，只有之后发生状态变化才触发。
* 也支持点号扁平写法，例如直接在根上写 `"handed.offhand": ["..."]`、`"handed.wear.helmet": ["..."]`。
* 检测周期与持续触发间隔在 `plugins/SSCustomItems/config.yml`：`check-interval-ticks`（默认 5）、`repeat-interval-ticks`（默认 20）。

## 护甲等级系统

在自定义物品（护甲、剑、弓）的 `components.minecraft:custom_data` 中写入：

```json
"minecraft:custom_data": {
  "armor_type": "plugin",
  "armor_lvl": 3
}
```

规则（仅对带 `armor_type:"plugin"` 的物品生效，**不影响原版装备**）：

1. **减伤**：武器攻击插件护甲时，武器等级每比护甲等级低 1 级，最终伤害降低 **20%**；低 5 级及以上伤害为 0。武器等级 ≥ 护甲等级时全额伤害。
* 护甲等级取四件装备中**未损坏且等级最高的那件**，减伤只结算给提供最高等级的那件。
* **无 `custom_data` 的近战武器（含原版剑）按默认 3 级结算**；插件剑攻击**普通护甲**、或对方没有可生效的插件护甲时，一律采用**原版伤害**。
2. **耐久转换**：被抵消的伤害（原伤害 − 最终伤害，**向上取整**）转化为护甲耐久损耗。
3. **最大耐久 15%**：插件护甲的最大耐久变为原版的 15%。
4. **归零不消失、不再减伤**：插件护甲耐久到 0 时不会像原版一样直接消失，而是停留在 0 耐久继续穿在身上；但该件**不再提供减伤效果**（身上其他未损坏的插件护甲照常提供），用铁砧修复后恢复。
5. **铁砧修复**：每用 1 个材料恢复 25% 原版最大耐久，但最大耐久永久降低 2（材料数量的两倍）。

箭矢的等级来自发射它的**插件弓**；非插件弓射出的箭不参与等级结算（按原版伤害）。

## 战斗指令参数说明

| 参数 | 含义 | 默认 |
| --- | --- | --- |
| 最大距离 | 弹道最远判定距离（格），超出不造成伤害 / 点燃 | 64 |
| 散布范围 | 在最大距离处，落点在垂直于视角平面内的随机偏移**半径（格）**；0 = 无散布 | 0 |
| 弹道下坠 | 每 tick 速度向下的加速度，单位 **格/tick²** | 0.05 |

**弹道下坠基准值**：`0` 纯直线；`0.03` ≈ 原版雪球/鸡蛋；`0.05` ≈ **原版箭矢（推荐）**；`0.1` 明显抛物线。

## 自定义伤害类型

在 `plugins/SSCustomItems/damage_types/` 下新建 json：

```json
{
  "type_id": "example",
  "base_type": "minecraft:generic_kill",
  "translate": {
    "zh_CN": {
      "byplayer": "<PLAYER1>被<PLAYER2>杀死了",
      "byentity": "<PLAYER>因为<ENTITY>注定要死亡",
      "bynature": "<PLAYER>死了"
    }
  }
}
```

## 自定义方块

在 `plugins/SSCustomItems/blocks/` 下新建 json。自定义方块**不改模型**，以一个原版方块为载体，放置后通过位置索引识别，替换掉原版交互逻辑。支持 chest_menu（箱子/大箱子/末影箱/潜影盒）、门锁（门）、unbreakable（无法破坏）等。详见 docs/README.md。

## 与 ComponentsAPI 的关系

- **ComponentsAPI 只提供通用数据组件基础设施**：组件存储、网络同步、资源包 HTTP 托管与自动推送、客户端渲染管线。其 jar 内置仅含 `pack.mcmeta` 的空基础包，**不含任何具体自定义内容**；
- **所有具体自定义内容（模型、贴图、基岩几何/动画）都由 SSCustomItems 提供**：放在 `plugins/SSCustomItems/resourcepack/` 下。

## 安装与构建

把 `SSCustomItems-2.1.6.jar` 放入服务端 `plugins/` 目录，重启即可。从源码构建（需要 JDK 25）：`mvn package`，产物在 `target/SSCustomItems-2.1.6.jar`。
