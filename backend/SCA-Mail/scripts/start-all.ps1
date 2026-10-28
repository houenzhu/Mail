param(
    # 只启动部分服务，例如：-Services mall-auth,mall-user
    # 兼容用 -File 调用时整串传入的情况（会按逗号再切分）
    [string[]]$Services
)

$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$patchDir = if ($env:PIPATCH_DIR) { $env:PIPATCH_DIR } else { Join-Path $env:TEMP 'pipatch\out' }
$jvmArgs = @()
if ($env:JAVA_HOME -and $env:JAVA_HOME -match 'jdk-?21') {
    Write-Host ("using JDK 21 runtime: {0}" -f $env:JAVA_HOME)
} elseif (Test-Path (Join-Path $patchDir 'sun\nio\ch\PipeImpl.class')) {
    $jvmArgs += "--patch-module=java.base=$patchDir"
    Write-Host ("using java.base patch: {0}" -f $patchDir)
} else {
    Write-Warning 'JDK 21 is recommended. No java.base patch found; JDK 25 Tomcat/Netty may not bind on this host (doc 12.8)'
}

# 注意：变量名不要叫 $Services——PowerShell 变量名不区分大小写，会覆盖同名参数（曾导致"选了服务却一个都不启动"）
$servicePorts = [ordered]@{
    'mall-auth'      = 8101
    'mall-user'      = 8102
    'mall-product'   = 8103
    'mall-inventory' = 8104
    'mall-order'     = 8105
    'mall-pay'       = 8106
    'mall-promotion' = 8107
    # 后台 BFF（8111）：聚合各服务的后台数据能力，对外统一 /api/admin/**（见计划文档 ADR-N1）
    'mall-admin'     = 8111
    'mall-gateway'   = 10000
}

$wanted = @()
if ($Services) {
    foreach ($item in $Services) {
        foreach ($part in ($item -split ',')) {
            $trimmed = $part.Trim()
            if ($trimmed) { $wanted += $trimmed }
        }
    }
}

foreach ($name in $servicePorts.Keys) {
    if ($wanted.Count -gt 0 -and ($wanted -notcontains $name)) { continue }
    $jar = Join-Path $root "$name\target\$name-1.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) {
        Write-Warning ("{0} not built yet, skipped (run: mvn install -DskipTests)" -f $name)
        continue
    }
    $arguments = $jvmArgs + @('-jar', $jar)
    $proc = Start-Process -FilePath 'java' -ArgumentList $arguments -WorkingDirectory $root -RedirectStandardOutput (Join-Path $logDir "$name-run.log") -RedirectStandardError (Join-Path $logDir "$name-run.err.log") -WindowStyle Hidden -PassThru
    Write-Host ("{0,-16} pid={1,-8} port={2}" -f $name, $proc.Id, $servicePorts[$name])
}

Write-Host ''
Write-Host 'logs: logs\*-run.log   gateway: http://127.0.0.1:10000'
