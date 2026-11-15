param(
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbName = "inventory_management_system",
    [string]$DbUser = "ims_migrator",
    [string]$OutputDir = ".\backups"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$Timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd-HHmmss")
$BackupFile = Join-Path $OutputDir "$DbName-$Timestamp.sql"
$ChecksumFile = "$BackupFile.sha256"

Write-Host "Creating consistent transactional MySQL backup to $BackupFile ..."
mysqldump --host=$DbHost --port=$DbPort --user=$DbUser --password `
    --single-transaction --routines --triggers --events --set-gtid-purged=OFF `
    $DbName > $BackupFile

$Hash = (Get-FileHash -Path $BackupFile -Algorithm SHA256).Hash
Set-Content -Path $ChecksumFile -Value "$Hash  $(Split-Path $BackupFile -Leaf)"
Write-Host "Backup complete. SHA-256: $Hash"
