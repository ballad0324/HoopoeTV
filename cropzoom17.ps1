Add-Type -AssemblyName System.Drawing

function CropZoom {
    param([string]$src, [string]$dst, [int]$x, [int]$y, [int]$w, [int]$h, [double]$scale)
    $img = [System.Drawing.Image]::FromFile($src)
    $ow = [int]($w * $scale)
    $oh = [int]($h * $scale)
    $bmp = [System.Drawing.Bitmap]::new($ow, $oh)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $destRect = [System.Drawing.Rectangle]::new(0, 0, $ow, $oh)
    $g.DrawImage($img, $destRect, $x, $y, $w, $h, [System.Drawing.GraphicsUnit]::Pixel)
    $bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
    $img.Dispose()
}

$d = 'D:\Projects\Box\shots'
CropZoom "$d\v117-detail.png" "$d\zoom-pill17.png" 30 785 600 95 2
CropZoom "$d\v117-detail.png" "$d\zoom-corner17.png" 10 505 260 200 3
CropZoom "$d\v117-detail.png" "$d\zoom-circle17.png" 620 790 220 90 3
Write-Output 'crops done'
