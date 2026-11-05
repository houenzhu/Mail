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
# RocketMQ client 内置 rmq.client.logback.xml 默认写 ${user.home}/logs/rocketmqlogs，
# 显式指定日志根目录，避免客户端日志继续占 C 盘（服务端配置见 E:/rocketmq/conf）。
$jvmArgs += "-Drocketmq.log.root=E:/rocketmq/logs/rocketmqlogs"
# 显式使用 JAVA_HOME 下的 java.exe；否则 Start-Process 'java' 会取 PATH，可能跑到别的 JDK。
$javaHome = $env:JAVA_HOME
if (-not $javaHome -and $env:JAVA_HOME_21) { $javaHome = $env:JAVA_HOME_21 }
if ($javaHome) {
    if ($javaHome -match 'jdk-?21') {
        Write-Host ("using JDK 21 runtime: {0}" -f $javaHome)
    }
    $javaExe = Join-Path $javaHome 'bin\java.exe'
} else {
    $javaExe = (Get-Command java -ErrorAction Stop).Source
    if ($javaExe -match 'jdk-?21') {
        Write-Host ("using JDK 21 runtime: {0}" -f $javaExe)
    }
}

# 敏感配置统一放在本机「用户环境变量」里；从当前终端 New-Item/Start-Process 起的进程
# 不一定继承到，缺什么补什么（已存在则以当前进程为准）。
$userEnvKeys = @('MYSQL_PASSWORD', 'INTERNAL_TOKEN', 'REDIS_PASSWORD', 'PAY_CHANNEL_SECRET')
foreach ($key in $userEnvKeys) {
    if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) {
        $value = [Environment]::GetEnvironmentVariable($key, 'User')
        if ($value) { Set-Item -Path "Env:$key" -Value $value }
    }
}

# OSS 上传（mall-admin）：沿用用户环境变量；未在 GUI 新开的进程里显式补上，
# 否则从当前终端启动时 Start-Process 继承不到"用户变量"。
if (-not $env:AccessKeyID) {
    $env:AccessKeyID = [Environment]::GetEnvironmentVariable('AccessKeyID', 'User')
}
if (-not $env:AccessKeySecret) {
    $env:AccessKeySecret = [Environment]::GetEnvironmentVariable('AccessKeySecret', 'User')
}
if (-not $env:OSS_BUCKET) { $env:OSS_BUCKET = 'zheliving-10000' }
if (-not $env:OSS_ENDPOINT) { $env:OSS_ENDPOINT = 'oss-cn-guangzhou.aliyuncs.com' }
# 本机 Windows 上 java.base 的 PipeImpl 会优先尝试 AF_UNIX 并报
# "Unable to establish loopback connection / Invalid argument: connect"。
# 如果存在临时补丁（强制走 TCP loopback），则自动挂载；补丁不存在不影响正常环境。
if (Test-Path (Join-Path $patchDir 'sun\nio\ch\PipeImpl.class')) {
    $jvmArgs += "--patch-module=java.base=$patchDir"
    Write-Host ("using java.base patch: {0}" -f $patchDir)
} elseif ($javaExe -notmatch 'jdk-?21') {
    Write-Warning 'JDK 21 is recommended. No java.base patch found; JDK 25 Tomcat/Netty may not bind on this host (doc 12.8)'
}

# 注意：变量名不要叫 $Services——PowerShell 变量名不区分大小写，会覆盖同名参数（曾导致"选了服务却一个都不启动"）
$servicePorts = [ordered]@{
    'mall-auth'      = 8101
    'mall-user'      = 8102
    'mall-product'   = 8103
    'mall-inventory' = 8104
    'mall-cart'      = 8109
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
    $proc = Start-Process -FilePath $javaExe -ArgumentList $arguments -WorkingDirectory $root -RedirectStandardOutput (Join-Path $logDir "$name-run.log") -RedirectStandardError (Join-Path $logDir "$name-run.err.log") -WindowStyle Hidden -PassThru
    Write-Host ("{0,-16} pid={1,-8} port={2}" -f $name, $proc.Id, $servicePorts[$name])
}

Write-Host ''
Write-Host 'logs: logs\*-run.log   gateway: http://127.0.0.1:10000'
