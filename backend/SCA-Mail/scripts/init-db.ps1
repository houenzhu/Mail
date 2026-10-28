param(
    [string]$MysqlHome = 'D:\mysql\mysql-8.0.32-winx64\mysql-8.0.32-winx64',
    [string]$DbHost = '127.0.0.1',
    [int]$Port = 3306,
    [string]$User = 'root',
    [string]$Password = $env:MYSQL_PASSWORD
)

$mysql = Join-Path $MysqlHome 'bin\mysql.exe'
if (-not (Test-Path $mysql)) {
    throw "mysql client not found: $mysql (use -MysqlHome to override)"
}
if (-not $Password) {
    throw 'password required: set env MYSQL_PASSWORD or pass -Password'
}

$root = Split-Path -Parent $PSScriptRoot
$files = Get-ChildItem (Join-Path $root 'sql') -Filter '*.sql' | Sort-Object Name

foreach ($file in $files) {
    Write-Host ("apply {0}" -f $file.Name)
    $path = $file.FullName.Replace('\', '/')
    & $mysql -h $DbHost -P $Port -u $User "--password=$Password" --default-character-set=utf8mb4 -e "source $path"
    if ($LASTEXITCODE -ne 0) {
        throw ("apply failed: {0}" -f $file.Name)
    }
}

Write-Host 'done'
