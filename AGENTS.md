# 怀旧游戏厅 (GameHub)

基于 J2ME Loader (Apache-2.0) 改造的怀旧手机游戏平台，同时支持 **Java（J2ME）** 和 **MRP（斯凯冒泡）** 游戏。
包名 `com.gamehub.retro`（调试版 `com.gamehub.retro.debug`）。

## 发布文件（`../java游戏合集/游戏包/`）
| 文件 | 内容 |
|---|---|
| `怀旧游戏厅_v<版本号>.apk` | 最新安装包（旧版在 `旧版本安装包/`） |
| `Java游戏包_中文精选.zip` | 472 款中文 Java 游戏 |
| `Java游戏包_扩展.zip` | 5134 款 Java 游戏（已排除精选包里的） |
| `MRP游戏包.zip` | 1112 款 MRP 游戏（由 `../mrp文件/` 里的合集整理而来） |
| `说明.txt` | 给用户看的安装/导入说明 |

APK 不内置游戏，游戏都通过「导入游戏包」进入 App。

## 环境
- Android SDK: `F:\jetbrains\AndroidStudioSDK`（写在 `local.properties`）
- JDK: Android Studio 自带的 `F:\jetbrains\Android Studio\jbr`（项目需要 Java 21+）
- NDK: 30.0.16248370（`build.gradle` 的 `NDK_VERSION`）
- Git：在 PowerShell 里用 Windows 的 git（`core.autocrlf=true`）；WSL 里的 git 会因换行符把所有文件显示为已修改，而且很慢

## 常用命令（PowerShell，在 GameHub 目录）
- 编译调试版：`$env:JAVA_HOME="F:\jetbrains\Android Studio\jbr"; .\gradlew.bat assembleOpenDebug`
  - 输出：`app/build/outputs/apk/open/debug/GameHub-<版本号>-open-debug.apk`
- 安装：`F:\jetbrains\AndroidStudioSDK\platform-tools\adb.exe install -r <apk>`
- 发版：`app/build.gradle` 的 `versionCode` +1、改 `versionName`，APK 复制到发布文件夹，旧版移进 `旧版本安装包/`
- Java 游戏包：`python tools/build_pack.py 输入zip或目录... -o 输出.zip [--exclude 已有游戏包.zip]`
  - 结构 `分类/游戏名.jar`，文件夹名就是分类；调整分类只需在 zip 里挪文件
- MRP 游戏包：`python tools/build_mrp_pack.py 合集.zip -o MRP游戏包.zip`
  - 去掉坏文件和重复的（按文件头中文名、内容 md5），疑似应用放进「应用工具」分类
- 游戏源（网络清单）：`python tools/prepare_games.py 游戏目录 --catalog 输出目录 --name "源名称"`
- 分类规则在 `tools/prepare_games.py` 的 `CATEGORIES`，改了要同步 `GameCategorizer.java`

## 版本记录
- 1.0.0 首版
- 1.1.0 底部导航、游戏包导入、键盘区分、隐藏标题栏、横屏/横屏精简布局
- 1.1.1 两种横屏布局改为左边方向键、右边其他按键
- 1.1.2 横屏按键离屏幕边缘留白（适配曲面屏/圆角），挖孔区域改为黑色
- 1.1.3 横屏「方向键分离」、键盘样式按竖屏/横屏/悬浮分组、启动/退出游戏转场和加载画面
- 1.2.0 支持 MRP 游戏（vmrp + unicorn 内核）
- 1.2.1 Java / MRP 两个独立游戏厅；MRP 键盘支持和 Java 一样的样式；MRP 越界内存访问不再直接崩溃
- 1.2.2 删除游戏同时清空存档；MRP 每个游戏独立沙盒；修复 MRP 加载插件后崩溃（如圣斗士星矢之冥王神话）；MRP 崩溃显示详细报告；
  竖屏手机键盘·方向键版、横屏精简改为 2/4/6/8 + 1/3/5/7/9 布局

## 游戏来源
1. 内置：`app/src/main/assets/games/`（目前为空，`prepare_games.py` 不带 `--catalog` 时会生成）
2. 导入游戏包：首页空状态按钮或「我的 → 导入游戏包」，支持 zip / jar / mrp，存到 `files/gamehub/local/`
   - zip 里文件所在文件夹名作为分类（"xxx合集"类文件夹按游戏名自动分类）；文件名 UTF-8/GBK 自动识别
   - 同名或同内容去重；同内容但名字不同的旧记录会被替换（用于修复乱码）；Java 和 MRP 分开去重
