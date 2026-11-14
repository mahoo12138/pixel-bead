# Pixel Bead 模组实施计划（Kotlin / Minecraft 26.3 / NeoForge 26.3.0.99）

## Context（为什么做这件事）

文件夹 `D:\Code\Kotlin\pixel-bead` 刚建好，目前只有设计文档 `minecraft-bead-mod-design.md`。目标是用 **Kotlin** 从零实现一个完整 MVP：291 种 MARD 纯色拼豆方块 + 染色工作台 + 服务端权威的“拼豆建造模式” + 九槽虚拟颜色快捷栏与完整颜色选择器，支持创造/生存、单人与专用服务器多人协作。本计划覆盖设计文档的全部 5 个阶段，按阶段增量落地，每阶段都有可进游戏验证的验收点。

模组标识：**modId = `pixelbead`**，基础包 **`com.pixelbead`**，显示名 **Pixel Bead**，方块 ID 形如 `pixelbead:bead_a1`。

---

## 一、已核实的工具链（权威来源核对，直接采用，不要再改版本）

| 组件 | 版本 / 坐标 | 来源 |
|---|---|---|
| Minecraft | 26.3 | 用户指定 |
| NeoForge | 26.3.0.99 | 用户指定 |
| Kotlin 加载器 | KFF `thedarkcolour:kotlinforforge-neoforge:6.5.0`（2026-11-09 发布） | KFF maven metadata + POM |
| Kotlin 语言 | **2.4.21**（与 KFF 内置 stdlib/coroutines 1.11.0 / serialization 1.12.0 对齐） | KFF 6.5.0 POM |
| KFF maven | `https://thedarkcolour.github.io/KotlinForForge/` | 可访问 |
| Gradle 插件 | ModDevGradle `net.neoforged.moddev` **2.0.148** | plugins.gradle.org metadata |
| Gradle | 9.x wrapper | 26.x 工具链 |
| Java | **25**（用 `foojay-resolver-convention` 0.8.0 自动下载 JDK） | 26.x 要求 |
| 颜色数据 | `makebead/craft-color-codes` → `data/csv/mard-291.csv`，**291 行**，许可证 **MIT**（设计文档猜的 CC BY 4.0 是错的） | GitHub API 实测 |

- KFF mod 元数据声明 Minecraft 范围 `[1.21.9, 26.4)`，**包含 26.3**，兼容。
- CSV 列：`code,name,hex,r,g,b,discontinued`；色系数量实测 = A26 B32 C29 D26 E24 F25 G21 H23 M15 P23 Q5 R28 T1 Y5 ZG8 = 291，与设计 §3.1 完全一致。
- **网络**：用户有代理/VPN，Gradle 能访问 `maven.neoforged.net`，按标准仓库配置即可，**不要写代理设置**。（代理沙箱内 `maven.neoforged.net`/`docs.neoforged.net` 被墙、GitHub API 限流，仅影响调研，不影响用户机器构建。）

---

## 二、26.3 API 基线（关键！与 1.21.x 旧教程不同，已对 NeoForge `26.3.x` 分支源码核实）

实现时一律按下表，**不要照搬旧版 `ResourceLocation` / `BlockEvent.BreakEvent` / lambda 颜色处理器写法**。首次 `runClient` 时 ModDevGradle 会在本地反编译出 26.3 源码，所有标注“待本地核对”的签名以本地源码为最终准绳。

