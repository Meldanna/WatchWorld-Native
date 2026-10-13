param(
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $repoRoot 'fixtures\.env.local'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Missing fixtures\.env.local. Copy fixtures\.env.example and create the ignored local config first.'
}

$config = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -notmatch '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
        continue
    }

    $name = $Matches[1]
    $value = $Matches[2].Trim()
    if ($value.Length -ge 2 -and
        (($value.StartsWith('"') -and $value.EndsWith('"')) -or
         ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    } else {
        $value = [regex]::Replace($value, '\s+#.*$', '').TrimEnd()
    }
    $config[$name] = $value
}

$apiKey = $config['WW_T04_DEEPSEEK_API_KEY']
if ([string]::IsNullOrWhiteSpace($apiKey)) {
    $apiKey = $env:DEEPSEEK_API_KEY
}
$baseUrl = $config['WW_T04_DEEPSEEK_BASE_URL']
if ([string]::IsNullOrWhiteSpace($baseUrl)) {
    $baseUrl = 'https://api.deepseek.com/v1'
}
$model = $config['WW_T04_DEEPSEEK_MODEL']
if ([string]::IsNullOrWhiteSpace($model)) {
    $model = 'deepseek-chat'
}

if ($DryRun) {
    [pscustomobject]@{
        EndpointHost = ([Uri]$baseUrl).Host
        Model = $model
        ApiKeyConfigured = -not [string]::IsNullOrWhiteSpace($apiKey)
        KeySource = if (-not [string]::IsNullOrWhiteSpace($config['WW_T04_DEEPSEEK_API_KEY'])) {
            'fixtures/.env.local'
        } elseif (-not [string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY)) {
            'DEEPSEEK_API_KEY environment variable'
        } else {
            'not configured'
        }
        SendsRequest = $false
    }
    exit 0
}

if ([string]::IsNullOrWhiteSpace($apiKey)) {
    throw 'Set WW_T04_DEEPSEEK_API_KEY in fixtures\.env.local. No request was sent.'
}

$env:WW_T03_API_KEY = $apiKey
$env:WW_T03_BASE_URL = $baseUrl
$env:WW_T03_MODEL = $model
$env:WW_T04_LIVE_DEEPSEEK = '1'

Push-Location $repoRoot
try {
    & (Join-Path $repoRoot 'gradlew.bat') ':shared:desktopTest' '--tests' 'com.watchworld.net.DeepSeekLiveSmokeTest' '--no-build-cache' '--rerun-tasks'
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