3. 游戏源：「我的 → 游戏源」添加 `catalog.json` 网址（url 以 `.mrp` 结尾的算 MRP 游戏）：
   ```json
   {
     "name": "源名称",
     "version": 1,
     "games": [
       {"id": "唯一ID", "name": "七侠五义", "category": "武侠江湖",
        "url": "games/xxx.jar", "icon": "icons/xxx.png", "size": 123456}
     ]
   }
   ```
   - `url`、`icon` 可以是相对 catalog.json 的路径或完整网址；`category`、`icon`、`size`、`id` 可省略
   - 游戏在点击时才下载，图标在添加/刷新游戏源时下载
   - 模拟器测试：本地 `python -m http.server 8765` 后用 `http://10.0.2.2:8765/catalog.json`

## 游戏 ID 与存档
- Java：ID = md5(游戏名)[:12]；MRP：ID = md5("mrp:" + 游戏名)[:12]。App 和脚本一致，存档靠 ID 关联
- `GameEntry.platform`：`java`（或空）/ `mrp`

## 代码结构
### 大厅（`app/src/main/java/ru/playsoftware/j2meloader/gamehub/`）
- `GameHubFragment`：底部四页（首页/分类/搜索/我的）；顶部 `view_platform_filter.xml` 切换 Java / MRP 游戏厅
  - `platform` 只有 java / mrp 两个值（存在偏好 `gamehub_platform`），所有页面、统计、最近玩过都只看当前游戏厅
- `GameSources`：本地导入（`Importer`）+ 网络游戏源；`GameLibrary`：汇总列表、拼音搜索、最近玩过、已玩过
- `GameLauncher`：Java 安装（`AppInstaller.installLocalJar()`）/ 启动 / 键盘设置；MRP 直接启动 `MrpActivity`
- `KeyboardStyles` + `KeyboardPreviewView`：键盘样式选择器，Java 用 `GROUPS`（竖屏/横屏/悬浮），MRP 用 `MRP_GROUPS`
- `GameIcons`：图标（Java 取 jar 里的图标；MRP 没有图标，显示首字 + 分类颜色）

