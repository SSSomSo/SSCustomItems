# SSCustomItems · 原始需求记录（output.md）

> 本文件整理项目作者（Mc_SomSo）在开发过程中提出的全部原始需求，按时间/版本顺序归档，尽量保留原始措辞。用途：需求溯源、后续 AI 接手时理解完整意图，避免遗漏或曲解。
>
> 项目：SSCustomItems（Paper 26.2 插件）与 ComponentsAPI（Paper 插件 + Fabric 客户端模组）。许可证：CC BY-SA 4.0。

---

## 一、初始版本：自定义物品

### 1. 自定义物品基础

> 帮我写一个 Minecraft Paper 26.2 插件，可以新增物品（读取 `\plugins\SSCustomItems\items\物品名.json`，格式为 `{"vanilla_id":"使用的原版id","id":"命名空间:一个名称","enable_model":true/false,"components":{}}`）。`enable_model` 选择后决定是否在使用指令给予自定义物品的时候附带 `components:{item_model:"id里填的"}`；`components` 是原版物品格式，即用指令给予物品的时候带的数据组件。
>
> `/ci give （命名空间：物品名）` 可以给予物品（原版物品格式：`{count:1b,components:{（数据组件）,id:"（物品id）"}}`）。

环境：电脑里有 jdk25。

### 2. 物品触发动作（handed）

> 支持在 .json 文件里写入 `"handed.offhand":["/tellraw @a 1"]`（拿下该物品后切换到副手触发的命令，支持多行）。更有 handed.mainhand、handed.inoffhand、handed.inmainhand、handed.leftclick、handed.rightclick、handed.wear.helmet、inhelmet，chestplate、leggings、boots 同理。

### 3. 早期故障

> 使用 PlugManX 热加载显示 Failed onEnable，输入 /ci 出现"试图执行该命令时出现意外错误"。

---

## 二、战斗指令与自定义伤害类型

> 再加指令：damagelook、firelook、damageln、fireln。damagelook 可以精准伤害视角查看的实体（使用 mc 原版 damage_type，加上补全功能），并控制最高伤害距离、散布范围、弹道下坠（给基准值）；firelook 精准燃烧视角实体，控制时间、距离、散布、下坠；damageln 伤害视角点的半径内实体；fireln 燃烧视角点半径内实体。
>
> 在插件控制文件夹下新建 `\damage_types`，编写自定义伤害类型，示例 `{"type_id":"example","translate":{"zh_CN":{"byplayer":"<PLAYER1>被<PLAYER2>杀死了","byentity":"<PLAYER>因为<ENTITY>注定要死亡","bynature":"<PLAYER>死了"}}}`。作者 Mc_SomSo，许可证 CC BY-SA 4.0。

后续：damagelook 和 damageln 加上控制伤害大小的参数。

---

## 三、自定义方块（第一版）

> 支持自定义方块（不改模型，只是替换掉原版的逻辑）：`{"vanilla_id":"...","id":"...","events":{"tick":[],"rightclick":[],"leftclick":[],"break":[],"place":[]},"data":{"chest_menu":{"ischest":true,"isbig":true,"isender":true,"isshulkerbox":true,"default_items":[],"open_name":{}}}}`。break 默认返还 vanilla_id 带 custom_data；ischest 右键直接打开箱子；isbig 大箱子；isender 末影箱；isshulkerbox 保留内部物品。

---

## 四、2.0.0：热重载、补全增强、kill 指令

> 支持在线热重载物品、damagetypes；增加补全内容和补全注释，贴合 mc 原版；版本号 2.0.0；新增 killlook 和 killln。

---

## 五、2.1.0：护甲等级系统与门锁

### 1. 护甲等级系统

> 配置 `custom_data:{armor_type:"plugin",armor_lvl:（short）}`：低等级剑和箭矢对高等级护甲每低一级降低 20% 攻击力，降低的 20% 转换为攻击耐久（向上取整），护甲最大耐久变为原来的 15%，耐久为 0 时不消失，修复后降低两倍修复物品数量的最大耐久。等级剑和弓所需 custom_data 与护甲无异，不对原版物品和弓剑产生影响。

