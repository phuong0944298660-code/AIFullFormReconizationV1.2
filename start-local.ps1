param(
  [int]$BackendPort = 18083,
  [int]$FrontendPort = 5197,
  [int]$FieldOcrPort = 18092,
  [switch]$SkipBuild,
  [switch]$WithSidecar,
  [switch]$NoSidecar,
  [switch]$KeepExisting
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$LogDir = Join-Path $Root "logs"
$StatePath = Join-Path $Root "tmp\local-services.json"
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $StatePath) | Out-Null

function Repair-PathEnvironment {
  $pathValue = [Environment]::GetEnvironmentVariable("Path", "Process")
  if ([string]::IsNullOrWhiteSpace($pathValue)) {
    $pathValue = [Environment]::GetEnvironmentVariable("PATH", "Process")
  }
  if (-not [string]::IsNullOrWhiteSpace($pathValue)) {
    [Environment]::SetEnvironmentVariable("PATH", $null, "Process")
    [Environment]::SetEnvironmentVariable("Path", $pathValue, "Process")
  }
}

function Import-CmdSetFile([string]$Path) {
  if (-not (Test-Path -LiteralPath $Path)) {
    return
  }
  Get-Content -LiteralPath $Path | ForEach-Object {
    $line = $_.Trim()
    if ($line -match '^set\s+"?([^=\s"]+)=(.*)"?\s*$') {
      $name = $matches[1].Trim()
      $value = $matches[2]
      if ($value.EndsWith('"')) {
        $value = $value.Substring(0, $value.Length - 1)
      }
      [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
  }
}

function Get-PortOwnerProcessIds([int]$Port) {
  $pids = @()
  try {
    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop
    $pids += @($connections | Select-Object -ExpandProperty OwningProcess -Unique | Where-Object { $_ -and $_ -gt 0 })
  } catch {
  }
  if ($pids.Count -eq 0) {
    $escapedPort = [regex]::Escape([string]$Port)
    $lines = @(netstat -ano | Where-Object { $_ -match "[:.]$escapedPort\s+.*LISTENING\s+(\d+)\s*$" })
    foreach ($line in $lines) {
      if ($line -match "LISTENING\s+(\d+)\s*$") {
        $pids += [int]$matches[1]
      }
    }
  }
  return @($pids | Select-Object -Unique)
}

function Stop-ProcessesOnPort([int]$Port) {
  $pids = Get-PortOwnerProcessIds $Port
  foreach ($processId in $pids) {
    try {
      Stop-Process -Id $processId -Force -ErrorAction Stop
      Write-Host "Stopped process $processId on port $Port"
    } catch {
      Write-Warning "Could not stop process $processId on port ${Port}: $($_.Exception.Message)"
    }
  }
}

function Get-PortOwnerProcessId([int]$Port) {
  $pids = Get-PortOwnerProcessIds $Port
  if ($pids.Count -gt 0) {
    return [int]$pids[0]
  }
  return $null
}

function Resolve-JavaExecutable {
  $candidates = @()
  if ($env:JAVA_HOME) {
    $candidates += (Join-Path $env:JAVA_HOME "bin\java.exe")
  }
  $candidates += "C:\Apps\common\Java\Java17.0.17\bin\java.exe"
  $commands = @(Get-Command java.exe -All -ErrorAction SilentlyContinue)
  $candidates += @($commands | Select-Object -ExpandProperty Source)
  foreach ($candidate in $candidates) {
    if ([string]::IsNullOrWhiteSpace($candidate)) {
      continue
    }
    if ((Test-Path -LiteralPath $candidate) -and ($candidate -notlike "*\Common Files\Oracle\Java\javapath\*")) {
      return $candidate
    }
  }
  if ($commands.Count -gt 0) {
    return $commands[0].Source
  }
  throw "java.exe not found. Set JAVA_HOME or install Java 17."
}

function Stop-StateProcesses([string]$Path) {
  if (-not (Test-Path -LiteralPath $Path)) {
    return
  }
  try {
    $state = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    foreach ($name in @("frontendPid", "backendPid", "fieldOcrPid")) {
      $processId = $state.$name
      if ($processId -and [int]$processId -gt 0) {
        try {
          Stop-Process -Id ([int]$processId) -Force -ErrorAction Stop
          Write-Host "Stopped previous $name $processId"
        } catch {
        }
      }
    }
  } catch {
    Write-Warning "Could not read previous state ${Path}: $($_.Exception.Message)"
  }
}

function Wait-PortFree([int]$Port, [int]$Seconds) {
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    $listeners = Get-PortOwnerProcessIds $Port
    if ($listeners.Count -eq 0) {
      return $true
    }
    Start-Sleep -Milliseconds 300
  }
  Write-Warning "Port $Port is still in use after ${Seconds}s."
  return $false
}

function Wait-Http([string]$Name, [string]$Url, [int]$Seconds) {
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    try {
      $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
        Write-Host "$Name ready: $Url"
        return $true
      }
    } catch {
      Start-Sleep -Seconds 1
    }
  }
  Write-Warning "$Name did not become ready within ${Seconds}s: $Url"
  return $false
}

