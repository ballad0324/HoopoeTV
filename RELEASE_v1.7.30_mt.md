# HoopoeTV v1.7.30 (versionCode 10121) — 四包发布说明

包名 `com.hoopoetv.tvbox` · Android 5.0+（minSdk 21）· targetSdk 28 · debug 签名（v1+v2）

## 下载

| 文件 | 大小 | 架构 | 内核 |
|---|---|---|---|
| HoopoeTV_v1.7.30_arm64-v8a-java-mt.apk | 41.75 MB | 64 位 ARM | 纯 Java |
| HoopoeTV_v1.7.30_arm64-v8a-python-mt.apk | 53.74 MB | 64 位 ARM | 内嵌 Python（Chaquopy） |
| HoopoeTV_v1.7.30_armeabi-v7a-java-mt.apk | 39.80 MB | 32 位 ARM | 纯 Java |
| HoopoeTV_v1.7.30_armeabi-v7a-python-mt.apk | 51.25 MB | 32 位 ARM | 内嵌 Python（Chaquopy） |

命名规则：`HoopoeTV_<版本>_<CPU架构>-<内核>-mt.apk`

- `arm64-v8a`：64 位设备（近 5–6 年电视盒子/手机/电视）
- `armeabi-v7a`：32 位老设备
- `java`：纯 Java 内核，体积小、启动快，跑 JS/Java 源
- `python`：内嵌 Python 运行时（lxml / requests / pyquery / bs4 / pycryptodome），可跑 Python 爬虫源
- `mt`：Mobile + TV 双端合一，默认手机 UI，设置内可切回 TV UI

## 本版内容

- 打包矩阵：新增 `mtjava` / `mtpython` 两个 mode flavor，与 `arm64` / `armeabi` 交叉出四包；产物名自带架构与内核标识
- 手机端 UI：设置页卡片化 + 胶囊开关、底部导航点击态、首页继续观看条、播放页手势与续播
- 设置层：CommonSetting 对齐 TV 端与手机端共用项
- 收藏：统一到 Room，新增 FavTombstone 去重
- 主题：SkinManager 七套主题（#D81F26 / #18A2E7 / #004B93 / #FF7439 / #FFD55E 等）
- 直播：修复接口 json 无 `lives` 字段导致分组建不出来的根因
- 依赖：`pyquery` 钉 1.4.3（Chaquopy 源缺 `cssselect>=1.5.0`）

## 已知限制

- debug 签名，正式发布需换 release 签名并开 minify/shrinkResources
- python 包依赖钉版可能影响个别新版 Python 源
- 四包尚未在真机完成「默认手机 UI → 切 TV UI → python 源加载」全链路实测

## 构建方式

```
gradlew.bat :app:assembleArm64GenericMtjavaDebug
gradlew.bat :app:assembleArmeabiGenericMtjavaDebug
gradlew.bat :app:assembleArm64GenericMtpythonDebug
gradlew.bat :app:assembleArmeabiGenericMtpythonDebug
```

必须串行执行（并行会导致 JVM native OOM）；内存参数：

```
-Dorg.gradle.jvmargs=-Xmx4608m -XX:MaxMetaspaceSize=1024m -XX:ReservedCodeCacheSize=512m
```

python 包额外需要 Python 3.8 安装于 `D:/Programs/Python/Python38`（见 `pyramid/build.gradle`）。
