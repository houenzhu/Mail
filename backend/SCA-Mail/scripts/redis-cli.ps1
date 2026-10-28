<#
.SYNOPSIS
    极简 Redis 客户端（RESP 协议直连），用于本机没有 redis-cli 时排查缓存与令牌 key。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\redis-cli.ps1 "KEYS mall:*"
    powershell -ExecutionPolicy Bypass -File .\scripts\redis-cli.ps1 "TTL mall:product:detail:1" -Database 1
    powershell -ExecutionPolicy Bypass -File .\scripts\redis-cli.ps1 "GET mall:auth:refresh:xxx"
#>
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Command,

    [string]$RedisHost = $env:REDIS_HOST,
    [int]$Port = 0,
    [string]$Password = $env:REDIS_PASSWORD,
    [int]$Database = 0
)

if (-not $RedisHost) { $RedisHost = '192.168.198.133' }
if ($Port -le 0) { $Port = 6379 }
if (-not $Password) { $Password = '123456' }
$Database = if ($env:REDIS_DATABASE) { [int]$env:REDIS_DATABASE } else { $Database }

$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($RedisHost, $Port)
$stream = $client.GetStream()
$stream.ReadTimeout = 4000

function Send-Command([object[]]$parts) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("*$($parts.Count)`r`n")
    foreach ($p in $parts) {
        $text = [string]$p
        [void]$sb.Append("`$$([System.Text.Encoding]::UTF8.GetByteCount($text))`r`n$text`r`n")
    }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($sb.ToString())
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Flush()
}

function Read-Byte {
    return $stream.ReadByte()
}

function Read-Line {
    $bytes = New-Object System.Collections.Generic.List[byte]
    while ($true) {
        $b = Read-Byte
        if ($b -lt 0) { break }
        if ($b -eq 13) { [void](Read-Byte); break }
        $bytes.Add([byte]$b)
    }
    return [System.Text.Encoding]::UTF8.GetString($bytes.ToArray())
}

function Read-Reply {
    $prefix = [char](Read-Byte)
    switch ($prefix) {
        '+' { return Read-Line }
        '-' { return 'ERR ' + (Read-Line) }
        ':' { return Read-Line }
        '$' {
            $len = [int](Read-Line)
            if ($len -lt 0) { return '(nil)' }
            $buffer = New-Object byte[] $len
            $read = 0
            while ($read -lt $len) { $read += $stream.Read($buffer, $read, $len - $read) }
            [void](Read-Byte); [void](Read-Byte)
            return [System.Text.Encoding]::UTF8.GetString($buffer)
        }
        '*' {
            $count = [int](Read-Line)
            if ($count -lt 0) { return '(nil)' }
            $items = @()
            for ($i = 0; $i -lt $count; $i++) { $items += (Read-Reply) }
            return $items
        }
        default { return "未知响应: $prefix" }
    }
}

try {
    if ($Password) { Send-Command @('AUTH', $Password) | Out-Null; [void](Read-Reply) }
    if ($Database -gt 0) { Send-Command @('SELECT', $Database) | Out-Null; [void](Read-Reply) }
    Send-Command ($Command -split '\s+')
    $reply = Read-Reply
    if ($reply -is [array]) { $reply | ForEach-Object { $_ } } else { $reply }
} finally {
    $client.Close()
}