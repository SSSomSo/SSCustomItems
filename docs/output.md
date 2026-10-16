# SSCustomItems · 原始需求记录（output.md）

> 本文件整理项目作者（Mc_SomSo）在开发过程中提出的全部原始需求，按时间/版本顺序归档，
> 尽量保留原始措辞。用途：需求溯源、后续 AI 接手时理解完整意图，避免遗漏或曲解。
>
> 项目：SSCustomItems（Paper 26.2 插件）与 ComponentsAPI（Paper 插件 + Fabric 客户端模组）。
> 许可证：CC BY-SA 4.0。

---

## 一、初始版本：自定义物品

### 1. 自定义物品基础

> 帮我写一个 Minecraft Paper 26.2 插件，可以新增物品（读取 `\plugins\SSCustomItems\items\物品名.json`，
> 格式为
> ```json
> {
>   "vanilla_id": "使用的原版id",
>   "id": "命名空间:一个名称",
>   "enable_model": true/false,
>   "components": { }
> }
> ```
> - `enable_model`：选择后决定是否在使用指令给予自定义物品的时候附带
>   `components:{item_model:"id里填的"}`；
> - `components`：原版物品格式，也是 `{}` 下一个 components，写入的时候不要写错了，
>   即用指令给予物品的时候带的数据组件。
>
> `/ci give （命名空间：物品名）` 可以给予物品（原版物品格式：
> `{count:1b,components:{（数据组件）,id:"（物品id）"}}`）。

环境补充：

> 电脑里有 jdk25（`C:\Users\mxbc6\Appdata\Roaming\.minecraft\runtime\java-runtime-epsilon\bin\java.exe`）。

### 2. 物品触发动作（handed）

> 再写一个功能，支持在 .json 文件里写入 `"handed.offhand":["/tellraw @a 1"]`
> （拿下该物品后切换到副手触发的命令，支持多行）。
>
> 更有：
> - `handed.mainhand`：在主手上时触发；
> - `handed.inoffhand`：只要在副手就一直触发；
> - `handed.inmainhand`：只要在主手时就一直触发；
> - `handed.leftclick`：左键触发；
> - `handed.rightclick`：右键触发；
> - `handed.wear.helmet`：戴在头上时触发；
> - `inhelmet`：只要在头上就触发；
> - chestplate、leggings、boots 同理。

### 3. 早期故障

> 使用 PlugManX 热加载显示 Failed onEnable，输入 /ci 出现"试图执行该命令时出现意外错误"。

---

## 二、战斗指令与自定义伤害类型

> 再加指令：`damagelook`、`firelook`、`damageln`、`fireln`。
>
> - `damagelook`：可以精准伤害视角查看的实体（使用 mc 原版 damage_type，加上补全功能），
>   并控制**最高伤害距离**（超过不伤害）、**散布范围**（半径，相对于玩家视角）、
>   **弹道下坠**（记得告诉我怎么控制，给我一个基准值）；
> - `firelook`：可以精准燃烧视角查看的实体，并控制时间、最高距离、散布范围和弹道下坠；
> - `damageln`：伤害视角查看的点的半径内实体，加入散布范围和弹道下坠；
> - `fireln`：燃烧视角查看的点的半径内实体，允许控制燃烧时间、散布范围和弹道下坠。
>
> 在插件控制文件夹下新建一个 `\damage_types`（与 `\items` 并列同一个文件夹），
> 可以编写自定义伤害类型，示例 `example.json`：
> ```json
> {
>   "type_id": "example",
>   "translate": {
>     "zh_CN": {
>       "byplayer": "<PLAYER1>被<PLAYER2>杀死了",
>       "byentity": "<PLAYER>因为<ENTITY>注定要死亡",
>       "bynature": "<PLAYER>死了"
>     }
>   }
> }
> ```
> 顺便加入作者信息：Mc_SomSo，许可证：CC BY-SA 4.0。

后续追加：

> damagelook 和 damageln 给我加上可以控制伤害大小的参数。

---

## 三、自定义方块（第一版）

