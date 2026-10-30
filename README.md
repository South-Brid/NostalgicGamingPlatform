# 怀旧游戏厅 · Nostalgic Gaming Platform

一个 Android 上的**怀旧手机游戏平台**：把当年功能机上的 **Java（J2ME）** 游戏和 **MRP（斯凯「冒泡」）** 游戏装进一个 App，
按分类浏览、搜索、一键启动，存档自动保留。

基于开源 J2ME 模拟器 [J2ME Loader](https://github.com/nikita36078/J2ME-Loader) 改造，MRP 内核移植自 [vmrp](https://github.com/vmrp/vmrp)。

## 功能

### 游戏厅
- **Java / MRP 两个独立游戏厅**，首页顶部一键切换，推荐、分类、搜索、「玩过的游戏」都分开
- 底部导航：首页（继续游戏、今日推荐、各分类精选）/ 分类 / 搜索 / 我的
- 自动分类：仙侠修真、武侠江湖、三国历史、西幻冒险、角色扮演、动作射击、策略经营、赛车体育、休闲益智、棋牌等
- 搜索支持游戏名、全拼和拼音首字母（如 `qxwy` → 七侠五义）
- 启动/退出游戏有转场动画和加载页，不再黑屏

### 添加游戏
- **导入游戏包**：选择 zip 游戏包或单个 jar / mrp 文件，支持多选、嵌套 zip
  - zip 里的文件夹名就是分类；文件名 UTF-8 / GBK 自动识别
  - 同名或同内容的游戏自动去重；MRP 游戏名取自文件头里的中文名
- **游戏源**：添加一个 `catalog.json` 网址，游戏在点击时才下载（格式见 [AGENTS.md](AGENTS.md#游戏来源)）

### 虚拟键盘
- 样式分组选择、带预览图：
  - 竖屏：手机键盘、手机键盘·方向键版、方向 + 数字（MRP）
  - 横屏：标准、精简、方向键分离（画面居中，按键在两侧）
  - 悬浮按键（Java）：数字/方向键半透明盖在画面上
- 可设置全局默认样式，也可以长按单个游戏、或在游戏中通过菜单单独切换
- 横屏按键离屏幕边缘留白，适配曲面屏和圆角屏

### MRP 支持
- 移植 vmrp 内核，ARM 指令由 [unicorn](https://github.com/unicorn-engine/unicorn) 模拟，arm64 / armv7 / x86 / x86_64 设备都能运行
- 支持画面、按键、触屏、定时器、声音（MIDI / WAV / MP3）、振动、对话框、文字输入
- 修正了 vmrp 在 64 位设备上的指针截断问题；越界内存访问按真机行为处理，不再直接崩溃

## 安装与使用
1. 安装 APK（见 Releases，或自行编译）。
2. 首页顶部选择「Java 游戏厅」或「MRP 游戏厅」。
3. 点「导入游戏包」，选择 zip 游戏包或 jar / mrp 文件。
4. 点游戏开始玩；游戏中按手机返回键打开菜单（切换键盘样式、退出）。

> 本仓库不包含任何游戏文件，请自行准备合法拥有的游戏。

## 编译

需要 Android SDK、NDK 30.0.16248370、JDK 21+（可用 Android Studio 自带的 JBR）。

```powershell
$env:JAVA_HOME="<Android Studio>\jbr"
.\gradlew.bat assembleOpenDebug
# 输出：app/build/outputs/apk/open/debug/GameHub-<版本号>-open-debug.apk
```

unicorn 已按 4 种 ABI 预编译为静态库放在 `app/src/main/cpp/vmrp/unicorn/lib/`，一般不需要重新编译；
如需重编：`tools/build_unicorn.ps1 -Source <unicorn 2.1.4 源码目录>`（需要 CMake、Git 自带的 sh）。

## 工具脚本（`tools/`）
| 脚本 | 作用 |
|---|---|
| `build_pack.py` | 把 Java 游戏合集整理成游戏包（去重、去坏文件、按分类放文件夹） |
| `build_mrp_pack.py` | 把 MRP 合集整理成游戏包（按文件头中文名命名、去重、分类） |
| `prepare_games.py` | 生成内置游戏或网络游戏源（`catalog.json` + 图标） |
| `build_unicorn.ps1` | 为 Android 交叉编译 unicorn 静态库 |

## 项目结构
```
app/src/main/java/ru/playsoftware/j2meloader/gamehub/   游戏厅界面、导入、游戏源、键盘样式选择
app/src/main/java/ru/playsoftware/j2meloader/mrp/       MRP 游戏页、虚拟键盘、运行时文件
app/src/main/java/javax/microedition/                   J2ME 运行环境（来自 J2ME Loader）
app/src/main/cpp/vmrp/                                  vmrp 内核 + Android 适配 + unicorn 静态库
app/src/main/assets/mrp/                                MRP 运行时文件（mythroad 层、字库、插件）
tools/                                                  游戏包 / 游戏源 / unicorn 构建脚本
```
开发细节（架构、约定、踩过的坑）见 [AGENTS.md](AGENTS.md)。

## 致谢与许可
- [J2ME Loader](https://github.com/nikita36078/J2ME-Loader)：Copyright © Nikita Shakarun 等，Apache License 2.0（见 [LICENSE](LICENSE)）
- [vmrp](https://github.com/vmrp/vmrp)：Copyright © zengming00，GPL-3.0（见 [app/src/main/cpp/vmrp/LICENSE](app/src/main/cpp/vmrp/LICENSE)）
- [unicorn](https://github.com/unicorn-engine/unicorn) 2.1.4：GPLv2（见 [app/src/main/cpp/vmrp/unicorn/COPYING](app/src/main/cpp/vmrp/unicorn/COPYING)）

由于包含 GPL 组件，分发含 MRP 内核的 APK 时需遵守 GPL，并提供对应源码（即本仓库）。
