# ScalingGUIs 2.0.0 改动说明（小数缩放支持）

版本：`1.12.2-2.0.0`
目标：让 GUI / HUD / Tooltip 的缩放可以取**非整数**值（默认 0.05 一档，可配置），使任意分辨率（尤其 2560×1440）都能调到与 1080p@3x 相同的视觉大小。

---

## 1. 为什么必须改底层

Minecraft 1.12.2 的 `net.minecraft.client.gui.ScaledResolution` 用**整数**缩放系数，并且逐级向上试探：

```java
int i = mc.gameSettings.guiScale;              // 0 = auto → 1000
if (i == 0) i = 1000;
while (this.scaleFactor < i
        && this.scaledWidth  / (this.scaleFactor + 1) >= 320
        && this.scaledHeight / (this.scaleFactor + 1) >= 240) {
    ++this.scaleFactor;
}
```

结果是可用档位极其稀疏：

| 分辨率 | 可用的整数缩放 | 对应虚拟分辨率（1x 起） |
| --- | --- | --- |
| 1920×1080 | 1 / 2 / 3 / 4 | 4x → 480×270 |
| 2560×1440 | 1 … 6 | 3x → 854×480，4x → 640×360 |
| 3840×2160 | 1 … 9 | 6x → 640×360 |

24.5″ 2K 显示器要接近 1080p@3x 的字号，需要约 **3.9×**；27″ 2K 约 **3.56×**。两者都不是整数，玩家只能在 3x（偏大）与 4x（偏小）之间二选一 —— 这就是"要么很大、要么很小"的根因。原版把 `guiScale` 上限锁在 `min(w/320, h/240)`，因此旧版滑块上的 7/8 档在 2K 上会被原版钳成 6，属于死档。

**关键事实（决定补丁位置）**：

- `EntityRenderer#setupOverlayRendering()` 用 `sr.getScaledWidth_double()` / `getScaledHeight_double()` 建立正交投影 → **投影天然支持小数**。
- `GuiIngame#renderGameOverlay(float)` 在方法开头 `new ScaledResolution(mc)`，随后 hotbar / 血量 / 经验条 / 计分板 / 调试信息 / 药水效果等**全部**子渲染都接收这一个实例。
- `EntityRenderer` 计算鼠标坐标用 `Mouse.getX() * scaledWidth / displayWidth`，`GuiScreen#handleMouseInput` 用 `eventX * this.width / displayWidth` → 只要虚拟尺寸正确，鼠标天然落在正确的虚拟坐标空间。
- `GuiScreen#setWorldAndResolution(mc, w, h)` 用传入的 w/h 作为 `width`/`height` 并触发 `initGui()`。

结论：**只需让 `ScaledResolution` 返回小数的虚拟分辨率**，投影 / HUD / 鼠标 / GUI 布局就会同时正确，不需要对 GUI 做矩阵缩放 hack（旧版对 HUD 只能靠临时改整数 `guiScale`，这正是它无法做小数缩放的原因）。

---

## 2. 运行期架构

```
ScaleContext（当前生效的小数缩放，默认 -1 = 未激活）
        │ set()/clear()
        ▼
ScaleHook.activeScale  ──────────────┐
        （无任何 MC 依赖的静态桥）      │
        ▲                            │
        │ ASM 注入                   │
ScaledResolution.<init>(Minecraft)  │
        │ 修改 scaledWidthD / scaledHeightD /
        │ scaledWidth / scaledHeight / scaleFactor
        ▼
正交投影 + GuiIngame + GUI 布局 + 鼠标坐标
```

### 2.1 coremod（新增）

| 文件 | 作用 |
| --- | --- |
| `asm/ScaleHook.java` | 无 MC 依赖的静态钩子。`activeScale <= 0` 时**原样透传**，等于完全没有装模组 |
| `asm/ScaledResolutionTransformer.java` | `IClassTransformer`，在 `<init>(Lnet/minecraft/client/Minecraft;)V` 的 `RETURN` 前注入 |
| `asm/ScalingGUIsCorePlugin.java` | `IFMLLoadingPlugin`，注册上面的 transformer |

实现要点：