> 再支持自定义方块（不改模型，只是替换掉原版的逻辑）：
> ```json
> {
>   "vanilla_id": "（原版的id）",
>   "id": "（使用 /cb place （namespace:id）时使用的id和命名空间）",
>   "events": {
>     "tick": [],
>     "rightclick": [],
>     "leftclick": [],
>     "break": [],
>     "place": []
>   },
>   "data": {
>     "chest_menu": {
>       "ischest": true,
>       "isbig": true,
>       "isender": true,
>       "isshulkerbox": true,
>       "default_items": [],
>       "open_name": { }
>     }
>   }
> }
> ```
>
> - `break` 默认不用写：破坏后返还一个 vanilla_id（若原版没有该方块的手持形式则不返还），
>   带 custom_data 放置后生成该方块的标签；
> - `ischest:true`：此项为 true 则 rightclick 事件不用写了，默认直接打开箱子界面，破坏后返还物品；
> - `isbig:true`：需要 ischest 为 true，打开后是大箱子界面；
> - `isender:true`：直接读取玩家潜影盒（末影箱），与 ischest、isbig 冲突；
> - `isshulkerbox:true`：与 ischest、isbig、isender 冲突，破坏后保留内部物品数据，放置后也出现；
> - `default_items`：自带物品，使用原版格式书写；
> - `open_name`：打开后的名字，也用原版格式。

---

## 四、2.0.0：热重载、补全增强、kill 指令

> 再改一下：
> - 支持在线热重载物品、damagetypes 之类的；
> - 增加补全内容和补全注释，使补全尽可能贴合 mc 原版；
> - 版本号改为 2.0.0；
> - 新增一个 `killlook` 和 `killln`。

---

## 五、2.1.0：护甲等级系统与门锁

### 1. 护甲等级系统

> 再写两个功能。
>
> 功能 1：配置 `custom_data:{armor_type:"plugin",armor_lvl:（short）}` 可以使：
> - 低等级剑和箭矢对高等级护甲的攻击力，每低一级降低 20%；
> - 同时将降低的 20% 转换为攻击耐久（向上取整）；
> - 但会导致护甲的最大耐久变为原来的 15%；
> - 并保证带 armor_type:plugin 的护甲耐久为 0 时不会直接消失；
> - 使用对应的修复物品修复后，降低两倍使用的修复物品数量的最大耐久；
> - 等级剑和弓所需的 custom_data 与护甲的无异；
> - 不对原版物品和弓剑产生影响。

### 2. 门 锁

> 功能 2：自定义方块 vanilla_id 为门时，允许使用
> `"data":{"lock":"（SSCustomItems 的自定义物品 ID）","allow_redstone":（true/false）}`
> 来指定门锁：
> - `allow_redstone` 为 true 时也可以使用红石来打开，不填默认 false；
> - 没有门锁（钥匙）时无法打开，提示同原版被上锁的箱子的提示。
>
> 修改版本为 2.1.0。

### 3. 护甲结算补充

> 还有：
> - 当自定义盔甲耐久归零时，将不为玩家提供减伤效果（如果身上还有别的自定义盔甲还是会提供的）；
> - 当没有 custom_data 的剑攻击自定义护甲时，自定义护甲根据自定义的 lvl 结算，默认剑是三级；
> - 当带 custom_data 的剑攻击普通护甲时，结算采用原版伤害。

### 4. 门锁系列修复

> 上锁的门会显示为 "%s 已被上锁"，改一下取消 %s；
> 而且甚至配置了不支持红石也能用红石开；
> 针对铁门居然没法用钥匙开（上下两层都要开）；
> 顺便要支持爆炸抗性和硬度配置。

随后变更：

> 算了，给抗性和硬度删了吧，加一个无法破坏（只有钥匙能破坏）。

### 5. 普通方块无法破坏

> 新增一个无法破坏（针对于方块）。

---

## 六、ComponentsAPI：数据组件子插件 + 客户端子模组

### 1. 初始需求

