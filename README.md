# Ponder++ (思索++)

面向机械动力(Create)内建教程系统“思索”(Ponder) 的增强附属模组。
所有代码均由deepseek-v4-flash/pro完成

## 功能

1. **进度条可移动到任意位置**：点击思索进度条的任意位置即可跳转到对应时间点；按住并拖动进度条可在任意时刻前后连续移动，不再局限于自带的关键帧节点。
2. **可在任意位置暂停**：思索界面进度条末尾新增“暂停/播放”按钮，点击即可在任意时刻暂停画面（场景时间完全冻结）；再次点击或按下**空格键**恢复播放。
3. **记住退出位置（可开关）**：关闭思索时记录当时所在的情景与进度条位置，再次打开同一思索时自动切回该情景并跳回上次进度。开关位于暂停按钮旁：`记进度`；`自动暂停` 控制跳回上次位置后是否立即暂停；`单击W` 开关控制“单击 W 键直接打开思索（替代长按）”。开关文本已本地化（`assets/ponderpp/lang/`，随客户端语言切换中/英）。配置存于 `config/ponderpp/settings.properties`，改完重开思索即生效。

## 支持版本 / 加载器

| Minecraft | 加载器  | 目录            | 依赖 Ponder |
|-----------|---------|-----------------|-------------|
| 1.20.1    | Forge   | `1.20.1-forge`  | `net.createmod.ponder:Ponder-Forge-1.20.1` |
| 1.21.1    | NeoForge| `1.21.1-neoforge`| `net.createmod.ponder:Ponder-NeoForge-1.21.1` |

## 工程结构

单一仓库，两个独立的 Gradle 工程，共享同一份版本无关源码（`common-src/src`）：

```
思索++/
├── common-src/src/        # 共享源码：核心 Mixin 与逻辑（两个工程共同编译同一份代码）
│   ├── main/java/com/ponderpp/
│   │   ├── PonderPpClient.java       # 暂停状态持有者
│   │   └── mixin/
│   │       └── PonderUIMixin.java        # 暂停冻结 + 暂停按钮绘制/点击 + 进度条点击/拖动 + 关闭清理
│   └── main/resources/ponderpp.mixins.json
├── 1.20.1-forge/          # Forge 1.20.1 工程（ForgeGradle，产出带 SRG refmap 的 jar）
├── 1.21.1-neoforge/       # NeoForge 1.21.1 工程（ModDevGradle，运行时为官名无需 refmap）
```

## 实现原理

全部逻辑集中在共享 Mixin（`PonderUIMixin` / `ForgePonderUIRenderMixin` / `NeoPonderUIRenderMixin` /
`PonderTooltipHandlerMixin`）中，注入目标**只选择能可靠参与 SRG/官名重映射的原版方法**
（`tick`、`renderBackground`/`renderWindow`、`removed`），并用 GLFW 直接轮询鼠标/键盘——
刻意避免对继承自 `Screen`/`AbstractWidget` 的成员或 mod 私有方法做
`@Shadow`/`@Invoker`/目标注入（这正是最初在 Forge 1.20.1（SRG 运行时）崩溃的根因）。
对 Ponder 自身私有成员（`scenes`/`index`/`lazyIndex`/`scroll`）改用反射读取/调用：Ponder 的
成品 jar 保留可读成员名，反射稳妥且失败时静默降级。

- **暂停冻结**：`PonderUI#tick()` `HEAD` 注入取消点，暂停态 `cancel()`，使 `activeScene.tick()` 不再推进场景时间；关闭界面（`PonderUI#removed()`）自动解除暂停态。
- **暂停按钮**：`PonderUI#renderBackground`（Forge）/ `renderWindow`（NeoForge）`TAIL` 手动绘制“暂停/播放”切换按钮与两个功能开关；左键/空格键的“刚按下”边沿在 `tick` 里用 GLFW 判定并切换暂停。
- **进度条移动到任意位置**：`tick` 内每帧读取左键状态与鼠标位置，命中进度条区域时按横坐标换算任意时间并 `seekToTime`；点击与拖动共用此判定，暂停状态下仍可拖动查看不同帧。`seekToTime` 向前为积累式 seek，向后的位置由其内部先 `replay` 再前进到目标。
- **记住退出位置**：`removed` 只记录“活动情景”的进度与所在索引；重开时先用原生 `scroll(true)` 逐帧切回上次情景、再把 `lazyIndex` 钉到 `index` 消除切换过渡，最后等目标情景 `totalTime` 累积到位后 `seekToTime` 恢复进度（避免 Ponder 只允许向前 seek、`begin()` 会清零时间等陷阱）。

## 构建

需要 JDK（1.20.1 → 17；1.21.1 → 21）

```powershell
# NeoForge 1.21.1
cd 1.21.1-neoforge
.\gradlew.bat assemble          # 产出 1.21.1-neoforge/build/libs/ponderpp-1.21.1-1.2.0.jar

# Forge 1.20.1
cd 1.20.1-forge
.\gradlew.bat assemble          # 产出 1.20.1-forge/build/libs/ponderpp-1.2.0.jar
```

## 自定义图标

暂停/播放按钮图标是一份独立贴图，可以直接改

- 源码位置：`common-src/src/main/resources/assets/ponderpp/textures/gui/ponder_buttons.png`
- jar 内同名路径：`assets/ponderpp/textures/gui/ponder_buttons.png`
- 规格：**64 × 32 像素**（RGBA，带透明通道）。
  - 画布左半（x 0–31）= **暂停**图标（❚❚ 两条竖条）。
  - 画布右半（x 32–63）= **播放**图标（▶ 朝右三角）。
  - 两个图标各自的内容区放在 **20×20** 的窗口里、白色前景即可；四周留透明。

> 说明：渲染器用 `GuiGraphics.blit` 从该贴图按偏移取图标——暂停取 `u=6,v=6` 的 20×20，播放取 `u=38,v=6` 的 20×20。若你调整了图标在画布内的位置，请同步改 `PonderUIMixin` 里的两个 `blit` 坐标。