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



| 指令                                               | 说明                        | 权限                            |
| ------------------------------------------------ | ------------------------- | ----------------------------- |
| `/ci give <命名空间:物品名>`                            | 给予 1 个对应自定义物品（背包满时掉落在脚下）  | `sscustomitems.give`（默认 OP）   |
| `/damagelook <伤害类型> [最大距离] [散布范围] [弹道下坠] [伤害大小]` | 精准伤害视角所指实体                | `sscustomitems.combat`（默认 OP） |
| `/firelook <燃烧秒数> [最大距离] [散布范围] [弹道下坠]`          | 精准点燃视角所指实体                | 同上                            |
| `/damageln <伤害类型> <半径> [散布范围] [弹道下坠] [伤害大小]`     | 伤害视角所指点位半径内的实体            | 同上                            |
| `/fireln <燃烧秒数> <半径> [散布范围] [弹道下坠]`              | 点燃视角所指点位半径内的实体            | 同上                            |
| `/cb place <命名空间:id>`                            | 在视角所指方块面上放置自定义方块          | `sscustomitems.block`（默认 OP）  |
| `/cb give <命名空间:id>`                             | 给予带标记的载体物品（可像原版方块一样放置）    | 同上                            |
| `/killlook [最大距离] [散布范围] [弹道下坠]`                 | 直接清除视角所指实体（等同原版 /kill 结算） | `sscustomitems.combat`（默认 OP） |
| `/killln <半径> [散布范围] [弹道下坠]`                     | 直接清除视角所指点位半径内的实体          | 同上                            |
| `/sscustomitems reload <目标>`（别名 `/ssci`）         | 在线热重载，无需重启                | `sscustomitems.reload`（默认 OP） |

`/ci give`、`/damagelook`、`/damageln` 的伤害类型参数，以及 `/cb` 的方块 id 参数均支持 Tab 补全。

**所有补全项都带有中文注释**：在补全列表中悬停某一项即可看到参数含义、默认值与基准值；

数值参数位会直接给出默认值示例，连续按 Tab 即可快速套用，体验与原版指令一致。

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



```
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

字段说明：



| 字段             | 必填 | 说明                                              |
| -------------- | -- | ----------------------------------------------- |
| `vanilla_id`   | 是  | 作为载体的原版物品 id，可省略 `minecraft:` 前缀                |
| `id`           | 是  | 自定义物品 id，格式 `命名空间:名称`，`/ci give` 时使用            |
| `enable_model` | 否  | 为 `true` 时，给予的物品自动附带 `item_model` 数据组件，值就是 `id` |
| `components`   | 否  | 原版物品数据组件，写法与原版 `components` 一致                  |
| `handed`       | 否  | 物品触发动作，见下                                       |

实际给予物品时，等价于原版 SNBT：



```
{count:1b, id:"minecraft:paper", components:{minecraft:item_model:"sscustomitems:ruby", ...}}
```

## 物品触发动作（handed）

在 json 中加入 `handed` 对象，每个触发对应一个**命令数组**（支持多行，按顺序执行）：



```
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

触发类型一览：



| 触发 key                                                             | 时机                         |
| ------------------------------------------------------------------ | -------------------------- |
| `offhand` / `mainhand`                                             | 物品**切换到**副手 / 主手的瞬间触发一次    |
| `inoffhand` / `inmainhand`                                         | 物品**只要在**副手 / 主手中，就按间隔反复触发 |
| `leftclick` / `rightclick`                                         | 手持物品时左键 / 右键触发（右键支持副手物品）   |
| `wear.helmet` / `wear.chestplate` / `wear.leggings` / `wear.boots` | 穿戴到对应装备槽的瞬间触发一次            |
| `inhelmet` / `inchestplate` / `inleggings` / `inboots`             | 只要穿戴在对应槽位，就按间隔反复触发         |

说明：



* 命令以控制台身份执行，可带可不带开头的 `/`；支持占位符 `%player%`（玩家名）。

* 玩家上线时已在身上的物品不会触发 “切换 / 穿戴”，只有之后发生状态变化才触发。

* 也支持点号扁平写法，例如直接在根上写 `"handed.offhand": ["..."]`、`"handed.wear.helmet": ["..."]`。

* 检测周期与持续触发间隔在 `plugins/SSCustomItems/config.yml`：


  * `check-interval-ticks`（默认 5）：状态检测周期

  * `repeat-interval-ticks`（默认 20）：in\* 持续触发的执行间隔

## 护甲等级系统

在自定义物品（护甲、剑、弓）的 `components.minecraft:custom_data` 中写入：



