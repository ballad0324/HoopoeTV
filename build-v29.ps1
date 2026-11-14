$ErrorActionPreference = 'Continue'
$p = 'D:\Projects\Box\app\build.gradle'
$enc = New-Object Text.UTF8Encoding($false)
$s = [IO.File]::ReadAllText($p)
$s = $s -replace '(?m)^(\s*)versionCode \d+\s*$', '${1}versionCode 10029'
$s = $s -replace '(?m)^(\s*)versionName "v[0-9.]*"\s*$', '${1}versionName "v1.2.9"'
[IO.File]::WriteAllText($p, $s, $enc)
Select-String -LiteralPath $p -Pattern 'versionCode|versionName' | ForEach-Object { "$($_.LineNumber): $($_.Line.Trim())" }

$env:JAVA_HOME = 'D:\AndroidDev\jdk17\jdk17.0.21_0'
$env:ANDROID_HOME = 'D:\AndroidDev\android-sdk'
Push-Location 'D:\Projects\Box'
& .\gradlew.bat :app:assembleArm64GenericMobileDebug --console=plain *> 'D:\Projects\Box\build-v29.log'
Pop-Location
"gradle exit=$LASTEXITCODE"
Select-String -LiteralPath 'D:\Projects\Box\build-v29.log' -Pattern 'BUILD SUCCESSFUL|BUILD FAILED|error:|\.java:\d+:' | ForEach-Object { $_.Line.Trim() }

$out = 'D:\Projects\Box\app\build\outputs\apk\arm64GenericMobile\debug'
$src = Join-Path $out 'TVBox_debug-arm64-generic-mobile.apk'
if (Test-Path -LiteralPath $src) {
    Copy-Item -LiteralPath $src -Destination (Join-Path $out 'HoopoeTV_Box_v1.2.9.apk') -Force
    Get-ChildItem -LiteralPath $out -Filter 'HoopoeTV_Box_v1.2.9.apk' | ForEach-Object { "$($_.Name) $($_.Length) $($_.LastWriteTime)" }
} else {
    "APK not found: $src"
}









