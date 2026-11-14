Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Image]::FromFile('D:\Projects\Box\shots\mumu-15-detail.png')
$crop = New-Object System.Drawing.Bitmap 560, 420
$g = [System.Drawing.Graphics]::FromImage($crop)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.DrawImage($src, (New-Object System.Drawing.Rectangle 0,0,560,420), (New-Object System.Drawing.Rectangle 0,500,280,210), [System.Drawing.GraphicsUnit]::Pixel)
$crop.Save('D:\Projects\Box\shots\crop-corner.png', [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $crop.Dispose(); $src.Dispose()
'ok'
