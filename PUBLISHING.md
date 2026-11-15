# 发布手册 — Pixel Bead 0.1.0-rc.1

面向 Modrinth / CurseForge / 国内平台 / GitHub Releases 的字段映射、依赖声明与合规清单。
资料改动的单一来源是 `gradle.properties` 与 `META-INF/neoforge.mods.toml`，本文件只是
照抄给各平台表单。

## 0. 发布前状态

| 项 | 状态 |
|---|---|
| 版本号 | `0.1.0-rc.1`（`gradle.properties` → `mod_version`） |
| 产物 | `build/libs/pixel-bead-0.1.0-rc.1.jar` |
| Mod ID | `pixelbead`（**不是** `pixel_bead`，与现有资源/存档命名空间一致） |
| 图标 | `src/main/resources/assets/pixelbead/icon.png`（512×512、291 色块螺旋、约 45 KB） |
| 前置 | Kotlin for Forge **6.5.0+**，**未随包**，必须在平台依赖里单列 |
| 许可 | MIT（仓库根目录 `LICENSE`） |

> 图标由 `tools/make_icon.py` 从 `color-data/mard-291.csv` 计算生成，可直接用于所有平台
> 的项目页；仓库与 jar 内不含第三方图像素材。来源说明与复现命令见 [NOTICE.md](NOTICE.md)
> 第 4 节。

## 1. 通用资料

| 字段 | 值 |
|---|---|
| 名称 | `Pixel Bead` |
| 中文名称 | `像素豆` |
| Slug | `pixel-bead`（上架前确认未被占用） |
| 摘要（中） | 291 色 MARD 拼豆方块、拼豆染色工作台，以及服务端权威的 18 槽虚拟色带建造模式。 |
| 摘要（英） | 291 MARD bead colours, a dye workbench, and a server-authoritative bead building mode with a virtual 18-slot colour bar. |
| 宣传语 | 一颗一色，拼出世界。 / One bead, one colour — build your world. |
| 吉祥物 | 白色小狗（White puppy） |
| 分类（Modrinth） | `decoration`, `utility` |
| 分类（CurseForge） | `Decoration`, `Miscellaneous` |
| 环境 | `client_and_server`（客户端与服务端都需要） |
| 游戏版本 | `26.3` |
| 加载器 | `NeoForge` |
| 许可证 | `MIT`（SPDX） |
| 源码 | https://github.com/mahoo12138/pixel-bead |
| 问题追踪 | https://github.com/mahoo12138/pixel-bead/issues |
| 许可证链接 | https://github.com/mahoo12138/pixel-bead/blob/main/LICENSE |
| 版本号 | `0.1.0-rc.1` |
| 版本类型 | Beta / Pre-release（不要标 Release） |
| 文件名 | `pixel-bead-0.1.0-rc.1.jar` |
| 变更日志 | `CHANGELOG.md` → `#010-rc1` |

### 依赖（必须逐条填写）

| 依赖 | 版本范围 | 必填 | 说明 |
|---|---|---|---|
| Minecraft | `26.3` | 是 | 只支持 26.3.x |
| NeoForge | `26.3.0.99+` | 是 | 加载器 |
| Kotlin for Forge | `6.5.0+` | 是 | **不随 jar 打包**，需单独下载 |

### 长描述（可直接粘贴）

```markdown
**像素豆 (Pixel Bead)** — 一颗一色，拼出世界。

291 色 MARD 拼豆方块、把普通方块批量转换成拼豆的染色工作台，以及一套服务端权威的拼豆
建造模式。虚拟 18 槽色带不占用真实背包，放置走原版方块管线，领地与保护模组仍可逐格否决。

**功能**
- 291 色 MARD 拼豆方块，按色系与色号排序，创造模式标签页直达
- 拼豆染色工作台：16 方块 + 1 染料 → 16 拼豆
- 拼豆模式：18 槽虚拟色带（2×9）、完整颜色选择器（色系 / 搜索 / 收藏 / 最近）
- 笔刷工具：拖动描边、左键橡皮、Z 撤销、`~` 两点快速形状（线性/平面 × 放置/消除）
- HUD 可停靠底部或左右侧边，色块三档尺寸；第一人称显示当前颜色拼豆
- 中英文本地化；服务端与客户端配置分离

**环境**：Minecraft 26.3 · NeoForge 26.3.0.99+ · Kotlin for Forge 6.5.0+ · Java 25
**许可**：MIT（代码）；MARD 291 色彩数据 © MakeBead，MIT
```

## 2. Modrinth

| 字段 | 值 |
|---|---|
| Project type | `Mod` |
| Title | `Pixel Bead` |
| Summary | 用上面的英文摘要（单句、无格式） |
| Body | 上面的长描述（英文版可自行翻译，保留依赖段） |
| Icon | `icon.png`（同上，512×512） |
| Gallery | 只放真实游戏截图；不要放渲染图或概念图 |
| License | `MIT` |
| Source / Issues | 同通用资料 |
| Version number | `0.1.0-rc.1` |
| Release channel | `Beta` |
| Loaders | `NeoForge` |
| Game versions | `26.3` |
| Dependencies | NeoForge（required）、Kotlin for Forge（required） |