### 2. 门锁

> 自定义方块 vanilla_id 为门时，`data:{"lock":"SSCustomItems 自定义物品 ID","allow_redstone":true/false}` 指定门锁，没有钥匙无法打开，提示同原版上锁箱子。版本 2.1.0。

### 3. 护甲结算补充

> 自定义盔甲耐久归零时不为玩家提供减伤（其他自定义盔甲仍提供）；没有 custom_data 的剑攻击自定义护甲按默认 3 级结算；带 custom_data 的剑攻击普通护甲采用原版伤害。

### 4. 门锁系列修复

> 上锁的门显示 "%s 已被上锁"，取消 %s；配置了不支持红石也能用红石开；铁门没法用钥匙开（上下两层都要开）；支持爆炸抗性和硬度配置。

随后：抗性和硬度删了，加无法破坏（只有钥匙能破坏）。

### 5. 普通方块无法破坏

> 新增无法破坏（针对于方块）。

---

## 六、ComponentsAPI：数据组件子插件 + 客户端子模组

### 1. 初始需求

> 加入 git 版本控制，制作子插件和子模组 ComponentsAPI：允许生物和方块使用数据组件（符合原版体系，可用 data merge block 修改），示例如 capi:block_model、capi:entity_model、capi:block_light；子模组导入自定义实体/方块模型；支持往 jar 塞资源包启动自动加载（不支持卸载）；修复 SSCustomItems 补全时前面空格被覆盖（/ci 空格 tab 补全 give 变 /cigive）。

### 2. 组件 API 细化

> ComponentsAPI 插件端找镜像站；给不支持方块实体的配 /capi block 写入 pdc；支持基岩版实体模型和动画；服务端配置是否需要从服务端下载；ComponentsAPI 插件端没有 SSCI 无法启动；加 AI 交接文档防止上下文丢失；告诉如何缓解"知道治疗许愿式编程"。

---

## 七、1.2.0：基岩实体能力补全、职责拆分、GitHub

### 1. 基岩实体能力

> 基岩版实体需要同时支持手持物品、第三人称显示、方块显示、两种 UV（盒子 UV 与每面 UV）、声音关键帧。一定要做出来。

### 2. 职责拆分

> ComponentsAPI 只负责数据组件部分，真正的自定义内容移到 SSCustomItems 里，加强前置性。

### 3. Git / GitHub / Release / 文档

> 用上 Git 管理，用 GitHub 插件新建仓库并提交源代码。编译产物默认 Pre-Release，标题 `[Maybe Cannot Use] （插件/模组名）（版本号）`。把提示词整理成 output.md 和 readme.md 上传到 SSCustomItems 仓库的 /docs。

---

## 附：版本演进对照

| 版本 | 里程碑 |
| --- | --- |
| alpha / 1.x | 自定义物品读取、/ci give、SNBT 数据组件 |
| 1.x+ | handed 14 种触发动作 |
| 1.x+ | damagelook/firelook/damageln/fireln + 自定义伤害类型 |
| 1.x+ | 自定义方块：箱子/大箱子/末影箱/潜影盒菜单、事件系统 |
| 2.0.0 | 在线热重载、Brigadier 风格带注释补全、killlook/killln |
| 2.1.0 | 护甲等级系统、门双半/门锁 |
| 2.1.x | 门锁修复、unbreakable、补全覆盖空格修复 |
| CAPI 1.0/1.1 | 数据组件基础设施、资源包托管、Fabric 客户端渲染 |
| CAPI 1.2.0 | 基岩实体手持物品、第三人称、展示实体方块显示、双 UV、声音关键帧 |
| SSCI 2.1.6 | 示例资源包随 jar 分发、首次启动解压 |

---

*整理：Doubao（依据作者历次原始指令）。作者 Mc_SomSo，CC BY-SA 4.0。*
