@echo off
chcp 65001 >nul
set JAVA_HOME=D:\AndroidDev\jdk17\jdk17.0.21_0
set ANDROID_HOME=D:\AndroidDev\android-sdk
set ANDROID_SDK_ROOT=D:\AndroidDev\android-sdk
cd /d D:\Projects\Box

echo ============================================
echo  编译 TVBox  APK
echo   arm64 + generic + java模式 (Debug)
echo ============================================
call gradlew.bat --no-daemon --console=plain :app:assembleArm64GenericNormalDebug
if errorlevel 1 (
    echo.
    echo [失败] 编译未通过，看上面的报错
    pause
    exit /b 1
)
echo.
echo [成功] 产物位置：
echo   D:\Projects\Box\app\build\outputs\apk\arm64GenericNormal\debug\TVBox_debug-arm64-generic-java.apk
echo.
echo 其他常用组合，把最后一行命令换成对应任务即可：
echo   armeabi 架构        : :app:assembleArmeabiGenericNormalDebug
echo   arm64 架构          : :app:assembleArm64GenericNormalDebug
echo   海信定制(generic->hisense) : :app:assembleArm64HisenseNormalDebug
echo   纯净release版       : :app:assembleArm64GenericNormalRelease
echo.
pause
