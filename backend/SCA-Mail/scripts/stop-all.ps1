$root = Split-Path -Parent $PSScriptRoot

$targets = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.CommandLine -and $_.CommandLine -like "*$root\mall-*" }

if (-not $targets) {
    Write-Host 'no project service process found'
    return
}

foreach ($target in $targets) {
    Write-Host ("stop pid={0}" -f $target.ProcessId)
    Stop-Process -Id $target.ProcessId -Force
}