### 正文需要补的来源说明

正文末尾加一段（英文版自行翻译）：

```markdown
Colour data: MARD 291 palette © MakeBead, MIT — vendored from makebead/craft-color-codes.
The project icon is generated from that palette by `tools/make_icon.py` (deterministic,
standard library only). No third-party artwork is bundled.
```

Modrinth 的素材来源类问题按实际情况填「无第三方素材」即可：jar 内除上述生成图标外没有
图像、音频或字体资源。

## 3. CurseForge

| 字段 | 值 |
|---|---|
| Project name | `Pixel Bead` |
| Summary | 英文摘要 |
| Category | `Decoration`（主）、`Miscellaneous`（次） |
| Game version | `26.3` |
| Mod loader | `NeoForge` |
| License | `MIT` |
| Release type | `Beta` |
| File name | `pixel-bead-0.1.0-rc.1.jar` |
| Relations | NeoForge（required）、Kotlin for Forge（required） |

描述末尾加来源说明：

> 色彩数据：MARD 291 色卡 © MakeBead，MIT；项目图标由该色卡经脚本计算生成。模组为独立
> 社区项目，与 Mojang、Microsoft、NeoForged 无关联。

## 4. 国内平台（MC百科等）

| 字段 | 值 |
|---|---|
| 中文名称 | 像素豆 |
| 英文名称 | Pixel Bead |
| 支持版本 | Minecraft 26.3 |
| 加载器 | NeoForge 26.3.0.99+ |
| 前置 | Kotlin for Forge 6.5.0+ |
| 简介 | 291 色 MARD 拼豆方块 + 染色工作台 + 18 槽虚拟色带建造模式；一颗一色，拼出世界。 |
| 图标 | `icon.png`（512×512）；简介里注明色彩数据来源 |
| 下载 | GitHub Releases 直链；网盘镜像自行补充 |

## 5. GitHub Releases

- Tag：`v0.1.0-rc.1`
- Title：`Pixel Bead 0.1.0-rc.1`
- 勾选 **Set as a pre-release**
- 附件：`build/libs/pixel-bead-0.1.0-rc.1.jar`
- 说明：粘贴 `CHANGELOG.md` 的 `[0.1.0-rc.1]` 段落，并补一句"需要 Kotlin for Forge 6.5.0+"

## 6. 平台通用免责声明

英文：

```text
NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
Minecraft is a trademark of Mojang Studios / Microsoft. Pixel Bead is an independent,
community-made mod and is not affiliated with NeoForged or Kotlin for Forge.
```

中文：

```text
本项目不是 Mojang Studios 或 Microsoft 的官方产品，未获其批准或关联。Minecraft 及相关
商标归其各自权利人所有。Pixel Bead 为独立社区模组，与 NeoForged、Kotlin for Forge 无
隶属或背书关系。
```

## 7. 构建与自检

```bash
# 需要代理时按 README 传 GRADLE_OPTS
./gradlew build
ls -l build/libs/pixel-bead-0.1.0-rc.1.jar

# 校验 jar 内元数据（版本、作者、图标、许可文件）
python - <<'PY'
import zipfile
z = zipfile.ZipFile("build/libs/pixel-bead-0.1.0-rc.1.jar")
print(z.read("META-INF/neoforge.mods.toml").decode("utf-8"))
print([n for n in z.namelist() if n.startswith("META-INF/")])
print([n for n in z.namelist() if n.endswith("icon.png")])
PY
```

发布前逐项确认：

- [ ] `neoforge.mods.toml` 里 `version="0.1.0-rc.1"`，jar 文件名一致
- [ ] `iconFile="pixelbead:icon.png"` 且 jar 内存在 `assets/pixelbead/icon.png`
- [ ] `META-INF/LICENSE` 与 `META-INF/NOTICE.md` 已随包
- [ ] 图标 SHA-256 与 `NOTICE.md` 记录的一致（`python tools/make_icon.py` 可重生成）
- [ ] 依赖里写明 Kotlin for Forge（用户最容易漏装）
- [ ] 各平台图标均上传 `icon.png`，正文含 MARD 色彩数据来源
- [ ] 所有页面没有"官方 Minecraft 产品""Mojang 关联"等表述

## 8. 发布后验证

1. 用干净实例安装 NeoForge 26.3.0.99 + Kotlin for Forge 6.5.0，再放本 jar。
2. Mods 列表应显示 `Pixel Bead 0.1.0-rc.1` 与 291 色块图标，无加载警告。
3. 进入世界，确认创造标签页有 291 色拼豆、工作台可合成、`P` 可打开颜色选择器。
4. 平台版本页确认依赖、游戏版本、加载器、Beta 标记均正确。