Repair-PathEnvironment
Import-CmdSetFile (Join-Path $Root "llm.local.cmd")
Import-CmdSetFile (Join-Path $Root "baidu-ocr.local.cmd")

if (-not $env:LLM_API_KEY -and -not $env:DASHSCOPE_API_KEY) {
  throw "Set LLM_API_KEY or DASHSCOPE_API_KEY, or create llm.local.cmd."
}

$shouldStartSidecar = $WithSidecar -and -not $NoSidecar
$env:SERVER_PORT = [string]$BackendPort
$env:FRONTEND_PORT = [string]$FrontendPort
$env:VITE_BACKEND_ORIGIN = "http://127.0.0.1:$BackendPort"
$env:BACKEND_ORIGIN = $env:VITE_BACKEND_ORIGIN
$env:RAG_ENABLED = "false"
$env:FIELD_OCR_ENABLED = $(if ($shouldStartSidecar) { "true" } else { "false" })
$env:FIELD_OCR_BASE_URL = "http://127.0.0.1:$FieldOcrPort"
$env:FIELD_OCR_PORT = [string]$FieldOcrPort
$env:npm_config_cache = Join-Path $Root "frontend\.npm-cache"
if (-not $env:LLM_PAGE_CONCURRENCY) {
  $env:LLM_PAGE_CONCURRENCY = "2"
}
if (-not $env:FDH_REVIEW_FILE_CONCURRENCY) {
  $env:FDH_REVIEW_FILE_CONCURRENCY = "2"
}
if (-not $env:OCR_PAGE_MAX_IMAGE_LONG_SIDE) {
  $env:OCR_PAGE_MAX_IMAGE_LONG_SIDE = "1800"
}
try {
  $llmTimeoutSeconds = [int]$env:LLM_TIMEOUT_SECONDS
} catch {
  $llmTimeoutSeconds = 0
}
if ($llmTimeoutSeconds -le 0 -or $llmTimeoutSeconds -gt 120) {
  $env:LLM_TIMEOUT_SECONDS = "120"
}

if (-not $KeepExisting) {
  Stop-StateProcesses $StatePath
  Stop-ProcessesOnPort $FrontendPort
  Stop-ProcessesOnPort $BackendPort
  if ($shouldStartSidecar) {
    Stop-ProcessesOnPort $FieldOcrPort
  }
  if (-not (Wait-PortFree $FrontendPort 8)) {
    throw "Frontend port $FrontendPort is still in use."
  }
  if (-not (Wait-PortFree $BackendPort 8)) {
    throw "Backend port $BackendPort is still in use."
  }
  if ($shouldStartSidecar) {
    if (-not (Wait-PortFree $FieldOcrPort 8)) {
      throw "Field OCR sidecar port $FieldOcrPort is still in use."
    }
  }
}

if (-not $SkipBuild) {
  Write-Host "Packaging backend jar..."
  Push-Location (Join-Path $Root "backend")
  try {
    & mvn.cmd -DskipTests package
    if ($LASTEXITCODE -ne 0) {
      throw "Backend package failed with exit code $LASTEXITCODE."
    }
  } finally {
    Pop-Location
  }
}

$backendOut = Join-Path $LogDir "backend-$BackendPort-$Stamp.out.log"
$backendErr = Join-Path $LogDir "backend-$BackendPort-$Stamp.err.log"
$frontendOut = Join-Path $LogDir "frontend-$FrontendPort-$Stamp.out.log"
$frontendErr = Join-Path $LogDir "frontend-$FrontendPort-$Stamp.err.log"
$sidecarOut = Join-Path $LogDir "field-ocr-$FieldOcrPort-$Stamp.out.log"
$sidecarErr = Join-Path $LogDir "field-ocr-$FieldOcrPort-$Stamp.err.log"