```
"minecraft:custom_data": {
  "armor_type": "plugin",
  "armor_lvl": 3
}
```

> `armor_lvl`
> 是 short 类型，直接写整数即可（数值范围 0–32767）。

规则（仅对带 `armor_type:"plugin"` 的物品生效，**不影响原版装备**）：



1. **减伤**：武器攻击插件护甲时，武器等级每比护甲等级低 1 级，最终伤害降低 **20%**；低 5 级及以上伤害为 0。武器等级 ≥ 护甲等级时全额伤害。

* 例：3 级剑打 5 级甲，差 2 级，伤害 ×(1 − 2×20%) = ×60%。

* 护甲等级取四件装备中**未损坏且等级最高的那件**，减伤只结算给提供最高等级的那件。

* **无&#x20;**`custom_data`**&#x20;的近战武器（含原版剑）按默认 3 级结算**；插件剑攻击**普通护甲**、或对方没有可生效的插件护甲时，一律采用**原版伤害**。

1. **耐久转换**：被抵消的伤害（原伤害 − 最终伤害，**向上取整**）转化为护甲耐久损耗。

2. **最大耐久 15%**：插件护甲的最大耐久变为原版的 15%（如钻石胸甲原版 528 → 79），给予物品时即生效。

3. **归零不消失、不再减伤**：插件护甲耐久到 0 时不会像原版一样直接消失，而是停留在 0 耐久继续穿在身上；但该件**不再提供减伤效果**（身上其他未损坏的插件护甲照常提供），用铁砧修复后恢复。

4. **铁砧修复**：左槽放插件护甲、右槽放对应原版修复材料时：

* 每用 1 个材料恢复 **25% 原版最大耐久**（如钻石胸甲恢复 132 点）；

* 但最大耐久**永久降低 2**（材料数量的两倍）；

* 每次铁砧消耗 1 个材料、至少 1 级经验。

修复材料对照：皮革甲→皮革；锁链 / 铁甲→铁锭；金甲→金锭；钻石甲→钻石；下界合金甲→下界合金锭；海龟壳→海龟鳞甲；狼铠→犰狳鳞甲。

箭矢的等级来自发射它的**插件弓**（弓与剑的 `custom_data` 写法和护甲相同）；非插件弓射出的箭不参与等级结算（按原版伤害）。

## 战斗指令参数说明

四个指令共用三个弹道参数（省略时取 config.yml 默认值）：



| 参数   | 含义                                            | 默认   |
| ---- | --------------------------------------------- | ---- |
| 最大距离 | 弹道最远判定距离（格），超出不造成伤害 / 点燃                      | 64   |
| 散布范围 | 在最大距离处，落点在垂直于视角平面内的随机偏移**半径（格）**；0 = 无散布、指哪打哪 | 0    |
| 弹道下坠 | 每 “tick” 速度向下的加速度，单位 **格 /tick²**             | 0.05 |

**弹道下坠怎么控制（基准值）**：模拟方式与原版箭矢一致 —— 每 tick 前进 3 格，速度每 tick 叠加一次向下加速度：



* `0`：纯直线，无下坠

* `0.03`：≈ 原版雪球 / 鸡蛋

* `0.05`：≈ **原版箭矢（推荐基准值）**

* `0.1`：明显抛物线，适合近距离曲射

`/damageln`、`/fireln` 额外的 `半径` 参数：以弹道落点为球心、该半径（格）内的全部生物都会受到影响。

`/damagelook`、`/damageln` 末尾可选的 `伤害大小` 参数：控制每个目标受到的伤害点数（省略时取 config.yml 的 `damage-amount`，默认 1000 秒杀）。

示例：



```
/damagelook minecraft:arrow 64 1.5 0.05 10   # 箭矢类型，64 格，1.5 散布，箭矢级下坠，造成 10 点伤害
/firelook 8 40 0 0                          # 点燃 8 秒，40 格，无散布，无下坠
/damageln example 4 0 0.05 20               # 自定义类型 example，落点 4 格半径，造成 20 点伤害
/fireln 5 3 1 0.03                          # 点燃 5 秒，3 格半径，1 格散布，雪球级下坠
/killlook 64 0 0.05                         # 清除视角所指实体，64 格，箭矢级下坠
/killln 4 0 0                               # 清除落点 4 格半径内全部实体，无散布无下坠
```

## 自定义伤害类型

在 `plugins/SSCustomItems/damage_types/` 下新建 json（与 `items/` 并列）：



