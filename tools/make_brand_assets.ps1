param(
    [string]$SrcLogo = "D:\Projects\webhtv\tools\brand\logo_src.png",
    [string]$OutDir  = "D:\Projects\webhtv\tools\brand",
    [string]$Title   = "HoopoeTV",
    [string]$BgColor = "#07080C"
)

Add-Type -AssemblyName System.Drawing

function LoadBmp([string]$p) {
    $fs = [System.IO.FileStream]::new($p, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read)
    $img = [System.Drawing.Image]::FromStream($fs)
    $bmp = [System.Drawing.Bitmap]::new($img)
    $img.Dispose(); $fs.Dispose()
    return $bmp
}

function NewCanvas([int]$w, [int]$h) {
    $bmp = [System.Drawing.Bitmap]::new($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    return @{ bmp = $bmp; g = $g }
}

function SavePng($bmp, [string]$dst) {
    $bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

function GetRoundPath([single]$x, [single]$y, [single]$w, [single]$h, [single]$radius) {
    $p = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $d = $radius * 2
    if ($radius -ge [Math]::Min($w, $h) / 2) {
        $p.AddEllipse([single]$x, [single]$y, [single]$w, [single]$h)
        return $p
    }
    $p.AddArc([single]$x, [single]$y, [single]$d, [single]$d, 180, 90)
    $p.AddArc([single]($x + $w - $d), [single]$y, [single]$d, [single]$d, 270, 90)
    $p.AddArc([single]($x + $w - $d), [single]($y + $h - $d), [single]$d, [single]$d, 0, 90)
    $p.AddArc([single]$x, [single]($y + $h - $d), [single]$d, [single]$d, 90, 90)
    $p.CloseFigure()
    return $p
}

# 白色圆角卡 + 内嵌原始 logo（白底与卡片融为一体，靠 clip 切出圆角）
function DrawLogoCard($g, $logo, [single]$x, [single]$y, [single]$size, [single]$radius, [single]$inset) {
    $path = GetRoundPath $x $y $size $size $radius
    $white = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
    $g.FillPath($white, $path)
    $white.Dispose()

    $g.SetClip($path)
    $pad = $size * $inset
    $g.DrawImage($logo, [single]($x + $pad), [single]($y + $pad), [single]($size - $pad * 2), [single]($size - $pad * 2))
    $g.ResetClip()
    $path.Dispose()
}

function MakeSplash([string]$logoPng, [string]$dst, [int]$W, [int]$H, [string]$title, [string]$bgHex) {
    $c = NewCanvas $W $H
    $g = $c.g; $bmp = $c.bmp
    $g.Clear([System.Drawing.ColorTranslator]::FromHtml($bgHex))

    $glow = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $glow.AddEllipse([single]($W / 2 - $W * 0.55), [single]($H * 0.38 - $H * 0.30), [single]($W * 1.10), [single]($H * 0.60))
    $pg = [System.Drawing.Drawing2D.PathGradientBrush]::new($glow)
    $pg.CenterPoint = [System.Drawing.PointF]::new([single]($W / 2), [single]($H * 0.38))
    $pg.CenterColor = [System.Drawing.Color]::FromArgb(60, 47, 128, 237)
    $pg.SurroundColors = [System.Drawing.Color[]]@([System.Drawing.Color]::FromArgb(0, 47, 128, 237))
    $g.FillPath($pg, $glow)
    $pg.Dispose(); $glow.Dispose()

    $logo = LoadBmp $logoPng
    $card = $W * 0.40
    DrawLogoCard $g $logo ([single](($W - $card) / 2)) ([single]($H * 0.38 - $card / 2)) ([single]$card) ([single]($card * 0.24)) ([single]0.02)
    $logo.Dispose()

    $fam   = [System.Drawing.FontFamily]::new("Segoe UI")
    $font  = [System.Drawing.Font]::new($fam, [single]($W * 0.108), [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $fmt   = [System.Drawing.StringFormat]::new()
    $fmt.Alignment     = [System.Drawing.StringAlignment]::Center
    $fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
    $textRect = [System.Drawing.RectangleF]::new([single]0, [single]($H * 0.38 + $card / 2 + $H * 0.045), [single]$W, [single]($W * 0.16))
    $g.DrawString($title, $font, $brush, $textRect, $fmt)
    $brush.Dispose(); $fmt.Dispose(); $font.Dispose(); $fam.Dispose()

    $g.Dispose()
    SavePng $bmp $dst
}

function MakeIcon([string]$logoPng, [string]$dst, [int]$size, [bool]$round, [single]$inset) {
    $c = NewCanvas $size $size
    $g = $c.g; $bmp = $c.bmp
    $g.Clear([System.Drawing.Color]::Transparent)

    $radius = if ($round) { $size / 2.0 } else { $size * 0.22 }
    $logo = LoadBmp $logoPng
    DrawLogoCard $g $logo ([single]0) ([single]0) ([single]$size) ([single]$radius) ([single]$inset)
    $logo.Dispose()
    $g.Dispose()
    SavePng $bmp $dst
}

function MakeBadge([string]$logoPng, [string]$dst, [int]$size, [single]$inset) {
    $c = NewCanvas $size $size
    $g = $c.g; $bmp = $c.bmp
    $g.Clear([System.Drawing.Color]::Transparent)
    $logo = LoadBmp $logoPng
    DrawLogoCard $g $logo ([single]0) ([single]0) ([single]$size) ([single]($size * 0.22)) ([single]$inset)
    $logo.Dispose()
    $g.Dispose()
    SavePng $bmp $dst
}

if (-not (Test-Path -LiteralPath $OutDir)) { New-Item -ItemType Directory -Force -Path $OutDir | Out-Null }

MakeSplash $SrcLogo (Join-Path $OutDir "splash_2160x3840.png") 2160 3840 $Title $BgColor
"S1 splash done"
MakeIcon $SrcLogo (Join-Path $OutDir "icon_512.png") 512 $false 0.03
"S2 icon done"
MakeIcon $SrcLogo (Join-Path $OutDir "icon_round_512.png") 512 $true 0.03
"S3 icon round done"
MakeBadge $SrcLogo (Join-Path $OutDir "ic_logo_600.png") 600 0.03
"S4 badge done"

Get-ChildItem -LiteralPath $OutDir -Filter "*.png" | ForEach-Object {
    $img = [System.Drawing.Image]::FromFile($_.FullName)
    "{0,-24} {1}x{2}  {3:N1} KB" -f $_.Name, $img.Width, $img.Height, ($_.Length / 1KB)
    $img.Dispose()
}
