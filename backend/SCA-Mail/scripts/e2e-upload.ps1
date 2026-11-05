<#
.SYNOPSIS
    mall-admin 商品图上传 E2E（/api/admin/upload）。

.DESCRIPTION
    前置条件：mall-admin、mall-auth、mall-gateway 已启动，且 mall-admin 进程能读到
    AccessKeyID / AccessKeySecret 环境变量。

    脚本会验证：
      1. 匿名 401、买家 403（角色边界）；
      2. 非图片伪装文件 38003（服务端按文件头识别）；
      3. 管理员上传 1x1 PNG，返回 objectKey 与可访问 URL；
      4. 返回的 URL 能取到字节。
    默认上传到 OSS；若只想验证权限与校验，可用 -SkipStorage。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\e2e-upload.ps1
#>
param(
    [string]$Gateway = 'http://127.0.0.1:10000',
    [string]$AdminUsername = 'admin',
    [string]$AdminPassword = 'Admin@123456',
    [string]$BuyerUsername = 'buyer01',
    [string]$BuyerPassword = 'Buyer@123456',
    [switch]$SkipStorage,
    [switch]$KeepObject
)

$ErrorActionPreference = 'Stop'
$script:Results = @()

function Add-Result([string]$Id, [string]$Case, [string]$Expect, [string]$Actual, [bool]$Pass, [string]$Detail = '') {
    $script:Results += [pscustomobject]@{ Id = $Id; Case = $Case; Expect = $Expect; Actual = $Actual; Pass = $Pass; Detail = $Detail }
    $mark = if ($Pass) { 'PASS' } else { 'FAIL' }
    Write-Host ("[{0}] #{1} {2} -> {3}" -f $mark, $Id, $Case, $Actual)
}

function Invoke-Api {
    param(
        [string]$Method = 'Get',
        [string]$Uri,
        [string]$Token,
        [object]$Body,
        [hashtable]$Form
    )
    # 不用 Invoke-RestMethod -Form：Windows PowerShell 5.1 没有该参数，
    # 本脚本会被 powershell.exe 调用，必须兼容 5.1。
    Add-Type -AssemblyName System.Net.Http
    $handler = New-Object System.Net.Http.HttpClientHandler
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(60)
    if ($Token) {
        $client.DefaultRequestHeaders.Authorization =
            New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token)
    }

    $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]$Method, $Uri)
    $content = $null
    if ($null -ne $Form) {
        $multipart = New-Object System.Net.Http.MultipartFormDataContent
        foreach ($name in $Form.Keys) {
            $value = $Form[$name]
            if ($value -is [System.IO.FileInfo]) {
                $bytes = [System.IO.File]::ReadAllBytes($value.FullName)
                $fileContent = New-Object System.Net.Http.ByteArrayContent(, $bytes)
                $fileContent.Headers.ContentType =
                    [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('application/octet-stream')
                $multipart.Add($fileContent, $name, $value.Name)
            } else {
                $multipart.Add((New-Object System.Net.Http.StringContent([string]$value)), $name)
            }
        }
        $content = $multipart
    } elseif ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Compress -Depth 8
        $stringContent = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, 'application/json')
        $content = $stringContent
    }
    if ($null -ne $content) { $request.Content = $content }

    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $status = [int]$response.StatusCode
        $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $payload = $null
        try { $payload = $text | ConvertFrom-Json } catch { }
        if (-not $payload) { $payload = [pscustomobject]@{ code = -1; message = $text; data = $null } }
        $payload | Add-Member -NotePropertyName httpStatus -NotePropertyValue $status -Force
        return $payload
    } catch {
        return [pscustomobject]@{ code = -1; message = $_.Exception.Message; data = $null; httpStatus = 0 }
    } finally {
        if ($null -ne $content) { $content.Dispose() }
        $request.Dispose()
        $client.Dispose()
    }
}

function New-TestPng([string]$Path) {
    # 1x1 透明 PNG
    $base64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='
    [System.IO.File]::WriteAllBytes($Path, [Convert]::FromBase64String($base64))
}

