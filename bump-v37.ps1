$s = [IO.File]::ReadAllText('D:\Projects\Box\build-v36.ps1')
$s = $s.Replace('10036', '10037').Replace('v1.3.6', 'v1.3.7').Replace('build-v36', 'build-v37')
[IO.File]::WriteAllText('D:\Projects\Box\build-v37.ps1', $s, (New-Object Text.UTF8Encoding($false)))

$g = [IO.File]::ReadAllText('D:\Projects\Box\app\build.gradle')
$g = $g.Replace('versionCode 10036', 'versionCode 10037').Replace('versionName "v1.3.6"', 'versionName "v1.3.7"')
[IO.File]::WriteAllText('D:\Projects\Box\app\build.gradle', $g, (New-Object Text.UTF8Encoding($false)))

Select-String -LiteralPath 'D:\Projects\Box\app\build.gradle' -Pattern 'versionCode|versionName' | Select-Object -First 3 | ForEach-Object { $_.Line.Trim() }