- 只用 `ClassWriter.COMPUTE_MAXS`（不用 `COMPUTE_FRAMES`），注入序列无分支、不新增局部变量槽以外的栈帧，避免 `VerifyError`。
- **不依赖字段名**：在构造器字节码里按 `PUTFIELD` 出现顺序识别字段（第 1/2/3 个 int = `scaledWidth`/`scaledHeight`/`scaleFactor`，第 1/2 个 double = `scaledWidthD`/`scaledHeightD`），因此 dev（MCP 名）与生产环境（srg 名）通用。
- 显示尺寸用 `Math.round(scaledWidthD * scaleFactor)` 反算，避免 `2560/6*6` 之类的取整误差累积。
- 任何异常 / 字节码形状不符 → 打印一行日志并**返回原始字节**，绝不因为补丁失败而让游戏崩掉。
- 纯 ASM + launchwrapper，不依赖 Mixin / MixinBooter → **Forge 1.12.2 与 Cleanroom 都能用**。

### 2.2 上下文管理（新增 `handlers/ScaleContext.java`）

- `set(scale)`：写入 `ScaleHook`，并把 `gameSettings.guiScale` 设为 `round(scale)`（照顾仍直接读原版字段的第三方 mod）；`clear()` 时恢复原始值。
- 三个上下文：GUI 打开时、HUD 渲染时、Tooltip 渲染时。
- HUD 重渲染前**先** `mc.entityRenderer.setupOverlayRendering()` 重建正交投影，渲染完再重建一次，保证后续 GUI 使用的是 GUI 缩放而不是 HUD 缩放。

---

## 3. 配置语义变化

| 项 | 旧版 | 新版 |
| --- | --- | --- |
| 类型 | int（1~8，另有 9 = 跟随主设置） | **double**，默认 0.05 一档（`scaleStep` 可配置，0.01~0.5） |
| `GUI Scale` / `HUD Scale` / `Tooltip Scale` | int | double |
| 滑块含义 | 0 = AUTO (MAX)，9 = 跟随主 GUI 缩放 | **0 = AUTO（自适应）**，>0 = 直接指定倍数 |
| AUTO 的含义 | 等于当前分辨率的最大整数缩放（2K 上是 6x → 426×240，极小） | **`屏幕高度 / referenceHeight`**，量化到 `scaleStep` |
| 滑块上限 | 写死 9 | 当前分辨率**真实可用**的上限 `min(displayWidth/320, displayHeight/240)`，不再有死档 |
| 新增配置 | — | `referenceHeight`（默认 360，范围 200~1000）、`scaleStep`（默认 0.05，范围 0.01~0.5，滑块步进）、`fractionalScaling`（默认 true）、`logScaleChanges`（默认 false） |

### AUTO 的换算

`AUTO = 量化到 scaleStep 的 (displayHeight / referenceHeight)`

- 参考高度 360 的物理含义：**1080p 分辨率下取 3x**，即"1K 屏幕上看着比较正常"的那一档；360 正是 1080/3。
- 于是 1080p → 3.0、1440p → 4.0、2160p → 6.0，各分辨率下 GUI 元素的物理尺寸基本一致。
- 想整体偏大就把 `referenceHeight` 调小（330 左右），偏小就调大（400 左右）。

### 步进精度（scaleStep）

滑块每次变化的幅度由 `scaleStep` 决定：`0.05`（默认，可调出 3.15 / 3.65 这种两位小数）、`0.25`（旧版粗粒度）、`0.01`（最精细）。修改后只影响滑块的步进，不会改动已经存下的数值；想要完全精确的值也可以直接编辑 `config/ScalingGUIs/ScalingGUIsCustomScales.json` 或 `config/ScalingGUIs/ScalingGUIs.cfg`。

### 旧配置迁移

`ScalingGUIsCustomScales.json` 新增 `formatVersion` 字段。读到 `formatVersion < 2` 时：

- `guiScale` / `hudScale` / `tooltipScale` 的 `9` → `0`（9 原本表示"跟随主设置"，新版 0 = AUTO，语义等价）；
- `customIndividualGuiScales` / `customGroupGuiScales` 里每条的 `"scale": 9` → `0`；
- 写回文件并置 `formatVersion = 2`。旧数值 1~8 原样保留（int 由 Gson 直接读成 double）。

`fractionalScaling = false` 时所有缩放会先 `Math.round` 再使用，行为退回旧版（可用于排查非整数缩放的兼容性问题）。

---

## 4. 已改动文件清单

新增：

- `src/main/java/spazley/scalingguis/asm/ScaleHook.java`
- `src/main/java/spazley/scalingguis/asm/ScaledResolutionTransformer.java`
- `src/main/java/spazley/scalingguis/asm/ScalingGUIsCorePlugin.java`
- `src/main/java/spazley/scalingguis/handlers/ScaleContext.java`

修改：

