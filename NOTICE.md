# NOTICE — 授权范围、第三方素材与图标来源

本文件说明 Pixel Bead / 像素豆 的授权范围与第三方素材归属。它是 [LICENSE](LICENSE)
的补充说明；两者冲突时，以本文件对第三方素材的说明为准。

> 以下为项目合规说明，不构成法律意见。

## 1. 项目标识

| 项目 | 值 |
|---|---|
| 中文名称 | 像素豆 |
| 英文名称 | Pixel Bead |
| 宣传语 | 一颗一色，拼出世界。 |
| 项目描述 | Minecraft 像素拼豆创作模组 |
| 吉祥物 | 白色小狗 |
| Mod ID | `pixelbead` |
| Git 仓库 | <https://github.com/mahoo12138/pixel-bead> |
| 当前版本 | `0.1.0-rc.1` |

## 2. 授权范围

- **源代码、构建脚本、资源生成器**：MIT，© 2026 mahoo12138，全文见 [LICENSE](LICENSE)。
  这包括 `tools/make_icon.py` 与第 4 节描述的图标生成流程。
- **MARD 291 色彩数据**：MIT，© 2026 MakeBead，**不是**本项目版权。详见
  [color-data/NOTICE.md](color-data/NOTICE.md) 与 [color-data/LICENSE](color-data/LICENSE)。
  构建期由 `generateBeadResources` 从 `color-data/mard-291.csv` 派生出调色板 JSON 与
  291 组方块/物品/战利品表/语言文件；这些派生产物随项目以 MIT 分发。
- **本项目图标**：由 `tools/make_icon.py` 从上述色彩数据计算生成，随项目以 MIT 分发，
  不包含任何第三方图像素材。详见第 4 节。
- **Minecraft、NeoForge、Kotlin for Forge**：本项目不包含也不再分发它们的代码。NeoForge
  以 LGPL-2.1 授权，Kotlin for Forge 以其自身许可证授权；它们作为运行时前置单独安装。

## 3. 商标与免责声明

Pixel Bead 是独立的社区模组，**不是** Mojang Studios、Microsoft、NeoForged 或
Kotlin for Forge 的官方产品，也未获得其认可或关联。Minecraft 及相关名称、商标、素材归
其各自权利人所有。

MARD 是拼豆厂商的品牌名称；本项目仅以指称方式使用它来描述色号体系。`mard-291.csv` 中的
HEX/RGB 是屏幕参考值，来自公开色卡整理，**不是**厂商实测值；不同色卡、批次、材质与显示器
可能存在差异（详见 `color-data/NOTICE.md`）。

## 4. 图标与美术资源

### 4.1 随包图标 `assets/pixelbead/icon.png`

`src/main/resources/assets/pixelbead/icon.png`（512×512、8-bit 真彩、约 45 KB）是一张
**由代码计算生成的抽象图形**：291 颗方形色块按黄金角（137.5°）螺旋排布，每颗对应
`mard-291.csv` 里的一个色号，色号顺序决定半径与色相，因此 15 个 MARD 色系在图上形成
螺旋色带。底色为暖纸色渐变，每颗色块自带按自身色相压暗的描边，保证深浅色都能分离。

该图不含任何摄影、绘画、字体或第三方图像输入，也不带任何附加元数据块（无 EXIF、文本块）。

### 4.2 生成方式（可复现）

```bash
# 512×512，写入 jar 图标位置
python tools/make_icon.py

# 需要大图时（平台横幅、社交图）
python tools/make_icon.py --size 1024 -o art/icon-1024.png
```

脚本只依赖 Python 标准库，直接读取 `color-data/mard-291.csv`，并对色号数量（291）与色系
数量（15）做硬校验。相同的脚本 + 相同的数据在任何机器上产出逐像素一致的结果；当前产物的
SHA-256 为 `2c673ebf90e12b2487fac816e117e3c2089959500b555884631c4d5cf38e85d8`，可用
`sha256sum src/main/resources/assets/pixelbead/icon.png` 复核。

### 4.3 美术资源范围

- 仓库与 jar 内**不含**任何第三方图像、音频或字体素材。
- 291 个方块与物品的贴图由构建期的 `generateBeadResources` 按色值直接生成纯色纹理，
  不是外部美术资源。
- 除 `icon.png` 外，项目没有其他位图美术；界面全部由代码绘制。

因此各平台在素材来源类问题上可如实选择「无第三方素材」；项目页正文只需注明 MARD 色彩数据
来自 MakeBead（MIT），见第 5 节。

## 5. 发布填报清单（与本文件相关）

- [ ] 图标：上传 `src/main/resources/assets/pixelbead/icon.png`（512×512 PNG）
- [ ] 正文注明：MARD 291 colour data © MakeBead, MIT（`makebead/craft-color-codes`）
- [ ] 不使用「官方 Minecraft 产品」「Mojang 关联」等表述，保留第 3 节的免责声明
- [ ] 依赖里单列 Kotlin for Forge 6.5.0+（用户最容易漏装）
- [ ] 画廊只放真实游戏截图，不要放渲染图或概念图
