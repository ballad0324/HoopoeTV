Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Image]::FromFile('D:\Projects\Box\shots\mumu-15-detail.png')
$crop = New-Object System.Drawing.Bitmap 900, 200
$g = [System.Drawing.Graphics]::FromImage($crop)
$g.DrawImage($src, (New-Object System.Drawing.Rectangle 0,0,900,200), (New-Object System.Drawing.Rectangle 0,760,900,200), [System.Drawing.GraphicsUnit]::Pixel)
$crop.Save('D:\Projects\Box\shots\crop-seam.png', [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $crop.Dispose(); $src.Dispose()
'ok'
