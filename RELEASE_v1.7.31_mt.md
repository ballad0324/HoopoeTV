# HoopoeTV v1.7.31 (versionCode 10122) — 手机端设置项与功能对齐

这一版只做一件事：把手机端设置页里“点了没行为”的项去掉，把“有设置但没接线”的项真正接上。
改动依据是对 `mobile/` 源码逐项 grep 设置键的消费点，不是按设置页文案猜测。

## 一、删掉的 6 项（手机端无消费者，只有电视端有）

| 设置项 | 为什么删 |
|---|---|
| 后台播放器 `BACKGROUND_PLAY_TYPE` | `MobilePlayActivity.onPause()` 直接 `videoView.pause()`，没有后台续播/PiP；该键只被 TV `PlayActivity`/`BaseActivity` 消费 |
| 播放器渲染器 `EXO_RENDERER` | `player/EXOmPlayer.java` 完全不读 Hawk；TV 侧只有 `MediaSettingDialog` 拿它显示文字 |
| 渲染模式 `EXO_RENDERER_MODE` | 同上，`HawkUtils` 只读来显示描述 |
| 首选配置来源 `VOD_PLAYER_PREFERRED` | `getVodPlayerPreferredConfigurationFile()` 只被 TV `PlayActivity`/`PlayFragment` 调用；手机端固定 `PlayerHelper.updateCfg(videoView)` |
| 视频净化 `VIDEO_PURIFY` | `AdBlocker` 在 `mobile/` 下零引用，净化只发生在 TV 播放页 |
| 解析 WebView `PARSE_WEBVIEW` | `MobileParseHelper` 直接 `new WebView(act)`，键不起作用 |

同时删掉 `MobileSettingActivity` 里只 import 不使用的 `MediaSettingDialog`（死 import）。

## 二、真正接上的 4 项

| 设置项 | 之前的实际行为 | 现在 |
|---|---|---|
| 历史条数 `HOME_NUM` | `MobilePlayHistory.MAX` 硬编码 20，改成 100 条还是 20 | `MobilePlayHistory.limit()` 读 `HOME_NUM`，20/40/60/80/100 五档真实生效，越界自动钳位 |
| 画面比例 `PLAY_SCALE` | 只在播放页点按时循环，设置里的值不参与起播 | 起播 `applySavedScale()` 按设置恢复并更新比例文字；点按切换后回写 `PLAY_SCALE`，下次起播沿用 |
| 搜索展示 `SEARCH_VIEW` | 搜索结果固定文字列表 | `SEARCH_VIEW=1` 时用 3 列缩略图网格（新布局 `item_search_grid_mobile.xml`，海报+角标+标题），`0` 保持原信息行卡 |
| 快速搜索 `FAST_SEARCH_MODE` | 手机端只支持按键盘搜索键 | 开启后输入 400ms 防抖自动起搜；边输边搜不抢焦点、不逐字写历史，与联想请求分开调度 |

## 三、保留但标注清楚的项

- **播放缓存**（`IJK_CACHE_PLAY`）：只有 IJK 内核读这个键，文案改为“弱网卡顿时开启，只在 IJK 内核生效”。

## 四、包矩阵

| 文件 | 架构 | 内核 |
|---|---|---|
| `HoopoeTV_v1.7.31_arm64-v8a-java-mt.apk` | 64 位 ARM | 纯 Java |
| `HoopoeTV_v1.7.31_armeabi-v7a-java-mt.apk` | 32 位 ARM | 纯 Java |
| `HoopoeTV_v1.7.31_arm64-v8a-python-mt.apk` | 64 位 ARM | 内嵌 Python（Chaquopy） |
| `HoopoeTV_v1.7.31_armeabi-v7a-python-mt.apk` | 32 位 ARM | 内嵌 Python（Chaquopy） |

包名 `com.hoopoetv.tvbox`，minSdk 21 / targetSdk 28，debug 签名（v1+v2）。

## 五、已知仍未处理

- `CommonSetting` 的 `isLiveChannelReverse` / `isLiveCrossGroup` / `isLiveShowNetSpeed` / `isLiveShowTime` 全工程零读取，TV 端也是死键，本轮不动。
- `后台播放器`若要保留，需要给手机端补 PiP 与 `onPause` 判断，属于功能开发而不是设置接线。
- TV 端的字幕/解码详细设置（`MediaSettingDialog`）手机端仍无入口。