$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ('mall-upload-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$png = Join-Path $tmp 'test.png'
$fake = Join-Path $tmp 'fake.png'
New-TestPng $png
[System.IO.File]::WriteAllText($fake, 'not-an-image')

try {
    Write-Host '=== mall-admin upload E2E ==='
    $admin = Invoke-Api -Method Post -Uri "$Gateway/api/auth/login" -Body @{ username = $AdminUsername; password = $AdminPassword }
    if ($admin.code -ne 0) { throw "管理员登录失败: $($admin.message)" }
    $buyer = Invoke-Api -Method Post -Uri "$Gateway/api/auth/login" -Body @{ username = $BuyerUsername; password = $BuyerPassword }
    if ($buyer.code -ne 0) { throw "买家登录失败: $($buyer.message)" }

    # #1 匿名
    $anon = Invoke-Api -Method Post -Uri "$Gateway/api/admin/upload" -Form @{ file = Get-Item $png }
    Add-Result '1' '匿名上传' 'HTTP 401 / 20001' ("http={0}, code={1}" -f $anon.httpStatus, $anon.code) `
        ($anon.httpStatus -eq 401 -and $anon.code -eq 20001)

    # #2 买家（staff 校验）
    $buyerRes = Invoke-Api -Method Post -Uri "$Gateway/api/admin/upload" -Token $buyer.data.accessToken -Form @{ file = Get-Item $png }
    Add-Result '2' '买家上传' 'HTTP 403 / 20003' ("http={0}, code={1}" -f $buyerRes.httpStatus, $buyerRes.code) `
        ($buyerRes.httpStatus -eq 403 -and $buyerRes.code -eq 20003)

    # #3 伪装图片（只测校验，不触碰存储）
    $fakeRes = Invoke-Api -Method Post -Uri "$Gateway/api/admin/upload" -Token $admin.data.accessToken -Form @{ file = Get-Item $fake }
    Add-Result '3' '伪装成 PNG 的文本' '38003' ("code={0}, message={1}" -f $fakeRes.code, $fakeRes.message) `
        ($fakeRes.code -eq 38003)

    # #4 真实上传
    if ($SkipStorage) {
        Write-Host '[SKIP] 已指定 -SkipStorage，跳过真实 OSS 写入'
    } else {
        $upload = Invoke-Api -Method Post -Uri "$Gateway/api/admin/upload" -Token $admin.data.accessToken -Form @{ file = Get-Item $png }
        $uriOk = $upload.code -eq 0 -and $upload.data.objectKey -and $upload.data.url
        Add-Result '4' '管理员上传 PNG' 'code=0 且返回 objectKey/url' `
            ("code={0}, objectKey={1}, url={2}" -f $upload.code, $upload.data.objectKey, $upload.data.url) $uriOk `
            ($upload.message)

        if ($uriOk) {
            # 默认公共读返回稳定 URL；私有桶返回带签名 URL，两者都必须能直接取到图片
            try {
                $remote = Invoke-WebRequest -Uri $upload.data.url -Method Get -TimeoutSec 30 -UseBasicParsing
                Add-Result '5' '回读上传对象' 'HTTP 200' ("http={0}, bytes={1}" -f $remote.StatusCode, $remote.RawContentLength) `
                    ($remote.StatusCode -eq 200)
            } catch {
                Add-Result '5' '回读上传对象' 'HTTP 200' ("error={0}" -f $_.Exception.Message) $false
            }
        }

        if (-not $KeepObject -and $uriOk) {
            Write-Host ("[INFO] 测试对象: {0}" -f $upload.data.objectKey)
            Write-Host '[INFO] 未自动删除 OSS 对象（当前接口未提供删除）；需要时请在控制台清理 product/ 下测试文件。'
        }
    }

    Write-Host ''
    Write-Host '=== 结果汇总 ==='
    $script:Results | Format-Table -AutoSize | Out-String -Width 240 | Write-Host
    $failed = @($script:Results | Where-Object { -not $_.Pass })
    Write-Host ("通过 {0}/{1}" -f ($script:Results.Count - $failed.Count), $script:Results.Count)
    if ($failed.Count -gt 0) { exit 1 }
} finally {
    if (-not $KeepObject) { Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue }
}