$sidecarProcess = $null
if ($shouldStartSidecar) {
  $sidecarScript = Join-Path $Root "ocr-service\run-dev.cmd"
  if (Test-Path -LiteralPath $sidecarScript) {
    Write-Host "Starting field OCR sidecar on port $FieldOcrPort..."
    $sidecarProcess = Start-Process -FilePath $sidecarScript `
      -WorkingDirectory (Join-Path $Root "ocr-service") `
      -WindowStyle Hidden `
      -RedirectStandardOutput $sidecarOut `
      -RedirectStandardError $sidecarErr `
      -PassThru
  }
}

Write-Host "Starting backend on port $BackendPort..."
$javaExe = Resolve-JavaExecutable
Write-Host "Using Java: $javaExe"
$backendProcess = Start-Process -FilePath $javaExe `
  -ArgumentList @("-jar", "target\baidu-full-page-ocr-backend-0.1.0.jar") `
  -WorkingDirectory (Join-Path $Root "backend") `
  -WindowStyle Hidden `
  -RedirectStandardOutput $backendOut `
  -RedirectStandardError $backendErr `
  -PassThru

Write-Host "Starting frontend on port $FrontendPort..."
$vite = Join-Path $Root "frontend\node_modules\vite\bin\vite.js"
if (-not (Test-Path -LiteralPath $vite)) {
  throw "Vite executable not found. Run npm install in frontend first."
}
$frontendProcess = Start-Process -FilePath "node.exe" `
  -ArgumentList @($vite, "--host", "127.0.0.1", "--port", [string]$FrontendPort, "--strictPort") `
  -WorkingDirectory (Join-Path $Root "frontend") `
  -WindowStyle Hidden `
  -RedirectStandardOutput $frontendOut `
  -RedirectStandardError $frontendErr `
  -PassThru

$backendReady = Wait-Http "Backend" "http://127.0.0.1:$BackendPort/api/llm/models" 60
$frontendReady = Wait-Http "Frontend" "http://127.0.0.1:$FrontendPort/" 30
$sidecarReady = $true
if ($shouldStartSidecar -and $sidecarProcess) {
  $sidecarReady = Wait-Http "Field OCR sidecar" "http://127.0.0.1:$FieldOcrPort/health" 20
}

$fieldOcrUrl = $null
$fieldOcrOutLog = $null
$fieldOcrErrLog = $null
if ($shouldStartSidecar) {
  $fieldOcrUrl = "http://127.0.0.1:$FieldOcrPort"
}
if ($sidecarProcess) {
  $fieldOcrOutLog = $sidecarOut
  $fieldOcrErrLog = $sidecarErr
}

$backendPid = Get-PortOwnerProcessId $BackendPort
if (-not $backendPid) {
  $backendPid = $backendProcess.Id
}

$state = [ordered]@{
  startedAt = (Get-Date).ToString("o")
  frontendUrl = "http://127.0.0.1:$FrontendPort/"
  backendUrl = "http://127.0.0.1:$BackendPort"
  fieldOcrUrl = $fieldOcrUrl
  frontendPid = $frontendProcess.Id
  backendPid = $backendPid
  fieldOcrPid = $(if ($sidecarProcess) { $sidecarProcess.Id } else { $null })
  logs = [ordered]@{
    backendOut = $backendOut
    backendErr = $backendErr
    frontendOut = $frontendOut
    frontendErr = $frontendErr
    fieldOcrOut = $fieldOcrOutLog
    fieldOcrErr = $fieldOcrErrLog
  }
}
$state | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $StatePath -Encoding UTF8

if (-not ($backendReady -and $frontendReady)) {
  Write-Host "Backend log: $backendOut"
  Write-Host "Frontend log: $frontendOut"
  throw "Local services did not start cleanly."
}

Write-Host ""
Write-Host "Local OCR demo is ready."
Write-Host "Frontend: http://127.0.0.1:$FrontendPort/"
Write-Host "Backend : http://127.0.0.1:$BackendPort"
if ($shouldStartSidecar) {
  Write-Host "Field OCR sidecar: http://127.0.0.1:$FieldOcrPort (ready=$sidecarReady)"
} else {
  Write-Host "Field OCR sidecar: disabled by default (use -WithSidecar to start it)"
}
Write-Host "State: $StatePath"
