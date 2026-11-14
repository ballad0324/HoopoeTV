$ErrorActionPreference = 'Continue'
$g = 'D:\Projects\Box\app\build.gradle'
$enc = New-Object Text.UTF8Encoding($false)
$t = [IO.File]::ReadAllText($g)
$t = $t -replace '(?m)^(\s*)versionCode \d+\s*$', '${1}versionCode 10066'
$t = $t -replace '(?m)^(\s*)versionName "v[0-9.]*"\s*$', '${1}versionName "v1.6.15"'
[IO.File]::WriteAllText($g, $t, $enc)

$env:JAVA_HOME = 'D:\AndroidDev\jdk17\jdk17.0.21_0'
$env:ANDROID_HOME = 'D:\AndroidDev\android-sdk'
Push-Location 'D:\Projects\Box'
& .\gradlew.bat :app:assembleArm64GenericMobileDebug --console=plain --no-daemon --max-workers=2 *> 'D:\Projects\Box\build-v67.log'
$code = $LASTEXITCODE
Pop-Location
"gradle exit=$code"
$out = 'D:\Projects\Box\app\build\outputs\apk\arm64GenericMobile\debug'
$src = Join-Path $out 'TVBox_debug-arm64-generic-mobile.apk'
if ($code -eq 0 -and (Test-Path -LiteralPath $src)) {
    Copy-Item -LiteralPath $src -Destination (Join-Path $out 'HoopoeTV_Box_v1.6.15.apk') -Force
    Get-ChildItem -LiteralPath $out -Filter 'HoopoeTV_Box_v1.6.15.apk' | ForEach-Object { "$($_.Name) $($_.Length) $($_.LastWriteTime)" }
} else { "BUILD not ok, skip copy" }
