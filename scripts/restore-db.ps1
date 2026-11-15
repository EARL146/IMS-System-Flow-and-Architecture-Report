param(
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbName = "inventory_management_system",
    [string]$DbUser = "ims_migrator"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $BackupFile)) {
    throw "Backup file not found: $BackupFile"
}

$ChecksumFile = "$BackupFile.sha256"
if (Test-Path $ChecksumFile) {
    $ExpectedHash = ((Get-Content $ChecksumFile).Split(" ")[0]).Trim()
    $ActualHash = (Get-FileHash -Path $BackupFile -Algorithm SHA256).Hash
    if ($ExpectedHash -ne $ActualHash) {
        throw "SHA-256 checksum mismatch! Expected $ExpectedHash but got $ActualHash"
    }
    Write-Host "Verified backup SHA-256 checksum: $ActualHash"
}

Write-Host "Restoring $BackupFile into $DbName on ${DbHost}:${DbPort} ..."
Get-Content -Raw $BackupFile | mysql --host=$DbHost --port=$DbPort --user=$DbUser --password $DbName
Write-Host "Database restore completed successfully."