| 主题 | 26.3 实际 API |
|---|---|
| 资源标识 | **`net.minecraft.resources.Identifier`**（`ResourceLocation` 已改名；`Identifier.fromNamespaceAndPath(ns, path)`）。已由 NeoForge 21.11 发行说明 + Mojira MC-308007 佐证 |
| 方块/物品注册 | `DeferredRegister.Blocks.registerBlock(name, Function<BlockBehaviour.Properties,B>, Supplier<Properties>)` 内部自动 `setId(ResourceKey…)`；`Items.registerItem(name, Function<Item.Properties,I>, Supplier<Properties>)`；`registerSimpleBlockItem(Holder<Block>, …)` 会 `useBlockDescriptionPrefix()`（物品共用方块语言键）。**1.21.3+ 必须给 Properties 盖注册 id** |
| 颜色着色 | `RegisterColorHandlersEvent.**BlockTintSources** / .**ItemTintSources** / .ColorResolvers**。`BlockTintSource` = `int color(BlockState)` + `int colorInWorld(BlockState, BlockAndTintGetter, BlockPos)`（**无 tintIndex 参数**）；`ItemTintSource` = `int calculate(ItemStack, ClientLevel?, LivingEntity?)` + `MapCodec<? extends ItemTintSource> type()`。物品 tint 走 **codec 注册 + item-model JSON 引用**，不是 per-item lambda |
| 破坏拦截 | **`BlockEvent.BreakEvent` 已移除** → `net.neoforged.neoforge.event.level.block.**BreakBlockEvent**`（可取消，两端触发，`getPlayer()`，`shouldNotifyClient()`） |
| 数据附件 | **无 `RegisterAttachmentTypesEvent`** → `DeferredRegister<AttachmentType<?>>(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "pixelbead")`；`AttachmentType.builder(::T).serialize(MapCodec).sync(StreamCodec).build()`；同步附件登录/变更时自动下发，服务端持久化到 `session_attachments.dat` |
| Tick 事件 | 拆分为 `net.neoforged.neoforge.event.tick.**PlayerTickEvent.Pre/Post**`、`ServerTickEvent`、`EntityTickEvent`、`LevelTickEvent` |
| HUD 图层 | `RegisterGuiLayersEvent.registerAbove(Identifier anchor, Identifier id, GuiLayer)`，`GuiLayer.render(**GuiGraphicsExtractor**, DeltaTracker)`（26.3 RenderPearl 渲染管线）。**待本地核对** `GuiGraphicsExtractor` 的 fill/blit/text 方法与 `VanillaGuiLayers` 锚点 id |
| 按键 | `RegisterKeyMappingsEvent.register(KeyMapping)` + `registerCategory(KeyMapping.Category)`；类别为 `KeyMapping.Category` 对象。SDL3 为底层，`InputConstants` 键码/`KeyMapping` 构造**待本地核对** |
| 输入事件 | `InputEvent.Key`（**不可取消**）、`InputEvent.MouseButton.Pre`（可取消）、`InputEvent.MouseScrollingEvent`（可取消，`getScrollDeltaY()`）、`InputEvent.**InteractionKeyMappingTriggered**`（可取消，`isAttack()/isUseItem()/isPickBlock()`）——拼豆模式攻击/使用的干净输入级闸门 |
| 交互事件 | `PlayerInteractEvent.RightClickBlock`（`setUseBlock/setUseItem(TriState)`、`setCancellationResult`）、`RightClickItem`、`EntityInteract`（可取消）；`RightClickEmpty/LeftClickEmpty` 不可取消；新增 `UseItemOnBlockEvent`（在 RightClickBlock 之后逐阶段触发，作兜底） |
| 网络包 | `RegisterPayloadHandlersEvent.registrar("1")` → `PayloadRegistrar.playToServer/playToClient(CustomPacketPayload.Type<T>, StreamCodec<? super RegistryFriendlyByteBuf,T>, IPayloadHandler<T>)`；handler 内 `context.enqueueWork{}` 切主线程 |
| 菜单/界面 | `MenuType` 走 DeferredRegister；`RegisterMenuScreensEvent.register(type, ::Screen)`；`player.openMenu(SimpleMenuProvider, pos)`（**待本地核对** ctor / `IMenuTypeExtension` / `ContainerLevelAccess`） |
| 创造分类 | `CreativeModeTab.builder()…displayItems((params,output)->…)` + `BuildCreativeModeTabContentsEvent` |
| 配置 | `net.neoforged.neoforge.common.ModConfigSpec`；`ModContainer.registerConfig`；`ModConfigEvent.Reloading`（**待本地核对**名称） |
| 其它已核实 | `PlayerEvent.Clone/LoggedIn/LoggedOut/Respawn/ChangedDimension/ChangeGameMode/BreakSpeed`、`EntityTeleportEvent`、`BlockEvent.EntityPlaceEvent`（可取消，领地模组在此否决）、`AddServerReloadListenersEvent`/`AddClientReloadListenersEvent`、`RegisterCommandsEvent`、`RegisterGameTestsEvent` |
| 入口 | `@Mod("pixelbead") class PixelBead(modEventBus: IEventBus)`，`modEventBus.addListener(...)`；游戏总线用 `@EventBusSubscriber` |

---

## 三、核心架构决策

1. **颜色数据 = 构建期 CSV→JSON**（不是运行期解析 CSV）。调色板在**注册期**就要用（早于任何 reload listener/datapack），两端必须一致，且同一份数据驱动生成 291×3 个资源文件。构建期转换可 fail-fast 校验、零运行期解析风险。运行期 `Palette` 用 classpath 流读 JSON（kotlinx-serialization，KFF 已内置）。
2. **染料→色系映射 = 运行期可调数据**：放 `data/pixelbead/pixelbead/dye_series_map.json`，经 `AddServerReloadListenersEvent` + `AddClientReloadListenersEvent` 加载。位于 `data/` 下 → 服务端 datapack 可覆盖、原版 datapack 同步自动下发客户端，无需自定义同步代码。白/黑名单放服务端配置。
3. **拼豆模式状态 = 同步 + 可序列化玩家数据附件**（不用 capability、不用裸 NBT）。`BeadModeState(active, selectedSlot, hotbar:List<String>(9), hotbarInitialized, priorMayFly, priorFlying, flightGranted)`，`AttachmentType.builder(::BeadModeState).serialize(MAP_CODEC).sync(STREAM_CODEC).build()`，**不加 `copyOnDeath()`**（死亡必须重置模式）。服务端唯一写者，客户端改动一律走 payload；快捷栏因此按玩家/按世界自动持久化与同步，零自定义同步代码。
4. **放置不传坐标、不传颜色**（安全核心，设计 §9.1/§11.4）。放置由**原版右键包**触发，服务端从 `PlayerInteractEvent.RightClickBlock.getPos()/getFace()` 取位置（原版已在 `ServerGamePacketListenerImpl` 做过距离校验）。`PlaceBeadRequest` 不含坐标/颜色 → 彻底消除坐标/颜色伪造面。破坏**无需任何 payload**：原版攻击包 → `BreakBlockEvent` → 放行拼豆/取消其余。
5. **放置走原版 BlockItem 管线**（领地兼容，设计 §9.3）：构造**脱离背包的** `ItemStack(BeadItems.itemFor(colorId))` + `BlockPlaceContext`，调用原版 use-on 路径 → 触发 `BlockEvent.EntityPlaceEvent`（领地模组在此否决）、播放声音、发 game event、套用可替换方块规则。成功后生存才从真实背包扣 1 个；被否决/失败则不扣 + HUD 提示。**绝不裸 `setBlock`**。
6. **虚拟快捷栏与真实物品栏解耦**（设计 §7.1）：`InputEvent.Key` 不可取消 → 数字键 1–9 时原版也会移动真实选中槽；对策：进入模式记录 `inventory.selected`，模式中每 tick（`ClientTickEvent.Post`）强制还原；退出时原槽完好。滚轮 `MouseScrollingEvent` 可取消 → 取消并切换虚拟槽。中键 `MouseButton.Pre` 取消原版 pick-block，若准星指向拼豆则吸取颜色（可选）。
7. **客户端代码全部隔离在 `com.pixelbead.client`**，仅由 `@EventBusSubscriber(value=[Dist.CLIENT])` / 客户端守卫初始化引用；公共代码绝不 import 它 → 专用服务器不加载客户端类。客户端通过同步附件得知模式状态。
8. **飞行授予/恢复精确**（设计 §6.3/§9.4）：进入（生存且配置允许）记录 `priorMayFly/priorFlying`，只置 `mayFly=true`（不强置 flying），`flightGranted=true`，同步 abilities；退出/清理只撤销本模组授予的部分。创造不授予（用原版飞行）。`PlayerChangeGameModeEvent` 中途切换 → 重新评估授予。

---

## 四、项目结构（关键文件路径）

```
D:\Code\Kotlin\pixel-bead\
├── settings.gradle.kts / build.gradle.kts / gradle.properties / gradlew(.bat) / gradle/wrapper/…
├── color-data/  mard-291.csv, LICENSE(MIT), NOTICE.md(署名+固定 commit+获取日期+屏幕参考值声明)
├── src/main/kotlin/com/pixelbead/
│   ├── PixelBead.kt                      @Mod 入口，先 Palette.load() 再挂所有 DeferredRegister
│   ├── palette/ BeadColor.kt(@Serializable) · Palette.kt(object 加载/校验/索引 byId/bySeries/seriesOrder/argb) · DyeSeriesMap.kt(reload JSON)
│   ├── blocks/ BeadBlock.kt(薄标记子类，O(1) isBead) · BeadBlocks.kt(注册 291) · BeadItems.kt(registerSimpleBlockItem)
│   ├── creative/ BeadCreativeTab.kt      “拼豆”分类 + BuildCreativeModeTabContentsEvent，色系/色号自然序(A2<A10)
│   ├── workbench/ DyeWorkbenchBlock.kt · WorkbenchMenus.kt · DyeWorkbenchMenu.kt · ConvertibleBlocks.kt · WorkbenchLogic.kt(纯函数可单测)
│   ├── beadmode/ BeadModeState.kt(+MapCodec/StreamCodec) · BeadModeAttachments.kt · BeadModeManager.kt(进入/退出/清理/飞行) · BeadModeEvents.kt(拦截矩阵) · BeadPlacement.kt(校验放置)
│   ├── inventory/ BeadInventory.kt       Player 扩展 countBead(id)/consumeBead(id)
│   ├── network/ BeadPayloads.kt · BeadNetwork.kt
│   ├── config/ ServerConfig.kt · ClientConfig.kt
│   └── command/ PixelBeadCommand.kt      /pixelbead enable|disable|status [player]
├── src/main/kotlin/com/pixelbead/client/
│   ├── PixelBeadClient.kt                Dist.CLIENT：tints/keys/screens/GUI layers
│   ├── render/ BeadBlockTint.kt · BeadItemTint.kt(MapCodec.unit，注册为 pixelbead:bead_color)
│   ├── key/ BeadKeyMappings.kt · BeadKeyHandler.kt
│   ├── hud/ BeadHudLayer.kt              GuiLayer：9 槽/色号/数量/模式徽标/提示
│   ├── gui/ ColorPickerScreen.kt · DyeWorkbenchScreen.kt
│   └── ClientPrefs.kt                    收藏/最近/默认快捷栏（基于 ClientConfig）
├── src/main/resources/
│   ├── META-INF/neoforge.mods.toml
│   ├── assets/pixelbead/ textures/block/bead_base.png(唯一 16×16 白纹理) · textures/gui/* · models/block/bead.json(共享立方体,6 面 tintindex 0) · palette/mard291.json(生成) · blockstates/bead_*.json×291(生成) · items/bead_*.json×291(生成,tints→pixelbead:bead_color) · lang/{en_us,zh_cn}.json(生成+手动 UI 串) · color-data/(CSV/LICENSE/NOTICE 随 jar 发布)
│   └── data/pixelbead/ pixelbead/dye_series_map.json(15 染料→15 色系,无蓝) · loot_table/blocks/bead_*.json×291(生成,掉落自身) · recipe/dye_workbench.json
└── src/test/kotlin/com/pixelbead/        PaletteTest · DyeSeriesMapTest · WorkbenchLogicTest · ConvertibleBlocksTest
```

### Gradle / 元数据要点
- `settings.gradle.kts`：`pluginManagement{ gradlePluginPortal(); maven("https://maven.neoforged.net/releases") }` + `foojay-resolver-convention 0.8.0`；`rootProject.name="pixel-bead"`。
- `gradle.properties`：`minecraft_version=26.3`、`neo_version=26.3.0.99`、`kff_version=6.5.0`、`mod_id=pixelbead`、`mod_version=0.1.0`、`org.gradle.jvmargs=-Xmx3G`、parallel/caching/configuration-cache。
- `build.gradle.kts`：`id("net.neoforged.moddev") version "2.0.148"` + `kotlin("jvm") version "2.4.21"`；`repositories{ mavenCentral(); maven(KFF) }`；`neoForge{ version=neo_version; runs{ create("client"){client()}; create("server"){server()} }; mods{ create("pixelbead"){ sourceSet(sourceSets.main.get()) } } }`；`implementation("thedarkcolour:kotlinforforge-neoforge:6.5.0")`；`java.toolchain=25` + `kotlin.jvmToolchain(25)`。**MDG 2.0.148 的 Kotlin-DSL 访问器待本地核对**。
- `neoforge.mods.toml`：`modLoader="kotlinforforge"`、`loaderVersion="[6,)"`、`license="MIT"`；依赖 `neoforge [26.3.0.99,)`、`minecraft [26.3,26.4)`、`kotlinforforge [6,)`，`side="BOTH"`。
- KFF 属性委托：每个 `val X by REGISTER.register(...)` 必须 `import thedarkcolour.kotlinforforge.neoforge.forge.getValue`。
- **构建任务 `generateBeadResources`**（Kotlin，内联或 buildSrc）：解析 CSV → 派生 `id=code.lowercase()`、`series=code.takeWhile{it.isLetter()}`（处理两字母 ZG）→ 校验 hex↔rgb、id/code 唯一 → **硬失败**除非 total=291 且 15 色系齐全且各系数量精确匹配 → 生成 palette JSON / 291 blockstates / 291 item models / 291 loot tables / lang，输出到 `build/generated/pixelbeadResources`，`sourceSets.main.resources.srcDir(...)` + `processResources.dependsOn(...)`。

---

## 五、分阶段实施

### 阶段一 · 基础注册（可运行地基）
**新建**：全部 Gradle 文件；`color-data/*`；`generateBeadResources`；`PixelBead.kt`；`palette/*`（DyeSeriesMap 先占位）；`blocks/*`；`creative/BeadCreativeTab.kt`；`client/render/*` + `PixelBeadClient.kt`；共享 `bead.json` + `bead_base.png`；生成的 blockstates/item models/loot/lang；单测。
**API**：`DeferredRegister.Blocks.registerBlock(name, ::BeadBlock){ uniformProps() }` / `Items.registerSimpleBlockItem(holder)`；`RegisterColorHandlersEvent.BlockTintSources.register(listOf(BeadBlockTint), *all291)`；`.ItemTintSources.register(Identifier("pixelbead","bead_color"), MapCodec.unit(BeadItemTint))`；创造分类按 `Palette.seriesOrder` + 色号自然序填充。
**统一属性**：完整立方体、无重力、无 BE、统一 `destroyTime`(如 0.5f)、无工具要求、正常爆炸抗性、掉落自身。tint 从方块/物品注册 id 经 `Palette` 取 ARGB —— 一纹理一模型 291 注册，零 per-color 逻辑。
**正确性**：`Palette.load()` 必须在任何注册 object 初始化前（Kotlin object 初始化顺序，入口构造里显式先调）；ZG 两字母解析；item-model JSON 正确引用自定义 tint 类型（**schema 待核对**）；模型 JSON 6 面显式 `tintindex:0`（原版 `cube_all` 无，需自写 elements 或核对 26.3 等价写法）。
**验收**：`gradlew build`（生成器数量不符即硬失败）；`PaletteTest`（291/15 系/精确数量/hex↔rgb）；`runClient`：拼豆分类显示 291 且排序正确；`/give pixelbead:bead_zg8`；放置后世界/手持/物品栏三处颜色一致且 = `#RRGGBB`；生存破坏掉自身、创造不掉；存盘退出重进保留；`runServer` 启动不加载客户端类；F3 显示 id。

### 阶段二 · 染色工作台
**新建**：`workbench/*`、`client/gui/DyeWorkbenchScreen.kt`、`DyeSeriesMap` reload listeners、`dye_series_map.json`、工作台 recipe/model/lang/loot、GUI 纹理。
**API**：`MenuType` DeferredRegister（**ctor/IMenuTypeExtension 待核对**）；`useItemOn` → `player.openMenu(SimpleMenuProvider, pos)`；**无 BlockEntity**，采用原版工作台/切石机式瞬态容器：menu 持有 input(可容 16)/dye/result 槽，`removed(player)` 归还输入，`ContainerLevelAccess` 式距离有效性。选色用原版 **`DataSlot`**(颜色索引) + **`clickMenuButton`** id 0..290 同步，**无需自定义 payload**。`RegisterMenuScreensEvent.register(type, ::DyeWorkbenchScreen)`。
**数据流**：输入变化 → `slotsChanged` → `WorkbenchLogic.evaluate(input, dye, selectedIndex, config, dyeMap)` → 预览 16 拼豆或状态码（需 16/染料不符/不可转换/未选色）。取出结果 → 服务端原子重校验 → 扣 16 个同类输入 + 1 染料 → 给 16 拼豆。蓝染料 → “未映射”状态。网格只渲染 `dyeMap.seriesFor(dye)` 的颜色。
**输入过滤 `ConvertibleBlocks`**：必须是 `BlockItem`；排除 `defaultBlockState().hasBlockEntity()`（箱子/木桶/潜影盒/熔炉，**方法待核对**）；排除容器内容数据组件（**`DataComponents.CONTAINER` 待核对**）；排除空气/流体/不可放置；`allow_modded_blocks` 命名空间闸门；配置白名单(覆盖)/黑名单。
**正确性**：仅取出时消耗、原子重校验防连点刷物；同类判定需组件感知相等；批量数取配置 `blocks_per_dye/blocks_per_batch`；多玩家并发各自独立瞬态容器。
**验收**：合成/放置/打开；**15 染料 × 各自色系全部颜色**（抽查 B=32、T=1、ZG=8）；16 石头 + 染料 → 16 拼豆且精确消耗；15 个输入 → 状态提示无结果；箱子/潜影盒/桶/岩浆被拒；黑白名单生效；关闭菜单归还物品；`WorkbenchLogicTest`+`ConvertibleBlocksTest`；第二客户端独立会话。

### 阶段三 · 拼豆模式基础（模式/飞行/拦截/放置/消耗）
**新建**：`beadmode/*`、`inventory/BeadInventory.kt`、`network/*`（ToggleModeRequest/SelectSlot/SetHotbarColor/HudMessage）、`config/ServerConfig.kt`、`command/PixelBeadCommand.kt`；最小模式标识（完整 HUD 在阶段四）。
**API**：附件注册 + `getData/setData`；`RegisterPayloadHandlersEvent.registrar("1").playToServer/playToClient`，handler 内 `enqueueWork` 改状态；拦截按下方矩阵；放置按决策 5；清理按下方触发器；`RegisterCommandsEvent` 管理命令。
**拦截矩阵（服务端权威，客户端镜像做预测/动画）**：
| 行为 | 闸门 |
|---|---|
| 放置/交互任意方块 | `RightClickBlock`：模式中 → DENY block+item；目标合法则改为拼豆放置 |
| 使用任意物品(食物/弓/桶/打火石) | `RightClickItem` 取消；`InteractionKeyMappingTriggered(isUseItem)` 客户端取消；进入模式 `stopUsingItem()`；兜底 `LivingEntityUseItemEvent.Start`（**待核对存在**） |
| 破坏（任意方块） | **不拦截**（2026-11-15 设定变更，原 `BreakBlockEvent` / `LeftClickBlock` / `BreakSpeed` 三道闸门已删除）→ 原版破坏 + 原版 loot |
| 实体交互 | `EntityInteract` 取消 |
| 攻击/使用/吸取键 | `InteractionKeyMappingTriggered` 模式中取消（pick-block 除外→吸取） |
| 空气点击 | `RightClickEmpty/LeftClickEmpty` 不可取消，无害（无目标）；挥手由 InteractionKeyMappingTriggered 抑制 |
| 纵深防御 | `UseItemOnBlockEvent` 模式中取消所有阶段（在 RightClickBlock 之后，正常不可达） |
所有 handler 守卫 `event.entity is ServerPlayer`（防假玩家）并按 `level.isClientSide` 分端；`BreakBlockEvent` 两端处理。
**飞行/清理**：按决策 8 授予/恢复；`forceExit(player, reason)` 统一清理路径，触发器 = `PlayerRespawnEvent`(死亡，附件已因无 copyOnDeath 重置)、`PlayerChangedDimensionEvent`、`EntityTeleportEvent`(珍珠/紫颂/命令)、`PlayerLoggedOutEvent`、`/pixelbead disable`、`ModConfigEvent.Reloading`(遍历在线玩家，若不再允许则退出)、服务器停止(附件持久化为 inactive)。
**正确性（最难）**：飞行恢复精确（只撤销授予的）；事件顺序（RightClickBlock 内 DENY + 我方放置同一 handler，绝不让原版 useItemOn 也跑）；进入模式中止进行中的物品使用；假玩家守卫；payload 不含坐标/颜色；附件仅服务端写。
**验收**：切换 → 服务端附件 active 且同步；生存进入获飞行、退出撤销（若进入前已有 mayFly 则保留）；手动跑完整拦截矩阵（石头放置/破坏、箱子/门/按钮/拉杆、食物/弓/桶/打火石、打实体）；创造放置∞/破坏不掉；生存放置精确扣 1（含快捷栏/副手搜索）、0 个 → 拒绝 + HUD；生存破坏掉自身；距离限制生效；死亡/切维度/珍珠/登出/管理禁用/配置热重载各 → 模式关 + 能力恢复；模拟领地（测试 handler 取消 `EntityPlaceEvent`）→ 放置被拒**且不消耗**。

### 阶段四 · HUD 与颜色选择器
**新建**：`client/key/*`、`client/hud/BeadHudLayer.kt`、`client/gui/ColorPickerScreen.kt`、`client/ClientPrefs.kt`、`config/ClientConfig.kt`、HUD/picker 纹理、lang。
**API**：`RegisterKeyMappingsEvent.registerCategory(KeyMapping.Category…)`+`register(KeyMapping…)`（**Category 工厂/KeyMapping ctor/SDL InputConstants 键码待核对**）；`ClientTickEvent.Post`+`consumeClick()` 处理模式/选择器键；`InputEvent.Key/MouseScrollingEvent/MouseButton.Pre/InteractionKeyMappingTriggered` 按决策 6；`RegisterGuiLayersEvent.registerAbove(VanillaGuiLayers.<hotbar 锚点>, id, BeadHudLayer)`（**锚点 + GuiGraphicsExtractor 绘制方法待核对**）；选择器是纯客户端 `Screen`（无 menu）+ 原版 `EditBox` 搜索。
**数据流**：HUD 读同步附件(槽/选择/active)+本地背包计数；选择器读 `Palette`+`ClientPrefs`；分配 → `SetHotbarColor` → 服务端校验 → 附件同步 → HUD 更新。最近使用为客户端本地（放置反馈/选择时更新），服务端仍只对玩法权威。
**正确性**：虚拟/真实快捷栏解耦（1–9/滚轮狂按后手持 ItemStack 与真实选中槽不变）；按键仅在无界面时生效；HUD 紧凑不挡准星；搜索大小写不敏感(`a15`→A15,`zg8`→ZG8)；收藏/最近重进保留；默认快捷栏 bootstrap 恰好一次（`hotbarInitialized=false` 时客户端发默认值，服务端置位）。
**验收**：HUD 9 槽+选择+色号+实时数量(创造∞)；1–9/滚轮切虚拟槽而真实物品栏不动（断言前后手持 ItemStack）；选择器 15 系导航/291 网格/搜索/HEX/收藏/最近/分配；重进世界**与**重进服务器 → 快捷栏/收藏/最近保留；吸取从准星拼豆设当前槽；原版控制里每个键可重绑无冲突。

### 阶段五 · 多人服务器与兼容性（硬化，无新模块）
**新建**：`runServer`+多客户端会话、可选 gametest（`RegisterGameTestsEvent`）覆盖放置/消耗/清理、打包(`gradlew build` jar)、README + 署名核对。
**验收（设计 §12 阶段五）**：
- 专用服务器 + 2 客户端：同处模式、同位置并发放置/破坏 → 单一服务端接受态，无刷物/丢失，各玩家消耗正确；拼豆经区块卸载/重载与服务器重启保留（原版区块存档）。
- 安全：模拟 hacked-client —— `SelectSlot(99)`、`SetHotbarColor("hack")`、配置禁用时切换、无模式放置请求 → 全部拒绝；放置 payload 无坐标/颜色可伪造；距离/边界强制。
- 领地：若有 26.3 兼容领地模组则装测（**风险：可用性**）；否则保留 `EntityPlaceEvent`/`BreakBlockEvent` 否决测试桩 → 保护区拒绝放置/破坏 + HUD 反馈、不消耗。
- 清理压力：模式中杀客户端进程 → 重进无飞行/锁定；飞行中死亡；模式中珍珠；切维度；管理禁用；运行中配置热重载(`enabled=false`,`survival_allowed=false`) → 受影响玩家干净退出。
- 配置矩阵：creative_allowed/survival_allowed/survival_flight_enabled/survival_consume_items 按设计 §8 表行为。
- 性能冒烟：放置数百拼豆 → FPS 稳定（共享模型），启动无注册/模型烘焙警告。

---

## 六、MVP 验收映射（设计 §13）
平台 26.3+NeoForge→阶段1；291 色/每色方块+物品/纯色完整立方体/创造分类→阶段1；工作台存在/15 染料→15 系/16+1→16→阶段2；模式进出/生存飞行/能力恢复/只放置破坏拼豆/禁普通物品交互/生存消耗掉落/创造不消耗不掉→阶段3；虚拟快捷栏不覆盖物品栏(机制在3落地)/291 选择器搜索快捷槽配置→阶段4；服务端权威多人/死亡传送切维度断线清理→阶段3 架构、阶段5 压测。

---

## 七、风险登记
| 风险 | 缓解 |
|---|---|
| 26.3 API 与旧教程差异大（Identifier 改名、BreakBlockEvent、codec tint、RenderPearl HUD 已确认变化） | 按第二节基线；其余标注“待本地核对”项在首次 `runClient`（MDG 本地反编译源码）时逐一核对 |
| 沙箱内 docs/maven 被墙、GitHub API 限流 | 所有 Gradle 执行在用户机器（有代理）；计划已钉到核实过的版本；实现期以本地源码复核 |
| KFF 6.5.0 ↔ NeoForge 26.3.0.99 ↔ Kotlin 2.4.21 ↔ Gradle 9 ↔ Java 25 栈兼容 | 元数据范围覆盖 26.3；Kotlin 插件对齐 2.4.21；阶段一先冒烟 `runClient` 再写玩法；回退：KFF issue / 钉更早 6.x |
| item-model tint JSON schema（RenderPearl 期）与 1.21.4 示例不同 | 生成器把 schema 集中一处；对照本地反编译 jar 的原版 26.3 assets 校验；方块侧 BlockTintSources API 已核实 |
| 291 注册性能 | 原版级注册量；共享模型/纹理；构建期生成资源；阶段一测启动耗时 |
| 领地模组兼容 | 放置走真实 BlockItem 管线(触发 EntityPlaceEvent)、破坏走 BreakBlockEvent；绝不裸 setBlock；即便无 26.3 领地模组也保留否决测试桩 |
| 飞行/状态清理边界（死亡 clone、使用中进入、gamemode 切换、热重载） | 单一 forceExit 路径；无 copyOnDeath；先记录后授予；阶段三跑全触发器、阶段五压测 |
| 输入拦截缝隙（InputEvent.Key 不可取消、Empty 不可取消） | 真实槽每 tick 还原；InteractionKeyMappingTriggered+RightClickItem 覆盖使用/攻击；空气点击无害；滚轮可取消 |
| 客户端预测不一致（拼豆在 RTT 后出现） | MVP 可接受；后续可加客户端预测（服务端纠正） |
| 收藏/最近配置保存语义 | 核对 ModConfigSpec 改即存；回退客户端本地附件（26.3 已核实特性） |
| CSV 上游漂移 | vendor CSV + NOTICE 钉 commit；构建期数量/色系校验硬失败 |

---

## 八、落地第一步（执行时）
1. 写 Gradle 三件套 + wrapper（Gradle 9.x）+ `neoforge.mods.toml` + `PixelBead.kt` 入口 + KFF 依赖。
2. vendor `color-data/mard-291.csv` + MIT LICENSE + NOTICE；写 `generateBeadResources` 与 `Palette`/`BeadColor` + `PaletteTest`。
3. 先只注册**少量**拼豆（或全 291）+ 共享模型/纹理 + tint，跑通 `gradlew runClient`，**对照本地反编译 26.3 源码核对第二节所有“待本地核对”签名**，确认颜色/放置/破坏/存档后再进入阶段二。
> 先让工具链与一个纯色方块在 26.3 真正跑起来，是排除环境/API 风险的最稳路径；其余阶段在此地基上增量叠加。

---

## 九、首次真实构建 / 运行时核实结果（2026-11-15）

工具链在本机通过代理 `127.0.0.1:7890`（`GRADLE_OPTS` 传 JVM 代理参数，**不改构建文件**）完整跑通：
`createMinecraftArtifacts` 反编译 7301 个 26.3 源文件到
`build/moddev/artifacts/minecraft-patched-26.3.0.99-sources.jar`，`./gradlew build` 成功
（291 资源生成 + 编译 + 单测 + jar + Gradle 9 配置缓存）。§2 中所有「待本地核对」均已对照真实源码核实。

实测修正（编译期与运行期两类）：
- **配置/命令**：`ModConfig.Type.SERVER` 已移除 → `SYNCED`；`CommandSourceStack.hasPermission(int)` →
  `permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`。
- **注册/序列化**：`DeferredRegister.Blocks/Items` 构造器 protected → `createBlocks/createItems`；
  `registerBlock` 的 `Supplier`/`UnaryOperator` 重载需显式 `Supplier`；附件 codec 用
  `RecordCodecBuilder.mapCodec`。
- **网络**：`ClientPlayNetworking` → `ClientPacketDistributor.sendToServer`；clientbound handler 走
  `RegisterClientPayloadHandlersEvent`（专用服务器不加载客户端类）。
- **客户端**：`GuiGraphicsExtractor.text/centeredText`、`mouseClicked(MouseButtonEvent, boolean)`、
  `VanillaGuiLayers.HOTBAR`、`Minecraft.gui.screen()/setScreen()`；`InputConstants.Type.KEYBOARD`
  + `InputEvent.Key.getKey()`（SDL scancode）+ `InputConstants.PRESS`；`Inventory.get/setSelectedSlot`；
  `KeyMapping.Category` 为 record（不再用 deprecated `register`）。
- **容器/菜单**：`SimpleContainer` 移到 `net.minecraft.world`；`addListener` 移除 → 覆写
  `setChanged()`；`placeItemBackInInventory` 需 `Prediction.SERVER_ONLY`；`containerSize` 为 Kotlin 属性。
- **放置管线（重要）**：`BlockItem.useOn()` 只调 `level.setBlock()`，不触发领地事件；必须走
  `ItemStack.useOn()` → `CommonHooks.onPlaceItemIntoWorld()` → `BlockEvent.EntityPlaceEvent`。
  同时 `RightClickBlock` 需显式 `isCanceled = true`（`setCancellationResult` 仅在取消时生效），
  并用 `BeadPlacement.isInternalPlacement()` 重入守卫放行自己的 `UseItemOnBlockEvent`。
- **tint**：`ItemTintSource.type()` 必须返回注册时同一个 `MapCodec` 实例；item-model `tints[].type`
  dispatch schema 已对照原版 `items/*.json` 核实。
- **数据包/构建**：reload 资源 id 必须带 `.json`；`generateBeadResources` 改为独立
  `Action<Task>`，否则 Gradle 9 配置缓存因捕获脚本对象而失败。
- **运行期（runServer 冒烟）**：`AddServerReloadListenersEvent` 是**游戏总线**事件
  （`AddClientReloadListenersEvent` 是 mod 总线），须注册到 `NeoForge.EVENT_BUS`；注册到
  modEventBus 会在专用服务器启动时抛 "not valid for this bus" 并崩溃。
- **26.3 数据包 schema（运行期实测）**：`recipe/dye_workbench.json` 第一次启动报
  `No key type in MapLike[{"tag":"minecraft:planks"}]` → ingredient 必须写成字符串 tag
  `"P": "#minecraft:planks"`（对照原版 `crafting_table.json`/`chest.json`）。`LootPool.CODEC`
  读的是 `LootItemCondition.CODEC.optionalFieldOf("condition")`，旧版
  `"conditions": [ ... ]` 无效 → 改为单个 `"condition": { "type": ... }`（对照原版 `dirt.json`）。
  已同步修正静态 `dye_workbench.json` 与 `build.gradle.kts` 生成的 291 张掉落表。
- **防回归**：新增 `ResourceSchemaTest`，遍历全 291 色，校验配方 ingredient 形式、掉落表
  `condition` 字段、self-drop、`items/*.json` 的 `tints[].type` dispatch、blockstate 模型指向，
  以及染料→色系映射的 15 条记录。
- **开发期加载警告画面（runClient）**：KFF 6.5.0 的 `neoforge.mods.toml` 仍声明 `logoFile`，
  NeoForge `LogoFileWarningsHandler` 在**非生产环境**会加一条 warning，客户端因此弹出
  "Warning while loading mods / 1 warning has occurred during loading" 拦截屏（可
  `Proceed to main menu`，不是崩溃、也不是本模组的问题）。该类源码明确提供开关
  `-Dneoforge.warnings.logofile.hide=true`，且 `FMLEnvironment.isProduction()` 时自动跳过，
  因此生产包根本不会出现。做法：在 `build.gradle.kts` 的 `runs { client/server }` 各加
  `systemProperty("neoforge.warnings.logofile.hide", "true")`（ModDevGradle `RunModel` DSL，
  实测写入 `build/moddev/clientRunVmArgs.txt` / `serverRunVmArgs.txt`），**只**屏蔽这一条
  dev-only 警告，真实加载错误仍会正常上报。

**专用服务器冒烟结果**：`prepareServerRun` 下载 5149 个文件（464 MiB），`run/eula.txt` 写入
`eula=true` 后 `runServer` 成功加载 mod 列表（KFF 6.5.0 / Minecraft 26.3 / NeoForge 26.3.0.99 /
Pixel Bead 0.1.0），输出 `Pixel Bead: loaded 291 MARD colors`，生成三个维度并到达
`Done (1.859s)! For help, type "help"`；`run/logs/latest.log` 中 `ERROR`/`FATAL`/解析失败为 0。
仅有原版命令歧义 WARN、离线模式 WARN（当时还有 KFF 自身 `logoFile` deprecated WARN，现已用上述开关消除）。
服务器为长驻进程，冒烟后手动终止，Gradle 因此把 `runServer` 记为 FAILED（非模组错误）。
`build/resources/main` 实点：292 blockstates + 292 item models + 292 loot tables（291 拼豆 + 工作台）。

**客户端启动冒烟结果**：`prepareClientRun` + `runClient` 在本机真实启动（SDL / LWJGL 3.4.3 /
OpenGL 3.3 NVIDIA 610.88），mod 列表含 Pixel Bead 0.1.0，输出
`Pixel Bead: loaded 291 MARD colors`，`Reloading ResourceManager: ... mod/pixelbead ...` 完成，
字体、全部纹理图集、OpenAL 正常，抵达主菜单；`ERROR`/`FATAL` 为 0、无新增 crash-report。
这覆盖了此前只靠源码核对的**客户端注册链路**：`RegisterColorHandlersEvent.ItemTintSources`、
按键/分类、GUI layer、menu screen、clientbound payload handler 均无运行期异常。
早期 `run/crash-reports/crash-2026-11-15_05.34.36-fml.txt` 记录的是已修复的 mod bus 注册错误
（外加 KFF 的 `logoFile` 警告被计入 loading issue），修复后不再产生新崩溃报告。
加上警告开关后再次启动实测：日志中 `logoFile` 与 `Mods loaded with N warning(s)` 均为 0，
`ERROR`/`FATAL` 为 0，直接抵达主菜单，警告拦截屏消失。后续还实际新建并进入世界
（`New World`，`Dev joined the game`），资源重载与区块渲染期间仍无模型/tint 解析错误，
说明 291 个 `items/*.json` 的 `tints[].type` dispatch 在运行期确实被接受。
冒烟后手动关闭窗口，Gradle 同样把长驻的 `runClient` 记为 FAILED（非模组错误）。
日志副本：`build/server-smoke-latest.log`、`build/client-smoke-latest.log`（`build/` 已忽略）。

### 运行期实测 bug：拼豆模式下左右键全部失效（已修）
- **现象**：按 B 进入拼豆模式（HUD 正常、附件同步正常）后，左键与右键都无任何效果。
- **根因**：`BeadKeyHandler.onInteractionKey` 在 active 时对
  `InputEvent.InteractionKeyMappingTriggered` 一律 `isCanceled = true`（只放行 pick block）。
  26.3 原版 `Minecraft.startAttack()` / `startUseItem()` 在该事件被取消时**立即 return**
  （`continueAttack()` 也被短路），于是：
  - 左键不发 `START_DESTROY_BLOCK`、不调 `continueDestroyBlock` → 服务端 `BreakBlockEvent` 永不触发；
  - 右键不调 `MultiPlayerGameMode.useItemOn()` → 不发 `ServerboundUseItemOnPacket`
    → 服务端 `RightClickBlock` 永不触发 → `BeadPlacement.place()` 永远不执行。
  这与 `BeadModeEvents.onRightClickBlock` 注释里"客户端仍会发 ServerboundUseItemOnPacket"的假设
  直接冲突：该假设只在 `startUseItem()` 真的被调用时成立，而输入级取消提前掐断了它。
  （已核对 `ServerGamePacketListenerImpl.handleUseItemOn`：只要包到达就会
  `ackBlockChangesUpTo(sequence)`，取消 `RightClickBlock` 不会卡预测队列，所以问题确在客户端输入层。）
- **修复**：`onInteractionKey` 只取消 `isPickBlock`（中键会改动真实物品栏，需与虚拟快捷栏解耦），
  attack/use 全部放行；所有交互过滤交给服务端事件链（`RightClickBlock` / `RightClickItem` /
  `EntityInteract` / `UseItemOnBlockEvent`；破坏类事件已按 2026-11-15 的设定变更移除）。
  用 Java 25 `javap` 复核字节码：仅 `isPickBlock()` 分支调用 `setCanceled(true)`。
- **配套**：`BeadPlacement` 所有早退路径改为 `deny(player, key)` 发 HUD 原因，新增
  `denied` / `no_color` / `blocked` 三个中英文 lang key（原 `insufficient` 保留），
  `ResourceSchemaTest` 增加 1 项校验 4 个 key 在 `en_us`/`zh_cn` 都存在。
- **别误判为 bug**：生存模式背包无对应拼豆时右键会被拒并提示"背包中拼豆不足"；
  放置目标不可替换时提示"无法在此放置"。复测请切创造模式或先备好拼豆。
  （左键破坏已在 2026-11-15 解禁，见下一节。）

### HUD / 选择器 UX 优化（2026-11-15，用户实测反馈）
- **反馈三点**：① 一排 9 个色块太少，要两排；② 按 P 选颜色时看不到 HUD 选中槽的预览，
  选择器与 HUD 割裂；③ 选中槽高亮变成纯白，原颜色被遮住。
- **③ 根因**：`BeadHudLayer` 用 `graphics.fill(...)` 画了一个**实心白矩形**当高亮，
  直接盖在色块上；`ColorPickerScreen` 选中颜色格也是同样的实心白框。26.3 的
  `GuiGraphicsExtractor.outline(x, y, w, h, color)` 正好提供 1px 描边，改为双层描边
  （深色外环 + 浅/青色内环），在浅色与深色拼豆上都清晰，且完全不遮挡颜色。
- **① 两排 18 槽**：`BeadModeState.SLOTS_PER_ROW=9 / SLOT_COUNT=18`，`DEFAULT_HOTBAR` 扩到 18 个
  已核对在 `mard-291.csv` 中存在；`selectSlot`/`setHotbarColor` 范围改 `0 until SLOT_COUNT`；
  `BeadKeyHandler`：1-9 → 第一排，`e.keyEvent.hasShiftDown()` → 第二排（26.3
  `InputWithModifiers.hasShiftDown()`），滚轮在 18 槽循环。
  **向后兼容**：`BeadModeState.normalized()` 把旧的 9 元素存档补齐/截断为 18，
  由 `BeadModeManager.state()/setState()` 统一调用，老存档不会出现灰色空槽或越界。
- **② 联动预览**：新增 `BeadHotbarView`（`client/hud`）作为**唯一绘制来源**，
  HUD 与选择器都调 `draw()`，共享 `BeadHudLayer.previewColor()` 客户端预览状态
  （不写服务端附件）。打开 P 时预选当前槽颜色；点击颜色格 → 立即在镜像条与 HUD 选中槽预览
  （青色描边 + "预览中"）；点击槽位 → 发 `SetHotbarColor` + `SelectSlot`，把 HUD 选中槽
  一起切过去，二者不再割裂；`Screen.added()/removed()` 负责清理预览，resize 保留搜索词与选中色。
- **资源/测试**：`assign_hint` 文案更新，新增 `gui.pixelbead.picker.preview` 中英文；
  `ResourceSchemaTest` 扩展为校验全部 6 个 UI/message key，并新增 18 个默认色号存在性校验
  （测试里写死 id 是刻意的，避免纯 JVM 单测加载 `BeadModeState` companion 里的网络类）。

### 拼豆模式沉浸感 + 选择器连续填色（2026-11-15，用户二次反馈）
- **需求 1**：进入拼豆模式后隐藏原版物品栏，并让玩家手上显示 HUD 选中的拼豆。
- **需求 2**：选择器流程反转为「先选槽 → 再点颜色」，且每次写入后光标自动跳到下一槽，
  便于照着色表连续选色。
- **需求 1 实现（关键：不改真实物品栏）**：新增 `client/render/BeadFirstPersonView.kt`。
  - 隐藏物品栏：`RenderGuiLayerEvent.Pre` 对 `VanillaGuiLayers.HOTBAR` 与
    `SELECTED_ITEM_NAME` 取消（已核对 `GuiLayerManager.renderInner`：Pre 取消只跳过当前 layer，
    我们注册在 hotbar 之上的 `BeadHudLayer` 仍会渲染）。
  - 手持拼豆：`RenderHandEvent` 只可取消、**不能换 ItemStack**，但 26.3
    `GameRenderer` 把 `playerState.firstPersonHandsAndItems` 原样传给
    `FirstPersonHandsAndItemsRenderer`，且 `submitArmWithItem()` 在 hook **之后**重新读取
    `state.mainHandItem`。因此在事件里改写 `mainHandItem` + 用
    `ItemModelResolver.updateForTopItem()` 重建 `mainHandRenderState`，原版渲染器就会用完整
    挥动/装备变换画出拼豆；副手置 `ItemStack.EMPTY` 只画手臂。全程不动 `Inventory`，
    服务端零感知，虚拟快捷栏与真实物品栏继续保持解耦。
  - 手持颜色取 `BeadHudLayer.previewColor() ?: state.selectedColorId()`，所以在选择器里
    hover 颜色时手上也会实时变化。
- **需求 2 实现**：`ColorPickerScreen` 引入 `editSlot`（金色光标）、`localHotbar`（乐观镜像）、
  `initialized`（resize 不重置）。点槽位 → 设 editSlot + 发 `SelectSlot` 同步 HUD；点颜色 →
  发 `SetHotbarColor(editSlot, id)` + 本地立即写入 + `editSlot=(editSlot+1)%18` + 发
  `SelectSlot(editSlot)` 让 HUD 高亮同步前进。镜像条只显示真实/乐观颜色，hover 预览交给
  HUD（青色框），避免"未提交的预览色"被误认为已填入下一槽。
  `BeadHotbarView.draw` 扩展 `previewSlot`/`highlightSlot`/`highlightColor`/`hotbarOverride`
  四个参数，HUD 与选择器继续共用同一渲染器。
- **顺带**：颜色网格按可用高度截断行数并显示 `shown / total`，避免搜索 291 色时网格溢出
  盖住底部镜像条。

### 收尾简化：状态栏全隐藏 + HUD 贴底 + 去掉 HUD/picker 联动（2026-11-15，用户三次反馈）
- **需求 1**：血条/经验/饱食度也去掉，模组 HUD 几乎贴窗口底部、只留一丝空隙。
  - `BeadFirstPersonView.HIDDEN_LAYERS` 由 2 个扩为 10 个：除 `HOTBAR` /
    `SELECTED_ITEM_NAME` 外，加 `PLAYER_HEALTH`、`ARMOR_LEVEL`、`FOOD_LEVEL`、`AIR_LEVEL`、
    `VEHICLE_HEALTH`、`EXPERIENCE_LEVEL`、`CONTEXTUAL_INFO_BAR`、
    `CONTEXTUAL_INFO_BAR_BACKGROUND`。准星、药水效果、聊天、Boss 条**保留**（瞄准放置仍需准星）。
    过滤仍先按 layer id 再查 `state.active`，避免每帧对每个 layer 读附件。
  - `BeadHotbarView.BOTTOM_GAP = 3`；`BeadHudLayer` 的 `y0` 从 `height - 60 - ROW_STRIDE`
    改为 `height - HEIGHT - BOTTOM_GAP`，色条底部离窗口底边 3px。原版状态栏已隐藏，
    该区域完全空出。label / toast 仍在上排上方 12 / 24px。
- **需求 2**：打开 P 时关闭游戏内 HUD，并去掉二者联动，整体简化。
  - `BeadHudLayer.render` 开头 `if (mc.gui.screen() is ColorPickerScreen) return`。
  - 删除整套预览状态：`BeadHudLayer.previewColorId` / `setPreviewColor()` / `previewColor()`、
    `BeadHotbarView.PREVIEW_COLOR`、`draw()` 的 `previewColorId`/`previewSlot` 参数、
    `ColorPickerScreen.added()` / `removed()` 里清预览的逻辑及全部 `setPreviewColor` 调用、
    `BeadFirstPersonView` 里 `previewColor() ?:` 的手持联动（改为固定 `selectedColorId()`）。
    `hoverColorId` 只保留用于选择器本地信息行显示色号/HEX。
  - 删除已无用的 `gui.pixelbead.picker.preview` 中英文 lang key，`ResourceSchemaTest`
    的 key 校验同步减为 5 个。
  - 结果：HUD 与选择器是**两个独立界面**，共用 `BeadHotbarView` 绘制器但不共享任何运行期状态；
    选择器内部仍保留 `editSlot` 金色光标 + `localHotbar` 乐观镜像 + 自动前进。

### 布局微调：色块紧凑 + 底部 2px + 选择器镜像条同样贴底（2026-11-15，用户第四次反馈）
- **原实现的偏差**：上一轮只调了整体底部间距（3px），但槽内仍留 `SLOT_SIZE=18 / SWATCH=16`
  的 2px 横缝与 `ROW_STRIDE=20` 的 4px 纵缝，观感仍是"松散的格子"；选择器的镜像条则独立
  使用 `height - 30 - HEIGHT`，没有贴底。
- **间距收紧**：`BeadHotbarView` 新增 `GAP = 1`，`SLOT_SIZE = SWATCH + GAP = 17`、
  `ROW_STRIDE = 17`、`WIDTH = COLS*SLOT_SIZE - GAP = 152`、`HEIGHT = 17 + 16 = 33`。
  色块之间只剩 1px 通道，整条读起来是一块紧凑色带。
- **底部 2px**：`BOTTOM_GAP` 由 3 改 2；`BeadHudLayer` 与 `ColorPickerScreen.hotbarY()` 都用
  `height - HEIGHT - BOTTOM_GAP`，两处共用同一条placement 规则，选择器镜像条与游戏内 HUD
  一样贴底。
- **选中框重设计**（1px 通道下原有外扩 2px 双环会吃掉邻居）：深色环退到 `-1` 正好填满通道
  提供对比，彩色环改为画在色块**内侧**（`outline(x, y, SWATCH, SWATCH, color)`），
  保留中心 14×14 本色，且不越界覆盖相邻色块。
- **验证**：`./gradlew build` → BUILD SUCCESSFUL，22 项测试全过；`javap -constants` 复核
  编译产物为 `SLOT_SIZE=17 / ROW_STRIDE=17 / WIDTH=152 / HEIGHT=33 / BOTTOM_GAP=2`。

### 设定变更：解除「不能破坏非拼豆方块」（2026-11-15，用户要求废弃该限制）
- **动机**：拼豆模式只能拆拼豆方块，做地形整理/拆除时非常别扭，用户明确要求废弃该设定。
- **删除的三道闸门**（原设计 §6.7）：
  - `PlayerInteractEvent.LeftClickBlock`：非拼豆 → 取消（顺带止裂纹）。
  - `BreakBlockEvent`：非拼豆 → 取消（服务端真正的闸门）。
  - `PlayerEvent.BreakSpeed`：非拼豆 → 取消（兜底）。
  三个 handler 整体移除，同时清理 `BeadBlocks` / `BreakBlockEvent` 的 import。
- **结果**：左键对拼豆方块与普通方块表现完全一致，均走原版破坏与掉落，挖掘速度不干预；
  创造模式仍不掉落。**放置**（右键恒定放拼豆）与**使用**（箱子/门/桶/食物等）限制保持不变。
- **`BeadBlocks.isBead()`** 变成无调用方，但保留（标记类 `BeadBlock` 仍用于 O(1) 判定和将来用途）。
- **文档同步**：`SPEC.md` §1.1 / §6.1 / §6.4 / §6.5 / §6.7 / §8 表格 / 阶段三验收 / §13 清单 /
  设计基线总结全部改写，并标注该条款已废弃。
- **验证**：`./gradlew build` → BUILD SUCCESSFUL，22 项测试全过。

### HUD 停靠可配置（2026-11-15，用户要求「下方 / 左右，左右时一列」）
- **新枚举** `com.pixelbead.config.HudPosition { BOTTOM, LEFT, RIGHT }`，放在 config 包（两侧都会
  加载，客户端代码仍隔离在 `com.pixelbead.client`）。`ClientConfig` 新增
  `hud.position = defineEnum(...)`，默认 `BOTTOM`；`ClientPrefs.hudPosition()/setHudPosition()`
  负责读写并 `SPEC.save()`。
- **几何改为按列数驱动**（`BeadHotbarView`）：删掉固定的 `COLS/ROWS/SLOT_SIZE/WIDTH/HEIGHT`，
  统一成 `CELL = SWATCH + GAP = 17` 与一组 `rowsFor/widthFor/heightFor/slotX/slotY/originX/
  originY/columns`。`BOTTOM` → 9 列（9×2）；`LEFT/RIGHT` → 1 列（18 行，305px），窗口高度不足
  309px 时自动退化 2 列 × 9 行，避免底部槽位被裁掉。
- **HUD 定位**：`originX` 按停靠侧贴边 2px，`originY` 底部贴底 / 两侧垂直居中；色号与 toast
  文本在下锚时堆在上方，侧锚时放到列旁边（右锚右对齐，用 `font.width` 反推 x）。
- **P 界面配置面板**：调色板右侧空白区（`GRID_X + 16*CELL + 18`，窄窗口夹到
  `width - PANEL_W - 6`）画一个小面板：标题 `gui.pixelbead.hud_position` + 三个按钮
  （下方/左侧/右侧），当前项高亮；点击写入 `ClientPrefs` 并立即生效。点击面板区域会被吞掉，
  不会穿透到色板。
- **按用户标注删除**：镜像条上方的 `editSlot+1 / 18` 计数与 `gui.pixelbead.assign_hint` 提示行
  一并移除，并把该 lang key 从 `generateBeadResources` 与 `ResourceSchemaTest` 中清掉，新增
  4 个 hud 相关 key 校验。
- **边界**：选择器的镜像条固定底部 9×2（它是编辑面板，侧锚会与配置面板撞位）。
  （该内嵌面板已在同日后续改动中被「设置按钮 + 原生配置页」取代，见本文件末尾一节。）
- **验证**：`./gradlew build` → BUILD SUCCESSFUL（22 项测试）；`runClient` 冒烟 0 ERROR/FATAL，
  `pixelbead-client.toml` 正确生成 `[hud] position = "BOTTOM"`（首次会有一条 "not correct.
  Correcting" 的 WARN，属新增配置项的自动写入）。

### 设置按钮 + 原生配置页（2026-11-15，用户要求「去掉直接展示的设置项，改成原生 UI 配置页」）
- **删除**：`ColorPickerScreen` 内嵌的 HUD 设置面板（`drawSettings` / `panelX` / `panelY` /
  `PANEL_*` 常量，以及 `mouseClicked` 里的面板命中分支）整体移除；选择器不再直接展示任何设置项，
  色板右侧恢复为纯空白区。
- **新增按钮**：选择器右上角一个原版 `Button`（54×20，位置 `width - 54 - 8, 4`，文案
  `gui.pixelbead.options.open`「设置」），点击 `mc.gui.setScreen(PixelBeadOptionsScreen(this, mc.options))`。
  按钮是真实 widget，键盘/朗读/悬停高亮全部由原版提供。
- **新屏幕** `client/gui/PixelBeadOptionsScreen.kt`：继承 `OptionsSubScreen`，在 `addOptions()` 里用
  `OptionsList.addHeader` + `addBig(CycleButton…)`，与「视频设置」完全同一套骨架（标题头、滚动
  列表、底部「完成」、`applyUnsavedChanges` 全由原版处理）。当前两项：
  `HUD 位置`（下方/左侧/右侧，`CycleButton<HudPosition>`）与 `显示 HUD`（`onOffBuilder`）。
  改动即时写入 `ClientPrefs` 并 `SPEC.save()`，无需 Apply——HUD 每帧重读 `ClientPrefs`。
- **模组列表入口**：`PixelBeadClient.onClientSetup(FMLClientSetupEvent)` 里
  `registerExtensionPoint<IConfigScreenFactory>(...)`，使「模组 → Pixel Bead → 配置」进入同一页。
  注意 Kotlin 不会把 lambda 直接转成类型参数 `T`，必须写显式 SAM 构造 `IConfigScreenFactory { … }`。
  注册放在 `Dist.CLIENT` 的订阅者里，专用服务器不会加载该类。
- **枚举归位**：`HudPosition` 增加 `translationKey`（纯字符串，无 MC 依赖），选择器与配置页共用；
  删掉 `ColorPickerScreen` 内的 `labelKey()` 扩展。
- **语言键**：新增 `gui.pixelbead.options` / `options.open` / `options.hud` /
  `options.hud_position` / `options.hud_enabled`，删除旧的 `gui.pixelbead.hud_position`；
  `ResourceSchemaTest` 的键清单同步替换。
- **HUD 隐藏条件**：`BeadHudLayer` 在 `ColorPickerScreen` **或** `PixelBeadOptionsScreen` 打开时
  都不绘制，避免配置页上叠一条 HUD。
- **验证**：`./gradlew build` → BUILD SUCCESSFUL（22 项测试全过）；`runClient` 冒烟 0 ERROR/FATAL。

### 色块三档尺寸（2026-11-15，用户要求「给色块尺寸设置三种大小」）
- **新枚举** `config/HudSwatchSize.kt { SMALL(12), MEDIUM(16), LARGE(20) }`，带 `translationKey`
  （`gui.pixelbead.size.small/medium/large`），默认 `MEDIUM`（与既有观感一致）。
- **配置**：`hud.swatch_size`（`defineEnum`）；顺手删掉从未被使用的 `hud.scale`（避免误解）。
  `ClientPrefs.swatchSize()/setSwatchSize()`。
- **几何参数化**（`BeadHotbarView`）：删掉固定的 `SWATCH/CELL`，保留 `GAP = 1`，新增
  `cellFor(size) = size.swatch + GAP`；`columns/widthFor/heightFor/slotX/slotY/originX/originY/
  slotAt/draw` 全部加 `size` 参数。尺寸变化时 `widthFor/heightFor` 自动跟着变：
  9 列宽 116 / 152 / 188，2 行高 25 / 33 / 41；侧栏一列高 232 / 305 / 377。
- **侧栏退化**：`columns(position, guiHeight, size)` 用当前尺寸的真实高度判断，窗口不够一列
  （如 271 高的窗口 + LARGE 377）自动退化为 2 列 × 9 行。
- **共享**：HUD 与选择器镜像条读同一个 `ClientPrefs.swatchSize()`，两处几何永不漂移；选择器
  `hotbarX/hotbarY/slotAt/draw` 与「Shift+1-9」提示行偏移都改用 `cellFor(size)`。
- **配置页**：第三项 `色块大小：小/中/大`（`CycleButton<HudSwatchSize>`），点一下立即生效。
- **未跟随**：选择器左侧的 291 色主网格仍是固定 16px（`picker.grid_size` 另有配置），
  本次只改 HUD/镜像条色块。
- **验证**：`./gradlew build` → BUILD SUCCESSFUL（22 项测试全过）；`runClient` 冒烟 0 ERROR/FATAL。


**最终回归**：`./gradlew build` → `BUILD SUCCESSFUL`，
`PaletteTest 7 + WorkbenchLogicTest 8 + ResourceSchemaTest 7 = 22` 项全通过，
产物 `build/libs/pixel-bead-0.1.0.jar`。

仍待进游戏人工验证（自动化冒烟无法替代）：颜色/tint 一致性、HUD/选择器布局、
隐藏物品栏后的观感、第一人称手持拼豆、按键重绑、工作台 GUI、
完整交互拦截与死亡/切维度/登出清理矩阵。