> 加入版本控制系统（我记得我电脑里有 git），并制作一个子插件和子模组，名叫 ComponentsAPI：
> - 允许生物和方块使用数据组件（不是原版的，一定要符合原版体系，可以用 data merge block 修改）；
> - 数据组件示例：
>   - `capi:block_model`、`capi:entity_model`（均使用客户端模型）；
>   - `capi:block_light:{eat_environment_light:false,light:1}`；
>   - 其他的就照原版方块的特性出；
> - 子模组可以导入自定义实体模型与自定义方块模型等需要客户端实现的功能；
> - 支持往 jar 里塞资源包启动自动加载（不支持卸载）；
> - 另外 SSCustomItems 还有补全时会导致前面的空格被覆盖（比如 /ci 空格按 tab 补全 give
>   会出现 /cigive），修一下。

### 2. 组件 API 细化

> ComponentsAPI 插件端你去找镜像站不就得了；
> 另外看 PDC 的名字感觉可以给不支持方块实体的配个 `/capi block` 写入 pdc；
> 能不能支持基岩版实体模型和动画；
> 支持服务端配置是否需要从服务端下载；
> 再加入 ComponentsAPI 插件端没有 SSCI 无法启动；
> 加个能让你的上下文丢失时仍能继续工作的东西——也就是说你要传给以后的 AI 独门秘籍，
> 不要让 AI 降智或者不符合我的要求；
> 顺便告诉我如何缓解"知道治疗许愿式编程"。

---

## 七、1.2.0：基岩实体能力补全、职责拆分、GitHub

### 1. 基岩实体能力（重点）

> 基岩版实体需要同时支持：
> - **手持物品**；
> - **第三人称显示**；
> - **方块显示**；
> - **两种 UV**（盒子 UV 与每面 UV）；
> - **声音关键帧**。
>
> 我知道这很难绷，但是一定要做出来。

### 2. 职责拆分

> 明确一下 ComponentsAPI 只负责数据组件部分，真正的自定义什么东西的可以移到 SSCustomItems 里，
> 这样可以加强前置性，而不是"改完依赖就能继续用"的东西。

### 3. Git / GitHub / Release / 文档

> 记得一定要用上 Git 管理，我用了 GitHub 插件，你可以在 GitHub 上把这几个仓库都新建，
> 然后源代码都提交上去。
>
> - 编译完的东西我没说过可用，默认提交 Release 为 **Pre-Release**，
>   内容前置：`[Maybe Cannot Use] （插件/模组名）（版本号）`；
> - 把之前我给你的提示词整理好后上传到仓库里的 `output.md`，
>   以及 `readme.md`，都上传到 GitHub 仓库里的 `/docs` 部分（提交到 SSCustomItems 仓库）。

---

## 八、ComponentsAPI 1.3–1.6：基岩物品模型体系

### 1. 基岩模型转向物品、手臂定位组

> 基岩实体模型在手持状态下支持手臂模型定位组（让手臂跟着组里的定位块动，不要改变手部纹理，因为基岩版实体模型每个组名都是唯一的所以定位组都是通用的），手臂定位组：lefthand_pos 左手、righthand_pos 右手，尺寸为 x4、y12、z4。

> 做个命令可以播放动画（会传到服务端）：/playbedrockanimation PLAYERNAME （资源包命名空间）：animations/xxx.animation.json （动画名称，例如 draw），动画不会出现在第三人称视角；允许使用按键绑定动作；资源包命名空间:index/xxx.json 可以指定默认动作、某些动作是否可被打断（默认可以）；兼容 TaCZ、YSM 之类的特殊 cube 或者定位组格式。

### 2. 第一人称偏移、按键绑定、覆盖原版动画

> 基岩版实体模型需要将第一人称默认时将模型往右下前偏移（模拟原版，可配置，默认开启），可以控制切换时动画（覆盖原版，分为切入和切出）、可以通过 index 控制按键绑定动画，lc 表示左键、rc 表示右键、mc 表示中键：{"keys":{"lc":[{"clicks":1,"animation":xxx}],"h":[{"animation":inspect}]}}。

### 3. 明确只服务物品、简化命令

> 顺便支持 SSCI 的自定义物品使用基岩版实体模型，取消针对方块和实体的基岩版实体模型支持（保留方块模型支持和物品基岩版实体模型支持）；去掉 pba 的 xxx.animation.json 选项，一个 index 和 geo 文件只对应一个同名 .animation.json 文件。

### 4. 贴图 / UV、环境与工作区

