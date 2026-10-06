$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskDirectory = Join-Path $taskRoot 'app/src/main/assets/models'
$taskManifest = Get-Content (Join-Path $taskDirectory 'question-author-v2.json') -Raw | ConvertFrom-Json
$taskModel = Join-Path $taskDirectory $taskManifest.file
if ((Test-Path -LiteralPath $taskModel) -and (Get-FileHash -LiteralPath $taskModel -Algorithm SHA256).Hash.ToLower() -eq $taskManifest.sha256) {
    Write-Output 'Verified Android question model already present.'
    exit 0
}
$taskUrl = "https://github.com/EndDefault/brain-walk/releases/download/android-author-v0.4.0/$($taskManifest.file)"
$taskPending = "$taskModel.download"
Invoke-WebRequest -Uri $taskUrl -OutFile $taskPending
if ((Get-FileHash -LiteralPath $taskPending -Algorithm SHA256).Hash.ToLower() -ne $taskManifest.sha256) { throw 'Downloaded model checksum mismatch' }
Move-Item -LiteralPath $taskPending -Destination $taskModel -Force
Write-Output "Verified Android model: $($taskManifest.sha256)"
