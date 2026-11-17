# HoopoeTV v1.7.32 (versionCode 10123) — 清理直播设置的死封装

## 背景

`CommonSetting` 里有 4 个直播相关的静态方法：

```java
isLiveChannelReverse()  // 换台反转
isLiveCrossGroup()      // 跨选分类
isLiveShowNetSpeed()    // 显示网速
isLiveShowTime()        // 显示时间
```

全工程（`app/src` 下 java + kotlin）**零调用**：电视端直播播放页 `LivePlayActivity` 直接读 `Hawk.get(HawkConfig.LIVE_*)`，从不走这层封装；手机端 `mobile/` 目录完全不读这几个键。

## 这一版做了什么

- 删除上述 4 个未被调用的封装方法（连同 `CommonSetting` 里的「直播（两端共用）」小节目录注释）。
- **不动** `HawkConfig.LIVE_CHANNEL_REVERSE` / `LIVE_CROSS_GROUP` / `LIVE_SHOW_NET_SPEED` / `LIVE_SHOW_TIME` 四个键——电视端直播面板「偏好设置」的显示时间、显示网速、换台反转、跨选分类，以及 `getNextChannel()` 的跨组换台逻辑都靠它们。
- **不动** `SettingsSyncHelper` 里的同步白名单，双端设置同步照旧。

行为上零变化，纯粹删死代码。

## 包矩阵

| 文件 | 架构 | 内核 |
|---|---|---|
| `HoopoeTV_v1.7.32_arm64-v8a-java-mt.apk` | 64 位 ARM | 纯 Java |
| `HoopoeTV_v1.7.32_armeabi-v7a-java-mt.apk` | 32 位 ARM | 纯 Java |
| `HoopoeTV_v1.7.32_arm64-v8a-python-mt.apk` | 64 位 ARM | 内嵌 Python（Chaquopy） |
| `HoopoeTV_v1.7.32_armeabi-v7a-python-mt.apk` | 32 位 ARM | 内嵌 Python（Chaquopy） |

包名 `com.hoopoetv.tvbox`，minSdk 21 / targetSdk 28，debug 签名（v1+v2）。