> 可以补上，把 SSCI 和 ComponentsAPI 工作区文件夹移动到 E:\workspace\；支持在 index 里自定义 uv 位置（模型对应的贴图）和 slot 贴图位置（物品栏贴图），覆盖原版动画（如果未配置原版的任何动作都不应出现）；把所有环境配好，写一个编写插件的 readme（不提交到 GitHub）。

### 5. ComponentsAPI 1.6.0：跟随手臂、音效、附魔光效

> 基岩实体模型不跟着手臂动。

> 加入动画使用的音效位于和原版音效文件一样的位置（可被 playsound 识别）。

> 允许在 index 里控制是否允许开启附魔光效以节省资源，默认不允许。

## 九、SSCustomItems 2.2.0：随机物品、多页容器、快速保存

### 1. 随机物品（random）

> SSCI 新增 random 选项，可以替代 components，支持在 random 下的 components 里使用占位符 <random_三个数字> 来生成随机数，通过匹配这三个数字在 {random_rules:[{id:1,type:double,max:10,min:1}]} 来设置随机数规则；当使用 ci give 带 random 的物品时随机从类似于 random:[{item_model:xxx},{item_name:xxx}] 下选择一项作为给予的物品。

### 2. 大末影箱与多页容器

> 允许自定义的末影箱使用 isbig（大箱子，使用插件储存），并加入 multipages 和 pages（末影箱、潜影箱和普通箱子均可用），当启用 multipages 时会将该箱子方块的最左下角和最右下角分别设置为前一页和后一页。

### 3. 快速保存手中物品

> 允许使用 ssci save（文件名）（id）来快速将手中物品保存为 SSCI 自定义物品。

## 十、ComponentsAPI 1.7.0 / SSCustomItems 2.3.0：第一人称切换、忽略组、强制播放、共享末影箱

### 1. 按键切换第一人称模拟原版手持

> 支持在绑定按键里写上绑定以后按下可以切换是否使用第一人称模拟原版手持，可配置不使用时与其他动画冲突（按下绑定按键不会出现对应效果，除非再按一次按键）。

### 2. 默认忽略不渲染的骨骼组

> 可配置哪些组（包括其中的块，因为基岩版实体模型同名称的组在单文件内只能存在一个，所以不用路径直接填名称）被默认忽略不渲染。

### 3. 动画强制播放

> 动画可配置是否启用强制播放（在播放过程中无法被除了切换物品、丢弃物品或死亡等以外的操作打断）。

### 4. 按名字共享的末影箱（np_）

> 另外 isender（包括 isbig）加入重命名成什么就使用哪个存储文件（开头要比玩家单人的存储多 np_），当任何玩家使用相同名字的自定义末影箱放下以后就会使用对应的存储文件。

## 十一、ComponentsAPI 1.8.0：第一人称模拟切换关键帧过渡

> 切换是否使用第一人称模拟时使用关键帧：
> - 切入模拟原版手持（基岩→原版）时，先播放基岩模型的退场关键帧动画（`to_vanilla_animation`），
>   动画结束后才交原版手持渲染；
> - 切回基岩（原版→基岩）时，先恢复基岩渲染并播放进场关键帧动画（`to_bedrock_animation`）；
> - 不写则回退 `switch.out` / `switch.in`，都没有则瞬间切换；
> - 过渡期间强制，普通按键（lc/rc/mc/h 及再次按 fp）不能打断，仅切换物品、丢弃、死亡可打断。

## 十二、ComponentsAPI 1.9.0：全键盘按键、加密资源包、示例包扩充

### 1. 示例资源包扩充（占位文件）

> 给 ComponentsAPI 示例资源包加入基岩版实体模型和动画、方块模型，加入到插件和模组里。
> 把示例包扩充一下，什么都要用到；基岩版模型和贴图用占位符（`xxx.json.txt`、`xxx.png.txt` 之类的）填充。
> （保留一个可真实加载的 example。）

### 2. 声音 / 粒子关键帧、每面 UV

> 能否支持声音 / 粒子关键帧、每面 UV（per-face UV）。

### 3. 全键盘按键绑定

> 别告诉我你只给按键绑定写了那几个，键盘上几乎所有的按键都要做（在示例里用注释就行了）。

