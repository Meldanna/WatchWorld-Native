param(
    [string]$Title = 'WatchWorld Native',
    [string]$OutPng = '',
    # 判定基准(默认主题 emerald-day + 浅色):换主题时先从 snapshots.json 取 600 档 oklch 换算出期望 RGB 再改这里
    # ⚠️ 2026-10-13 更新:默认主题是 `emerald-day`,走**白天低饱和表**(m1-600 = oklch(59.6% 0.075 163.225))
    #    → 渲染值 #528D72。旧注释写的 #009966 是 emerald-600 的 **HIGH 表**(C=0.145)值,
    #    只对应 `emerald-night`/多巴胺这类高饱和主题,不再是默认观感。
    [int]$PrimaryR = 82,  [int]$PrimaryG = 141, [int]$PrimaryB = 114,  # emerald-day m1-600 → #528D72
    [int]$BgR = 248,      [int]$BgG = 250,      [int]$BgB = 252,        # surface1Light #F8FAFC
    [int]$PurpleR = 103,  [int]$PurpleG = 80,   [int]$PurpleB = 164     # M3 默认紫 #6750A4(必须 0 像素)
)

# T12 E2E · 空界面校验:等窗口出现 → 截图 → 数像素(主色 / 默认紫 / 背景)。
# 判定基准与来源见同目录 README.md;输出落 build/。
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32 {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
}
"@

[Win32]::SetProcessDPIAware() | Out-Null

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\')).Path
if (-not $OutPng) { $OutPng = Join-Path $repoRoot 'build\ww-theme-verify.png' }
New-Item -ItemType Directory -Force -Path (Split-Path $OutPng) | Out-Null

# 1) wait for the window (up to 4 min: first gradle run may compile)
$deadline = (Get-Date).AddMinutes(4)
$proc = $null
while ((Get-Date) -lt $deadline) {
    $proc = Get-Process | Where-Object { $_.MainWindowTitle -eq $Title } | Select-Object -First 1
    if ($proc) { break }
    Start-Sleep -Seconds 5
}
if (-not $proc) { Write-Output 'RESULT: WINDOW-NOT-FOUND'; exit 1 }
Write-Output ("WINDOW: pid=" + $proc.Id)
Start-Sleep -Seconds 8   # let first frames render

# 前台被锁时 SetForegroundWindow 会被拦 → 截到别的窗口;先 AppActivate。
# 最小化窗口的 GetWindowRect 是 L=-32000 的退化矩形(实测踩过) → 必须先还原。
(New-Object -ComObject WScript.Shell).AppActivate($proc.Id) | Out-Null
Start-Sleep -Milliseconds 500
Add-Type -Namespace Ww -Name Win -MemberDefinition '
[DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);'
[Ww.Win]::ShowWindow($proc.MainWindowHandle, 9) | Out-Null   # SW_RESTORE
[Win32]::SetForegroundWindow($proc.MainWindowHandle) | Out-Null
Start-Sleep -Seconds 2

# 2) screenshot of the app window rect
$rect = New-Object Win32+RECT
[Win32]::GetWindowRect($proc.MainWindowHandle, [ref]$rect) | Out-Null
$w = $rect.Right - $rect.Left
$h = $rect.Bottom - $rect.Top
if ($w -le 0 -or $h -le 0) { Write-Output 'RESULT: WINDOW-RECT-EMPTY'; exit 1 }
Write-Output ("RECT: " + $w + "x" + $h)

$bmp = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($rect.Left, $rect.Top, 0, 0, (New-Object System.Drawing.Size($w, $h)))
$g.Dispose()
$bmp.Save($OutPng, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Output ("SAVED: " + $OutPng)

# 3) classify sampled pixels
$green = 0; $purple = 0; $bg = 0; $total = 0
$samples = New-Object System.Collections.Generic.List[string]
for ($x = 0; $x -lt $bmp.Width; $x += 6) {
    for ($y = 0; $y -lt $bmp.Height; $y += 6) {
        $p = $bmp.GetPixel($x, $y)
        $total++
        $dPrimary = [math]::Abs($p.R - $PrimaryR) + [math]::Abs($p.G - $PrimaryG) + [math]::Abs($p.B - $PrimaryB)
        $dPurple = [math]::Abs($p.R - $PurpleR) + [math]::Abs($p.G - $PurpleG) + [math]::Abs($p.B - $PurpleB)
        $dBg = [math]::Abs($p.R - $BgR) + [math]::Abs($p.G - $BgG) + [math]::Abs($p.B - $BgB)
        if ($dPrimary -lt 60) {
            $green++
            if ($samples.Count -lt 6) { $samples.Add(("primary($x,$y)=#{0:X2}{1:X2}{2:X2}" -f $p.R, $p.G, $p.B)) }
        } elseif ($dPurple -lt 40) {
            # 阈值 40:紫判定收紧 —— L1 距 59 会把 slate 灰(#67728B 等)误报成紫(实测踩过)
            $purple++
        }
        if ($dBg -lt 12) { $bg++ }
    }
}
$bmp.Dispose()

Write-Output ("SAMPLED: " + $total)
Write-Output ("PRIMARY_PIXELS: " + $green)
Write-Output ("M3_DEFAULT_PURPLE_PIXELS: " + $purple)
Write-Output ("SURFACE_BG_PIXELS: " + $bg)
foreach ($s in $samples) { Write-Output ("SAMPLE " + $s) }
# 空界面可能只有背景色(主色像素=0 合法);默认紫出现才是一票否决
if ($purple -eq 0 -and $bg -gt 30) { Write-Output 'RESULT: PASS' } else { Write-Output 'RESULT: CHECK' }
