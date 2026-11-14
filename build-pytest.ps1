$ErrorActionPreference = 'Continue'
$env:JAVA_HOME = 'D:\AndroidDev\jdk17\jdk17.0.21_0'
$env:ANDROID_HOME = 'D:\AndroidDev\android-sdk'
Push-Location 'D:\Projects\Box'
& .\gradlew.bat :app:assembleArm64GenericPythonDebug --console=plain *> 'D:\Projects\Box\build-pytest.log'
Pop-Location
"gradle exit=$LASTEXITCODE"
Select-String -LiteralPath 'D:\Projects\Box\build-pytest.log' -Pattern 'BUILD SUCCESSFUL|BUILD FAILED|FAILURE:|error:|\.java:\d+:|e: ' | ForEach-Object { $_.Line.Trim() } | Select-Object -Last 25
$out = 'D:\Projects\Box\app\build\outputs\apk\arm64GenericPython\debug'
if (-not (Test-Path -LiteralPath $out)) { $out = 'D:\Projects\Box\app\build\outputs\apk' }
Get-ChildItem -LiteralPath $out -Recurse -Filter '*.apk' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 4 FullName,Length,LastWriteTime | Format-Table -AutoSize | Out-String