- `handlers/ConfigHandler.java`：缩放值改 double、新增 `quantize()` / `clampScale(double)` / `getVanillaMaxScale()` / `getMaxSelectableScale()` / `getAutoGuiScale()` / `getMainGuiScale()`，新增配置项与旧配置迁移
- `handlers/ClientEventHandler.java`：改用 `ScaleContext`；去掉"整数缩放相同就提前返回"；动态缩放按 `scaleStep` 步进搜索；HUD 重渲染前后重建正交投影；Tooltip 缩放比改为 `target / 当前生效缩放`
- `config/CustomScales.java`：三个缩放字段改 double，新增 `formatVersion`
- `gui/guiconfig/GuiSnappingSlider.java`：滑块按 `scaleStep`（默认 0.05）量化，0 显示为 `AUTO (实际值)`
- `gui/guiconfig/SnappingSliderEntry.java`：double 语义 + epsilon 比较
- `gui/guiconfig/GuiConfigSG.java`：配置界面读写改 double，新建条目的默认值改 AUTO
- `assets/scalingguis/lang/en_us.lang`：`AUTO (MAX)` → `AUTO (%s)`
- `build.gradle`：版本 `1.12.2-2.0.0`；manifest 增加 `FMLCorePlugin` / `FMLCorePluginContainsFMLMod`；新增 `compileOnly` 的 launchwrapper 与 asm-debug-all；RFG 插件版本 `1.3.16` → `1.4.2`（1.3.16 已在 GTNH 仓库下架）
- `settings.gradle`：GTNH Maven 地址改为 `https://nexus.gtnewhorizons.com/repository/public/`（旧 `jenkins.usrv.eu:8081` 已失效）

---

## 5. 验证记录

- `CheckClassAdapter.verify(..., true, ...)` 对打补丁后的真实 `ScaledResolution` 做数据流校验：**通过**（原 1814 字节 → 1959 字节）。
- 数值单测：1440p@3.75 → 682.667×384（683×384，`getScaleFactor()=4`）；4K@7.5 → 512×288；反算显示尺寸精确回到 2560×1440 / 1920×1080 / 3840×2160，无取整丢失；未激活时原样透传。
- 真实客户端（Forge 14.23.5.2847 + Java 8）：coremod 正常注入，日志 `Patched net.minecraft.client.gui.ScaledResolution for fractional GUI scaling.`
- 模拟 2K（display 2052×1133）：`AUTO` → `3.25x → 632×349`；指定 `3.75x` 时 GL 投影实测 `orthoWidth=547.2 orthoHeight=302.13`，与虚拟分辨率一致。
- 主菜单、配置界面（含底部 Done / Undo / Reset / Apply globally）在 3.0× 与 3.75× 下渲染正确（读取完整帧缓冲确认）。
- **HUD**：进入真实存档（超平坦创造/生存，HUD 缩放 3.75×）抓取完整帧缓冲 —— 物品栏（182 GUI px → 约 685 帧缓冲 px）居中贴底、聊天栏文字按 3.75× 正确排版并无溢出，证明 `GuiIngame` 的各子渲染都吃到了小数虚拟分辨率。
- 发布版复验：清理所有调试代码后重新构建，`ScaledResolution` 补丁日志正常，AUTO 在 2052×1133 下解出 **3.25×**（非整数），Forge 正常加载无异常。

---

## 6. 已知限制

- 非整数缩放下文字与贴图会落在半像素上，理论上可能看到轻微模糊或采样接缝；`fractionalScaling = false` 可退回旧行为。
- 若某个第三方 GUI 不读 `width/height` 而是自己 `new ScaledResolution` 再自行排版，可能错位 → 把它加入 `GUI Class Blacklist`（回落到原版缩放）。
- OptiFine / Cleanroom 组合未实测；若它们也补丁了 `ScaledResolution`，本模组的 transformer 会在异常或形状不符时安全退出并保留原字节。
- 原版"Unicode 字体时奇数缩放会减 1"的规则在小数模式下不再适用（这是刻意的，否则又会出现跳档）。

---

## 7. 构建

```powershell
$env:JAVA_HOME='O:\java\zulu21.34.19-ca-jdk21.0.3-win_x64'
& 'C:\Users\Administrator\.gradle\wrapper\dists\gradle-8.12-bin\1njkg3ikremrlsj5arvgmk8qt\gradle-8.12\bin\gradle.bat' --no-daemon -p 'D:\codex\mods\ScalingGUIs-1.12.2' build -x test
```

产物：`build/libs/scalingguis-1.12.2-2.0.0.jar`（coremod 已内嵌，直接放入 `mods/` 即可）。
