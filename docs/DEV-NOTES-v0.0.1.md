# HoopoeTV

基于 [takagen99/Box](https://github.com/takagen99/Box)（TVBox 系 TV 端影视客户端）二次定制的自有品牌版本。
包名 `com.hoopoetv.tvbox`，应用名 **HoopoeTV**，默认主题 + 简体中文，已做「空壳播放器化」——不内置任何视频源、直播源、节目单地址，由使用者自行配置。

- 当前版本：`v0.0.1`（versionCode 1）
- 编译环境：JDK 17 / Gradle 7.5 / AGP 7.4.2 / compileSdk 34 / minSdk 21 / targetSdk 28
- 编译变体：`arm64` + `generic` + `normal`

---

## 一、界面结构

### 1. 顶栏

- 左侧「应用」独立块：**长方形 vs_140×vs_70**，图标在上、文字在下，与右侧六个入口**等高对齐**。
- 右侧六键：**首页 / 直播 / 收藏 / 文件 / 历史 / 推送**，高度统一 vs_70、`0dp + weight=1` 均分、图标与文字整组居中。
- 左上角显示当前数据源名称，**点击弹出「请选择数据源」列表**（按源数量自动 1~3 列），选中即生效、自动关闭并重载首页；无源时提示「当前没有可选视频源」。
- 顶栏左右 padding vs_20、上下 vs_25，与下方内容区左边距对齐。

### 2. 左侧导航（拨轮）

- 分类导航从原顶部横排移到**左侧竖排**，定位固定，超出屏幕的项由**上下滚动拨轮**呈现。
- 拨轮可视框高度 `vs_370`（约 7 项，第 8 项渐隐），`clipChildren=true`，上下各一层 **vs_30 渐变遮罩**（`fade_top` / `fade_bottom`），滚动到边缘的项淡出。
- 所有菜单项**等宽**（按 6 个汉字宽度 `mm2px(160)`），短标签与长标签块宽一致。
- 菜单之间留**外间距**：由 `shape_left_item.xml` 的 layer-list 上下各内缩 `7dp` 实现（RecyclerView 的 spacing / margin 在此布局下不生效）。
- 选中态：主题色底 + 2mm 白边；未选中：`color_3D3D3D_50` 无描边；文字 `0xE6FFFFFF`（与顶栏「首页」按钮一致）。
- 点击左侧分类即切换右侧内容页，**顶栏六个入口保持不变**。

### 3. 右侧内容区

- 右栏 `rightArea` 背景 `shape_content_paper`（20mm 圆角、淡白底 + 1mm 描边），呈现「一张纸」的效果。
- 内容网格 4 列铺满，随分类切换平滑过渡。

### 4. 底部分类筛选条

- 分类页底部可展开「类型 / 地区 / 排序 / 年代」筛选条，**默认关闭**。
- 再次点击同一分类可开 / 关该条；返回键优先关闭筛选条，不退出页面。

### 5. 过渡效果

- 切页时右侧内容区 alpha 0.4 + 沿 Y 轴位移 `mm2px(25)`，260ms DecelerateInterpolator 平滑入场；左栏 alpha 0.4 → 1（220ms）。

---

## 二、交互

- **任意页面连按两次返回键退出 App**（2 秒内第二次才真正退出，首次给提示）。
- 返回键优先级：加载中刷新 → 退出多选 → 关闭底部分类条 → 页面越界回退 → 两次退出。
- 遥控器方向键在左栏顶部/底部边界时不会误跳焦点，交由列表自身滚动。

---

## 三、设置页

- **主题切换、语言切换已隐藏**（锁定默认主题 + 简体中文）。
- **系统分组新增「清理缓存」**：右侧实时显示缓存占用大小，点击即清理，完成后刷新数值并 toast「已清理 X 缓存」。
- 配置地址页保留：`app_source` / `app_live` / `app_epg` 三个内置地址均为空字符串，全部由用户自行填写。
- 语言 / 主题 / 旧清理缓存入口（`llHomeIcon`）统一置为 `GONE`。

---

## 四、空壳播放器化

- 内置视频源、直播源、EPG 地址**全部清空**；`assets/epg_data.json`（4924 行内置节目单）已删除。
- 首次启动通过标记 `shell_url_reset_v1` 清空 Hawk 中残留的 `API_URL` / `LIVE_URL` / `EPG_URL`。
- `ApiConfig.getApiUrl()`：用户手填非空则用用户值，否则走空壳默认；`loadConfig()` 在空源时返回 `error("源地址为空")`。
- `JarLoader` 兼容 DecoTV 等分发的 `spider.jar`：不含 `com.github.catvod.spider.Init` 类时不再判整包失败，DexClassLoader 打开成功即视为加载成功。

---

## 五、品牌资源

| 资源 | 说明 |
|---|---|
| `res/drawable/app_icon.png` | HoopoeTV 图标 512×512 透明底 |
| `res/drawable/app_banner.png` | 640×360 深蓝底 + 白字品牌图 |
| `res/drawable/wallpaper_1~3.jpg` | 1920×1080 品牌壁纸（默认主题壁纸池） |
| `res/layout/dialog_about.xml` | 免责声明：「本 APP 仅供学习参考使用，测试教学源代码，请下载后于 24 小时内及时删除」 |

---

## 六、编译

命令行编译（Windows，无需 Android Studio）：

```bat
set JAVA_HOME=D:\AndroidDev\jdk17\jdk17.0.21_0
set ANDROID_HOME=D:\AndroidDev\android-sdk
set ANDROID_SDK_ROOT=D:\AndroidDev\android-sdk
gradlew.bat --project-dir . --no-daemon --console=plain :app:assembleArm64GenericNormalDebug
```

或直接双击仓库根目录的 `编译APK.bat`。

产物：`app/build/outputs/apk/arm64GenericNormal/debug/TVBox_debug-arm64-generic-java.apk`

> 注意：AGP 增量编译有时不刷新 APK。改完 Java / 资源后如发现打出来的还是旧包，先删除
> `app/build/intermediates/dex`、`app/build/intermediates/project_dex_archive` 与目标 APK 再编译。

---

## 七、主要改动文件

**新增**

```
app/src/main/res/drawable/shape_left_item.xml        左栏菜单背景（三态 + 7dp 内缩间距）
app/src/main/res/drawable/shape_filter_panel.xml    底部分类条磨砂背景
app/src/main/res/drawable/shape_content_paper.xml   右侧「纸面」内容区
app/src/main/res/drawable/fade_top.xml              拨轮上渐变遮罩
app/src/main/res/drawable/fade_bottom.xml           拨轮下渐变遮罩
app/src/main/res/drawable/wallpaper_1~3.jpg         品牌壁纸
编译APK.bat                                          一键编译脚本
```

**修改（关键）**

```
app/build.gradle                                    包名 / versionName
app/src/main/java/.../ui/activity/HomeActivity.java  左栏拨轮、源选择、切页过渡、返回键逻辑
app/src/main/java/.../ui/adapter/SortAdapter.java    竖排模式 + 等宽 + 选中态
app/src/main/java/.../ui/fragment/GridFragment.java  底部分类筛选条
app/src/main/java/.../ui/fragment/ModelSettingFragment.java  设置项隐藏 / 清理缓存
app/src/main/java/.../base/App.java                  锁定主题语言、首启清空地址
app/src/main/java/.../api/ApiConfig.java             空壳取源逻辑
app/src/main/java/com/github/catvod/crawler/JarLoader.java  兼容无 Init 的 spider.jar
app/src/main/res/layout/activity_home.xml            顶栏 + 左右分栏布局
app/src/main/res/layout/fragment_user.xml            首页推荐区
app/src/main/res/layout/fragment_grid.xml            分类页 + 筛选条
app/src/main/res/layout/fragment_model.xml           （及 layout-v21 同名文件）设置页
app/src/main/res/values/dimens.xml                   新增 vs_370
app/src/main/res/values/strings.xml / values-zh      品牌名、三地址置空
```

**删除**

```
app/src/main/assets/epg_data.json                    内置 EPG 数据（4924 行）
```

---

## 八、上游说明（保留原文）

```
=== Source Code - Editing the app default settings ===
/src/main/java/com/github/tvbox/osc/base/App.java

    private void initParams() {
        putDefault(HawkConfig.HOME_REC, 2);       // Home Rec 0=豆瓣, 1=推荐, 2=历史
        putDefault(HawkConfig.PLAY_TYPE, 1);      // Player   0=系统, 1=IJK, 2=Exo
        putDefault(HawkConfig.IJK_CODEC, "硬解码");// IJK Render 软解码, 硬解码
        putDefault(HawkConfig.HOME_SHOW_SOURCE, true);  // true=Show, false=Not show
        putDefault(HawkConfig.HOME_NUM, 2);       // History Number
        putDefault(HawkConfig.DOH_URL, 2);        // DNS
        putDefault(HawkConfig.SEARCH_VIEW, 2);    // Text or Picture
    }
```

---

## 免责声明

本项目仅供学习与技术研究使用，不提供、不分发任何影视资源。所有内容均由使用者自行配置的数据源提供，与作者无关。请于下载后 24 小时内删除。