```
{
  "type_id": "example",
  "base_type": "minecraft:generic_kill",
  "translate": {
    "zh_CN": {
      "byplayer": "<PLAYER1>被<PLAYER2>杀死了",
      "byentity": "<PLAYER>因为<ENTITY>注定要死亡",
      "bynature": "<PLAYER>死了"
    },
    "en_us": {
      "byplayer": "<PLAYER1> was slain by <PLAYER2>",
      "byentity": "<PLAYER> was doomed to die by <ENTITY>",
      "bynature": "<PLAYER> died"
    }
  }
}
```



| 字段          | 说明                                            |
| ----------- | --------------------------------------------- |
| `type_id`   | 自定义伤害类型 id，用于指令与补全                            |
| `base_type` | 实际结算用的**原版**伤害类型（默认 `minecraft:generic_kill`） |
| `translate` | 各语言下三种死亡场景的消息                                 |

占位符：



* `byplayer`：`<PLAYER1>` 死者、`<PLAYER2>` 击杀者（玩家）

* `byentity`：`<PLAYER>` 死者、`<ENTITY>` 击杀实体名

* `bynature`：`<PLAYER>` 死者

死亡消息使用的语言由 config.yml 的 `language`（默认 `zh_CN`）控制；找不到对应语言时回退 `en_us`。

## 自定义方块

在 `plugins/SSCustomItems/blocks/` 下新建 json（与 `items/`、`damage_types/` 并列）。自定义方块**不改模型**，

以一个原版方块为载体，放置后通过位置索引（方块实体额外打 PDC）识别，替换掉原版交互逻辑：



```
{
  "vanilla_id": "minecraft:chest",
  "id": "sscustomitems:gift_box",
  "events": {
    "tick": ["/say 方块还在"],
    "rightclick": ["/say 右键"],
    "leftclick": ["/say 左键"],
    "break": ["/say 被破坏"],
    "place": ["/say 已放置"]
  },
  "data": {
    "chest_menu": {
      "ischest": true,
      "isbig": false,
      "default_items": [
        "{id:\"minecraft:diamond\",count:2b}"
      ],
      "open_name": {"text": "礼物盒", "color": "gold"}
    }
  }
}
```

字段说明：



| 字段                | 必填 | 说明                                                                   |
| ----------------- | -- | -------------------------------------------------------------------- |
| `vanilla_id`      | 是  | 载体原版方块 id，外观与碰撞箱完全沿用它                                                |
| `id`              | 是  | 自定义方块 id，`/cb place`、`/cb give` 时使用                                  |
| `events`          | 否  | `tick` / `rightclick` / `leftclick` / `break` / `place` 五个事件，每个是命令数组 |
| `data.chest_menu` | 否  | 挂载容器菜单，见下                                                            |
| `data.unbreakable` | 否  | 为 `true` 时方块无法破坏，见下                                                  |

事件说明：



* `place`：放置完成后触发；`break`：被玩家破坏时触发；`tick`：方块存在期间按间隔触发（间隔由 config.yml 的 `block-tick-interval-ticks` 控制，默认 10 刻）。

* `rightclick` / `leftclick`：右键 / 左键方块时触发，并取消原版交互。

* 事件命令支持占位符：`%player%`（tick 时为空）、`%world%`、`%x%`、`%y%`、`%z%`。

* **破坏的默认行为**（即使不写 `break`）：返还 1 个带标记的载体物品；若该原版方块没有物品形式则不返还。用返还物品重新放置会自动恢复为自定义方块。爆炸、活塞移动也做了相应处理（活塞会被取消推动）。

chest\_menu 配置（四个开关互斥，按以下优先级判定）：



| 开关              | 效果                                           |
| --------------- | -------------------------------------------- |
| `ischest`       | 右键直接打开 27 格箱子界面（无需再写 rightclick），破坏后返还载体物品   |
| `isbig`         | 需 `ischest: true`，打开 54 格大箱子界面               |
| `isender`       | 与上述冲突，右键直接打开该玩家的末影箱物品栏                       |
| `isshulkerbox`  | 与上述冲突，打开 27 格界面；**破坏后物品保留全部内容物数据**，重新放置后内容还在 |
| `default_items` | 容器自带物品，使用原版 SNBT 书写（字符串数组），按顺序放入空格           |
| `open_name`     | 打开界面的标题，使用原版文本组件 JSON 格式                     |

普通箱子 / 大箱子被破坏时，内容物会像原版一样掉落在地；潜影盒内容物写入返还物品、不掉落。

### 门锁（门）

当 `vanilla_id` 是任意门（如 `minecraft:iron_door`、`minecraft:oak_door`）时，可在 `data` 中配置门锁：



```
{
  "vanilla_id": "minecraft:iron_door",
  "id": "sscustomitems:secret_door",
  "events": {
    "place": ["/say 暗门已安装"]
  },
  "data": {
    "lock": "sscustomitems:ruby",
    "allow_redstone": false
  }
}
```



