param(
    [string]$Title = 'WatchWorld Native',
    [string]$OutPng = '',
    # 判定基准(emerald/浅色默认);换主题时同步改,见 README.md
    [int]$PrimaryR = 0,   [int]$PrimaryG = 153, [int]$PrimaryB = 102,  # emerald-600 v4 oklch 渲染值 #009966
    [int]$PurpleR = 103,  [int]$PurpleG = 80,   [int]$PurpleB = 164     # M3 默认紫 #6750A4(必须 0 像素)
)

# T12 E2E · 键入后校验:点击输入框聚焦 → SendKeys 键入 → 截图。
# 「发送」按钮启用后主色必须出现(emerald),M3 默认紫必须 0 像素 → RESULT: PASS。
# 注意:会真的把字打进焦点控件(顺带回归 T03 流式链路,消息会真实发出)。
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32C {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
}
"@

[Win32C]::SetProcessDPIAware() | Out-Null

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\')).Path
if (-not $OutPng) { $OutPng = Join-Path $repoRoot 'build\ww-theme-typed.png' }
New-Item -ItemType Directory -Force -Path (Split-Path $OutPng) | Out-Null

$proc = Get-Process | Where-Object { $_.MainWindowTitle -eq $Title } | Select-Object -First 1
if (-not $proc) { Write-Output 'RESULT: WINDOW-NOT-FOUND'; exit 1 }

# 前台被锁时仅 SetForegroundWindow 会被拦 → 键入会打进别的窗口;AppActivate 是关键
(New-Object -ComObject WScript.Shell).AppActivate($proc.Id) | Out-Null
Start-Sleep -Milliseconds 600
[Win32C]::SetForegroundWindow($proc.MainWindowHandle) | Out-Null
Start-Sleep -Seconds 2

# click into the text field area (bottom-left of window, input line) to give it keyboard focus
$rect = New-Object Win32C+RECT
[Win32C]::GetWindowRect($proc.MainWindowHandle, [ref]$rect) | Out-Null
$w = $rect.Right - $rect.Left; $h = $rect.Bottom - $rect.Top
$cx = $rect.Left + [int]($w * 0.40); $cy = $rect.Top + [int]($h * 0.945)
Add-Type @"
using System; using System.Runtime.InteropServices;
public class Mouse { [DllImport("user32.dll")] public static extern bool SetCursorPos(int x,int y);
[DllImport("user32.dll")] public static extern void mouse_event(uint f,uint dx,uint dy,uint d,int e); }
"@
[Mouse]::SetCursorPos($cx, $cy) | Out-Null
Start-Sleep -Milliseconds 200
[Mouse]::mouse_event(2, 0, 0, 0, 0)   # left down
[Mouse]::mouse_event(4, 0, 0, 0, 0)   # left up
Start-Sleep -Milliseconds 800

# type text via SendKeys (caret should turn emerald; send button becomes enabled & emerald)
[System.Windows.Forms.SendKeys]::SendWait("Hello WatchWorld theme!")
Start-Sleep -Seconds 2

$bmp = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($rect.Left, $rect.Top, 0, 0, (New-Object System.Drawing.Size($w, $h)))
$g.Dispose()
$bmp.Save($OutPng, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Output ("SAVED: " + $OutPng)

# count primary pixels + locate bounding box
$green = 0; $purple = 0; $minX = $w; $minY = $h; $maxX = 0; $maxY = 0
for ($x = 0; $x -lt $bmp.Width; $x += 4) {
    for ($y = 0; $y -lt $bmp.Height; $y += 4) {
        $p = $bmp.GetPixel($x, $y)
        if (([math]::Abs($p.R - $PrimaryR) + [math]::Abs($p.G - $PrimaryG) + [math]::Abs($p.B - $PrimaryB)) -lt 60) {
            $green++
            if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y }
        } elseif (([math]::Abs($p.R - $PurpleR) + [math]::Abs($p.G - $PurpleG) + [math]::Abs($p.B - $PurpleB)) -lt 40) {
            # 阈值 40:紫判定收紧 —— L1 距 59 会把 slate 灰误报成紫(实测踩过)
            $purple++
        }
    }
}
$bmp.Dispose()
Write-Output ("PRIMARY_PIXELS: " + $green)
if ($green -gt 0) {
    Write-Output ("PRIMARY_BBOX: x " + $minX + "-" + $maxX + ", y " + $minY + "-" + $maxY + "  (window " + $w + "x" + $h + ")")
}
Write-Output ("M3_PURPLE_PIXELS: " + $purple)
if ($green -gt 30 -and $purple -eq 0) { Write-Output 'RESULT: PASS' } else { Write-Output 'RESULT: CHECK' }