### 4. 资源包加密（自定义 AES，不兼容 zip 加密）

> 支持资源包进行加密：模组会在启动后加载 `\config\capi\rp\` 下所有扩展名为 zip 的资源包进行加密，
> 密钥在 `config\capi\config.toml`（如果不是 toml 就用原来的）进行加密解密；
> 加密需要将加密后的 `.dat` 文件直接放在 jar 里才能加载；
> 启动时不要在非临时文件夹里解密资源包，当然直接解密完写游戏里、不放临时文件更好；
> 加密不要兼容 zip 加密，一定要是至少 512 位密钥。

## 附：版本演进对照

| 版本 | 里程碑 |
| --- | --- |
| alpha / 1.x | 自定义物品读取、`/ci give`、SNBT 数据组件 |
| 1.x+ | handed 14 种触发动作（主副手切换/持续、左右键、四件装备穿戴/持续） |
| 1.x+ | damagelook/firelook/damageln/fireln + 自定义伤害类型 |
| 1.x+ | 自定义方块：箱子/大箱子/末影箱/潜影盒菜单、事件系统 |
| 2.0.0 | 在线热重载、Brigadier 风格带注释补全、killlook/killln |
| 2.1.0 | 护甲等级系统、门双半/门锁 |
| 2.1.x | 门锁修复（%s、红石、铁门双半、防爆）、普通方块 unbreakable、补全覆盖空格修复 |
| ComponentsAPI 1.0.0 / 1.1.0 | 数据组件基础设施、资源包托管、Fabric 客户端渲染（方块模型/基岩模型） |
| ComponentsAPI 1.2.0 | 基岩实体手持物品（全生物）、第三人称、展示实体方块显示、双 UV（含 uv_size/旋转）、声音关键帧（3D 定位/随机音调） |
| ComponentsAPI 1.3–1.4 | 基岩模型转向物品、手臂定位组 lefthand_pos/righthand_pos、/playbedrockanimation、index 按键绑定（lc/rc/mc/h）与切入切出动画、兼容 TaCZ/YSM、取消方块/实体基岩模型、自定义 uv/slot 贴图、覆盖原版动画 |
| ComponentsAPI 1.5.0 | 第三人称手持基岩物品渲染、工作区迁至 E:\workspace、本地开发环境 |
| ComponentsAPI 1.6.0 | 修复第三人称手持模型跟随手臂摆动、动画音效走原版 sounds 体系（可 playsound）、index 附魔光效开关默认关 |
| SSCustomItems 2.1.6 | 示例资源包随 jar 分发、首次启动解压，交由 ComponentsAPI 合并托管 |
| SSCustomItems 2.2.0 | random 随机物品（变体 + `<random_###>` 占位符）、大末影箱（isender+isbig 插件存储）、箱子/潜影盒/末影箱多页（multipages/pages，左下=上一页、右下=下一页）、`/ssci save` 保存手中物品 |
| ComponentsAPI 1.7.0 | 按键（默认 R 键）切换第一人称是否模拟原版手持（可压制其他按键动画）、index 按名字忽略不渲染骨骼组（含子层级）、动画强制播放（force，普通操作不可打断，仅切换物品/丢弃/死亡可打断） |
| SSCustomItems 2.3.0 | isender（含 isbig）按 open_name 名字使用 `np_<名字>` 全局共享存储（ender_storage/），任何玩家同名字末影箱共享同一份，破坏只返还载体、内容保留 |
| ComponentsAPI 1.8.0 | fp 切换第一人称模拟时播放关键帧过渡（`to_vanilla_animation` 退场 / `to_bedrock_animation` 进场），未配置则回退 switch.out/in，过渡期间强制不可打断 |
| ComponentsAPI 1.9.0 | 全键盘按键绑定（字母/数字/F 键/方向/小键盘/符号/修饰键，点击 vs 按住）、资源包 AES-256-GCM 加密（512 位密钥，config.toml）`.dat` 放进模组 jar 内 `capi_packs/` 运行时内存解密注入不落盘、showcase 全功能占位示例（每面 UV/粒子/声音关键帧/方块模型） |

---

*整理：Doubao（依据作者历次原始指令）。作者 Mc_SomSo，CC BY-SA 4.0。*