| 字段                     | 说明                                                                                                     |
| ---------------------- | ------------------------------------------------------------------------------------------------------ |
| `lock`                 | 自定义物品 id（`items/` 中的 id）。玩家**主手或副手手持该物品**右键才能开门（**铁门同样有效**，上下半扇同步开关），否则门不动作，播放上锁音效并提示 “已被上锁”（不显示 `%s`） |
| `allow_redstone`       | `true` 时红石也可以开门；**默认&#x20;**`false`，红石无法打开（每 2 tick 兜底强制关闭被充能打开的门）                                     |

门的放置 / 破坏按整扇（上下两半）处理：放置时需门上方一格为空；破坏时两扇一起消失、只返还 1 个带标记物品；爆炸、tick 事件也做了双半去重。

**上锁的门无法被破坏**：非钥匙玩家挖掘会被取消并提示 “已被上锁”，爆炸（TNT、苦力怕、火球等）也无法将其摧毁；只有手持钥匙的玩家能正常拆除。

已知限制：村民开门等既非玩家交互、也非红石的开门方式没有对应 Bukkit 事件，未做拦截；如需绝对防开建议使用铁门。

### 无法破坏（普通方块）

任意自定义方块在 `data` 中写 `"unbreakable": true` 后：

- 玩家挖掘会被取消，并提示“此方块无法破坏”；
- 爆炸（TNT、苦力怕、火球、床/重生锚爆炸等）无法将其摧毁，不会掉落物品；
- 活塞无法推动（所有自定义方块均如此）。

示例：

```json
{
  "vanilla_id": "minecraft:obsidian",
  "id": "sscustomitems:fortress_core",
  "events": {
    "rightclick": ["/say 核心发出微弱的光芒"]
  },
  "data": { "unbreakable": true }
}
```

## 与 ComponentsAPI 的关系（模型 / 资源包）

**职责边界（重要）**：

- **ComponentsAPI 只提供通用数据组件基础设施**：组件存储（方块实体 NBT / PDC 兜底）、网络同步、资源包 HTTP 托管与自动推送、客户端渲染管线。其 jar 内置的是仅含 `pack.mcmeta` 的空基础包，**不含任何具体自定义内容**；
- **所有具体自定义内容（模型、贴图、基岩几何/动画）都由 SSCustomItems 提供**：放在 `plugins/SSCustomItems/resourcepack/` 下，按标准资源包目录组织。

ComponentsAPI 启动时自动组装最终资源包：解压内置基础包 → 合并 `plugins/SSCustomItems/resourcepack/**`（同名覆盖）→ 重新压缩、计算 SHA-1 并托管推送。

SSCustomItems 首次启动时，若 `plugins/SSCustomItems/resourcepack/` 不存在，会自动从 jar 内 `example-pack/` 解压一份示例资源包（红宝石方块 + 基龙几何示例）。想停用示例内容，直接删除/清空该目录后完整重启即可。

资源包一旦推送不支持运行时卸载；更新资源内容后需**完整重启**服务器（不要使用 PlugManX）。

## 组件值的类型说明（重要）

JSON 本身无法区分 byte /short/int/float/double，插件按以下规则转换：



* `true` / `false` → 布尔

* 普通整数 → int；超出 int 范围自动按 long 处理

* 小数 → 默认 **float**（物品组件中的小数绝大多数是 float，如 `custom_model_data` 的 floats、`tool` 的 speed）

* 需要 **double / byte / short / long** 时，把数字写成带后缀的字符串，会被原样识别：


  * `"1.5d"`（double，例如 `attribute_modifiers` 的 amount）

  * `"64b"`（byte）、`"100s"`（short）、`"12345678901L"`（long）

* 列表元素类型必须一致（SNBT 列表不支持混合类型）

## 安装与构建

把构建好的 `SSCustomItems-2.1.6.jar` 放入服务端 `plugins/` 目录，重启即可。

首次启动会自动生成：`items/ruby.json` 示例物品、`damage_types/example.json` 示例伤害类型、
`blocks/example_chest.json` 与 `blocks/example_shulker.json` 示例方块、`config.yml`，
以及 `resourcepack/` 示例资源目录（供 ComponentsAPI 合并托管，见上）。

> 注意：建议通过完整重启加载 / 更新插件。PlugManX 等热加载工具对新版 Paper 的类加载支持不完整，可能出现 onEnable 失败或指令异常。

从源码构建（需要 JDK 25）：

```
mvn package
```

产物在 `target/SSCustomItems-2.1.6.jar`。