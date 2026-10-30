# 怀旧游戏厅 (GameHub)

基于 J2ME Loader (Apache-2.0) 改造的 J2ME 游戏平台 App。

## 环境
- Android SDK: `F:\jetbrains\AndroidStudioSDK`（写在 `local.properties`）
- JDK: 用 Android Studio 自带的 `F:\jetbrains\Android Studio\jbr`（项目需要 Java 21+）
- NDK: 30.0.16248370（`build.gradle` 里的 `NDK_VERSION`）

## 常用命令（PowerShell）
- APK 不内置游戏（约 22MB），游戏通过「游戏包」导入
- 生成游戏包：`python tools/build_pack.py 输入zip或目录... -o 输出.zip [--exclude 已有游戏包.zip]`
  - 游戏包结构：`分类/游戏名.jar`，文件夹名就是分类；调整分类只需在 zip 里挪文件
  - 现有游戏包在 `../java游戏合集/游戏包/`：`中文精选包.zip`（472）、`扩展包.zip`（5134，已排除精选包里的）
  - 分类规则在 `prepare_games.py` 的 `CATEGORIES`，改了要同步 `GameCategorizer.java`
  - 游戏 ID = md5(游戏名)[:12]，App 和脚本一致，存档靠这个 ID 关联
- 生成游戏源：`python tools/prepare_games.py 游戏目录 --catalog 输出目录 --name "源名称"`
- 编译调试版：`$env:JAVA_HOME="F:\jetbrains\Android Studio\jbr"; .\gradlew.bat assembleOpenDebug`
  - 输出：`app/build/outputs/apk/open/debug/GameHub-<版本号>-open-debug.apk`
  - 发版：改 `app/build.gradle` 的 `versionCode`（+1）和 `versionName`，复制到 `../java游戏合集/游戏包/怀旧游戏厅_v<版本号>.apk`
  - 版本记录：1.0.0 首版；1.1.0 底部导航、游戏包导入、键盘区分、隐藏标题栏、横屏/横屏精简布局；
    1.1.1 两种横屏布局改为左边方向键、右边其他按键
- 安装：`F:\jetbrains\AndroidStudioSDK\platform-tools\adb.exe install -r <apk>`（包名 `com.gamehub.retro.debug`）
- Git：在 PowerShell 里用 Windows 的 git（`core.autocrlf=true`）；WSL 里的 git 会因换行符把所有文件都显示为已修改，而且很慢

## 游戏来源（添加游戏的接口）
1. 内置：`app/src/main/assets/games/`（目前为空，`prepare_games.py` 不带 `--catalog` 时会生成）
2. 导入游戏包 / jar：App 首页空状态按钮或「我的 → 导入游戏包」，存到 `files/gamehub/local/`
   - zip 里 jar 所在文件夹名作为分类（"xxx合集"类文件夹按游戏名自动分类）；文件名 UTF-8/GBK 自动识别
   - 同名或同内容去重；同内容但名字不同的旧记录会被替换（用于修复乱码）
3. 游戏源：App「我的 → 游戏源」添加一个 `catalog.json` 网址。格式：
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
   - `url`、`icon` 可以是相对 catalog.json 的路径，也可以是完整网址
   - `category`、`icon`、`size`、`id` 可省略（省略分类时按游戏名自动分类）
   - 游戏在用户点击时才下载，图标在添加/刷新游戏源时下载
   - 模拟器测试：本地 `python -m http.server 8765` 后用 `http://10.0.2.2:8765/catalog.json`

## 代码结构
- 平台界面：`app/src/main/java/ru/playsoftware/j2meloader/gamehub/`
  - `GameHubFragment` 底部导航四页（首页/分类/搜索/我的）
  - `GameSources` 本地导入 + 网络游戏源；`GameLauncher` 安装/启动/键盘设置
  - `KeyboardStyles` + `KeyboardPreviewView` 键盘样式选择器（对应 `VirtualKeyboard.TYPE_*`）
  - 新增布局（原版只有 0-6）：`TYPE_LANDSCAPE = 7` 横屏、`TYPE_LANDSCAPE_SIMPLE = 8` 横屏精简版；
    两侧按键在 `applyLandscapeOffsets()` 里居中，`MicroActivity.orientationFor()` 负责强制横/竖屏
- 从 jar 安装：`AppInstaller.installLocalJar()`

## 约定
- 用户自己在手机上测试，不需要写单元测试或单独的测试脚本，保证能编译通过即可。
- 用户可能同时在模拟器上操作，用 adb 模拟点击前先确认。