### Java 游戏（J2ME Loader 原有部分）
- `VirtualKeyboard` 新增布局（原版只有 0-6）：7 横屏标准、8 横屏精简（左边是 2/4/6/8 数字十字、中间空，右边功能键 + 1/3/7/9/*/0/#，5 在 1/3/7/9 正中间）、9 方向键分离（5 画成确定，按下发送 5）；
  2 手机键盘·方向键版（竖屏：左软键/菜单/右软键、↑、←确定→、↓、完整九宫格；软键行和方向键行比数字行矮，`getPhoneKeyboardHeight` 按这个算）；
  两侧按键在 `applyLandscapeOffsets()` 里居中并离边缘留白（`LANDSCAPE_EDGE_X/Y`）
- `MicroActivity.orientationFor()` 按键盘样式强制横/竖屏；游戏内「切换键盘」也用 `KeyboardStyles.pick()`
- 转场：`GameLauncher.start()` 进入动画（`res/anim/game_enter`）；`launch_overlay`（`view_game_launch.xml`）显示图标/名字/加载中，
  `Canvas.setFirstFrameListener` 收到第一帧后淡出；退出时 `showExitOverlay()`，`MidletThread.notifyDestroyed()` 延迟杀进程让动画播完
- `MicroActivity` 必须跑在独立进程 `:midlet`（退出靠杀进程清理 J2ME 静态状态）。原版 `src/debug/AndroidManifest.xml`
  在调试版里去掉了这个进程，会导致退出游戏时把大厅一起杀掉，已删除，别再加回去
- 游戏页主题是 `AppTheme.NoActionBar.Game`（名字里的点表示继承关系，别改成 `AppTheme.Game`，否则会带上标题栏导致崩溃）

### MRP 游戏
- 内核：`app/src/main/cpp/vmrp/`，来自 github.com/vmrp/vmrp（GPL-3.0），ARM 指令用 unicorn 2.1.4 模拟（GPLv2）
  - unicorn 预编译静态库在 `cpp/vmrp/unicorn/lib/<abi>/`，重新编译：`tools/build_unicorn.ps1 -Source <unicorn 2.1.4 源码>`
    （需要 CMake、Git 自带的 sh、NDK 的 make）
  - 64 位适配：guest 结构体里的指针一律用 uint32（`dsm.h` 的 `#ifdef VMRP` 段、`bridge.c` 的 `mr_c_function_P_t`），
    宿主指针不能截成 32 位（`memory.c`）
  - 越界访问：启动时映射 0~CODE_ADDRESS，读写未映射地址时临时补页（`vmrp.c` 的 `hook_mem_invalid`），模拟真机不崩溃
  - 内存是 `uc_mem_map_ptr` 映射的宿主缓冲区，宿主直接写模拟器内存（`br_mr_read`、`br_memcpy`、`br_memset`）后必须
    `uc_ctl_remove_cache` 让那段地址的已翻译代码失效，否则 unicorn 继续执行旧代码。
    付费插件（netpay）会先把游戏内存存成 `plugins/dump0`、在同一地址加载插件代码，结束后再读回来，不失效就会跳到 0 崩溃（圣斗士星矢之冥王神话）
  - 文件打开（`fileLib.c` 的 `my_open`）：`MR_FILE_WRONLY` 当读写打开，`MR_FILE_RECREATE`(16) 在可写时截断；文件操作都会打日志（.uc2 字库除外）
  - 致命错误时错误框显示编译时间、最近 200 行 `vmrp` 日志、首次越界访问时的寄存器/代码/栈、沙盒文件列表和游戏文件 md5，
    可「复制错误信息」，也存到 `files/mrp/last_error.txt`
  - `vmrp_android.c`：JNI、模拟器线程（事件队列 + 定时器）、画面/声音/振动/对话框/编辑框回调到 Java；日志标签 `vmrp`
  - 新增 ndk-build 子目录后要删 `app/build/.cxx` 才会被识别
- 运行时文件 `assets/mrp/`（cfunction.ext、字库、插件）首次运行复制到 `files/mrp/`（共享运行时）；
  改了 assets 要把 `MrpRuntime.ASSETS_VERSION` +1
- 每个游戏在自己的沙盒 `files/mrp/games/<id>/` 里运行：`cfunction.ext` 和 `mythroad/` 里的 assets 条目是指向共享副本的符号链接，
  游戏在 `mythroad/<id>.mrp`，存档只写在这个目录里；旧版共享 `mythroad/` 里的存档首次启动时复制进来一次（标记 `files/mrp/legacy_seeded/<id>`）
- 删除游戏会同时删除游戏文件、存档和按键设置（Java 存档在 `Config.getDataDir()/<id>`，MRP 是整个 `files/mrp/games/<id>/`）；
  删除沙盒时绝不能递归进符号链接（会删掉共享字库/插件），`MrpRuntime.clearGame()` 用 `Os.lstat` 判断
- Java 侧：`ru/playsoftware/j2meloader/mrp/`
  - `MrpActivity` 跑在独立进程 `:mrp`，返回键/「菜单」键打开菜单（切换键盘样式、退出）
  - `MrpGameView` 画 240x320 屏幕和虚拟键盘；样式 2 手机键盘·方向键版（默认；从上到下：左键/菜单/右键、↑、←确定→、↓、完整九宫格，前几行比数字行矮）、10 方向+数字（只给 MRP）、7/9/8 横屏，
    方向键分离里的「5/确定」在 MRP 里发送确定键
  - 每个游戏的键盘样式存在 `files/mrp/keys/<id>`（文件，跨进程可见），默认值 `KeyboardStyles.getMrpDefault()`
- 导入时游戏名取 mrp 文件头里的中文名（GBK，偏移 0x1C，24 字节）

## 约定
- 用户自己在手机/模拟器上测试，不需要写单元测试或单独的测试脚本，保证能编译通过即可
- 用户可能同时在模拟器上操作，用 adb 模拟点击前先确认；装包、推文件、看日志可以直接做
- 用户问问题时先回答，不要擅自加功能；不确定要不要做就先问
