Start-Sleep -Seconds 200
if (Test-Path -LiteralPath 'D:\Projects\Box\build-v67.log') {
    Select-String -LiteralPath 'D:\Projects\Box\build-v67.log' -Pattern 'BUILD SUCCESSFUL','BUILD FAILED','e: ','error:' |
        Select-Object -Last 15 | ForEach-Object { $_.Line.Trim() }
} else { 'no log' }
Get-ChildItem -LiteralPath 'D:\Projects\Box\app\build\outputs\apk\arm64GenericMobile\debug' -Filter '*.apk' |
    ForEach-Object { "$($_.Name) $($_.Length) $($_.LastWriteTime)" }
