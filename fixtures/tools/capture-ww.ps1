param(
    [string]$Title = 'WatchWorld Native',
    [string]$OutPng = ''
)

# T12 E2E · 基础截图:前置应用窗口,截取 + 关键位置采样 + 主色直方图。
# 用法见同目录 README.md。输出默认落 build/(clean 会清,属预期)。
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32B {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
}
"@

[Win32B]::SetProcessDPIAware() | Out-Null

# 仓库根 = fixtures/tools 的上两级;截图等产物落 build/(Gradle clean 责任区)。
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\')).Path
if (-not $OutPng) { $OutPng = Join-Path $repoRoot 'build\ww-theme-verify2.png' }
New-Item -ItemType Directory -Force -Path (Split-Path $OutPng) | Out-Null

$proc = Get-Process | Where-Object { $_.MainWindowTitle -eq $Title } | Select-Object -First 1
if (-not $proc) { Write-Output 'RESULT: WINDOW-NOT-FOUND'; exit 1 }
Write-Output ("WINDOW: pid=" + $proc.Id)

# restore if minimized, then force-foreground via both mechanisms
# (仅 SetForegroundWindow 会被系统前台锁拦掉 → 截到终端;AppActivate 是关键)
[Win32B]::ShowWindow($proc.MainWindowHandle, 9) | Out-Null   # SW_RESTORE
(New-Object -ComObject WScript.Shell).AppActivate($proc.Id) | Out-Null
Start-Sleep -Milliseconds 500
[Win32B]::SetForegroundWindow($proc.MainWindowHandle) | Out-Null
Start-Sleep -Seconds 4

$rect = New-Object Win32B+RECT
[Win32B]::GetWindowRect($proc.MainWindowHandle, [ref]$rect) | Out-Null
$w = $rect.Right - $rect.Left
$h = $rect.Bottom - $rect.Top
Write-Output ("RECT: L=" + $rect.Left + " T=" + $rect.Top + " " + $w + "x" + $h)

$bmp = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($rect.Left, $rect.Top, 0, 0, (New-Object System.Drawing.Size($w, $h)))
$g.Dispose()
$bmp.Save($OutPng, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Output ("SAVED: " + $OutPng)

# exact pixels at fractional positions (x,y as fractions of window size)
$points = @(
    @(0.50, 0.02, 'titlebar'),
    @(0.50, 0.20, 'upper-content'),
    @(0.10, 0.50, 'left-mid'),
    @(0.50, 0.50, 'center'),
    @(0.90, 0.50, 'right-mid'),
    @(0.50, 0.85, 'lower-content'),
    @(0.85, 0.95, 'inputbar-area'),
    @(0.50, 0.97, 'bottom-edge')
)
foreach ($pt in $points) {
    $x = [int]($pt[0] * ($w - 1)); $y = [int]($pt[1] * ($h - 1))
    $p = $bmp.GetPixel($x, $y)
    Write-Output ("PT {0} ({1},{2}) = #{3:X2}{4:X2}{5:X2}" -f $pt[2], $x, $y, $p.R, $p.G, $p.B)
}

# dominant colors histogram
$hist = @{}
for ($x = 0; $x -lt $bmp.Width; $x += 10) {
    for ($y = 0; $y -lt $bmp.Height; $y += 10) {
        $p = $bmp.GetPixel($x, $y)
        $key = '{0:X2}{1:X2}{2:X2}' -f $p.R, $p.G, $p.B
        if ($hist.ContainsKey($key)) { $hist[$key]++ } else { $hist[$key] = 1 }
    }
}
Write-Output 'DOMINANT:'
$hist.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 10 | ForEach-Object {
    Write-Output ("  " + $_.Key + " x" + $_.Value)
}
$bmp.Dispose()

# expectations: center should be theme surface (light #F8FAFC or dark #0F131C)
Write-Output 'RESULT: CAPTURED'
