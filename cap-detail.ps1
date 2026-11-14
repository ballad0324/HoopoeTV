$adb = 'D:\AndroidDev\android-sdk\platform-tools\adb.exe'
$d = '127.0.0.1:16384'
& $adb -s $d shell input tap 230 1100
Start-Sleep -Seconds 7
& $adb -s $d shell screencap -p /sdcard/g2.png
& $adb -s $d pull /sdcard/g2.png 'D:\Projects\Box\shots\mumu-15-detail.png'
& $adb -s $d shell rm /sdcard/g2.png
& $adb -s $d logcat -d -t 200 | Select-String -Pattern 'RuntimeShader|AGSL|Fatal|AndroidRuntime|E/libEGL|skia' | Select-Object -Last 15 | ForEach-Object { $_.Line }
