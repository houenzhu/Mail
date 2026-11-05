<#
.SYNOPSIS
    商品销量口径 E2E（G-12 累计销量回写 + G-13 30 日销量）。

.DESCRIPTION
    前置条件：mall-auth / mall-user / mall-product / mall-order / mall-pay /
    mall-admin / mall-gateway 已启动，MySQL 已执行 sql/14_product_sales.sql。

    脚本会：
      1. 买家真实下单并支付（走 MOCK 渠道回调），断言商品累计销量 +N；
      2. 重复支付回调，断言销量不再增长（Outbox + 认领表双层幂等）；
      3. 后台 30 日销量接口与直接 SQL 聚合逐项对齐；
      4. 鉴权边界：匿名 401 / 买家 403。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\e2e-sales.ps1
#>
param(
    [string]$Gateway = 'http://127.0.0.1:10000',
    [string]$MysqlHome = 'D:\mysql\mysql-8.0.32-winx64\mysql-8.0.32-winx64',
    [string]$MysqlHost = '127.0.0.1',
    [int]$MysqlPort = 3306,
    [string]$MysqlUser = 'root',
    [string]$MysqlPassword = $env:MYSQL_PASSWORD,
    [string]$PaySecret = $env:PAY_CHANNEL_SECRET,
    [int]$SkuId = 1001,
    [int]$Qty = 2,
    [string]$RunTag = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
if (-not $RunTag) { $RunTag = (Get-Date -Format 'HHmmss') }
if (-not $PaySecret) { throw 'PAY_CHANNEL_SECRET is required.' }
if (-not $MysqlPassword) { throw 'MYSQL_PASSWORD is required (used to verify SQL aggregation).' }

$mysql = Join-Path $MysqlHome 'bin\mysql.exe'
if (-not (Test-Path $mysql)) { throw "mysql client not found: $mysql" }

$script:Results = @()

function Add-Result([string]$Id, [string]$Case, [string]$Expect, [string]$Actual, [bool]$Pass) {
    $script:Results += [pscustomobject]@{ Id = $Id; Case = $Case; Expect = $Expect; Actual = $Actual; Pass = $Pass }
    $mark = if ($Pass) { 'PASS' } else { 'FAIL' }
    Write-Host ("[{0}] #{1} {2} -> {3}" -f $mark, $Id, $Case, $Actual)
}

function Invoke-Api {
    param(
        [string]$Method = 'Get',
        [string]$Uri,
        [string]$Token,
        $Body
    )
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(20)
    $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]$Method, $Uri)
    if ($Token) { $request.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token) }
    $content = $null
    try {
        if ($null -ne $Body) {
            $json = ConvertTo-Json -InputObject $Body -Compress -Depth 8
            $content = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, 'application/json')
            $request.Content = $content
        }
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

function Get-Token([string]$Username, [string]$Password) {
    $body = @{ username = $Username; password = $Password }
    $r = Invoke-Api -Method Post -Uri "$Gateway/api/auth/login" -Body $body
    if ($r.code -ne 0) { throw "登录失败: $Username -> $($r.message)" }
    return $r.data
}

function Get-Sql([string]$Database, [string]$Query) {
    # 变量名不要叫 $args：它是 PowerShell 自动变量
    $env:MYSQL_PWD = $MysqlPassword
    $mysqlArgs = @('-N', '-B', '-h', $MysqlHost, '-P', "$MysqlPort", '-u', $MysqlUser,
        '-e', "use $Database; $Query")
    $out = & $mysql @mysqlArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "SQL 失败: $Query" }
    return ($out | Out-String).Trim()
}

function Get-ProductSales([long]$ProductId) {
    $value = Get-Sql 'mall_product' "select sales from product where id = $ProductId;"
    return [int]$value
}

function Get-Sign([string]$Content, [string]$Secret) {
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($Secret)
    $hash = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Content))
    return (($hash | ForEach-Object { $_.ToString('X2') }) -join '')
}

Write-Host "=== 商品销量 E2E (tag=$RunTag) ==="

$buyer = Get-Token 'buyer01' 'Buyer@123456'
$admin = Get-Token 'admin' 'Admin@123456'

$orderBody = @{
    requestId = "e2e-sales-$RunTag"
    receiverName = 'E2E'
    receiverPhone = '13800000011'
    receiverAddress = '上海市浦东新区世纪大道 1 号'
    items = @(@{ skuId = $SkuId; qty = $Qty })
}
$order = Invoke-Api -Method Post -Uri "$Gateway/api/order" -Token $buyer.accessToken -Body $orderBody
if ($order.code -ne 0) { throw "下单失败: $($order.message)" }
$orderNo = $order.data.orderNo

$payBody = @{ orderNo = $orderNo; payType = 1; requestId = "pay-$RunTag" }
$pay = Invoke-Api -Method Post -Uri "$Gateway/api/pay" -Token $buyer.accessToken -Body $payBody
if ($pay.code -ne 0) { throw "发起支付失败: $($pay.message)" }
$paymentNo = $pay.data.paymentNo
$amount = ('{0:F2}' -f [decimal]$pay.data.amount)
$tradeNo = "MOCK_SALES_$paymentNo"

$productId = [long](Get-Sql 'mall_order' "select product_id from order_item where order_no = '$orderNo' limit 1;")
$before = Get-ProductSales $productId

$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$content = "amount=$amount&channelTradeNo=$tradeNo&paymentNo=$paymentNo&status=SUCCESS&timestamp=$timestamp"
$sign = Get-Sign $content $PaySecret
$callbackBody = @{
    paymentNo = $paymentNo; channelTradeNo = $tradeNo; amount = [decimal]$amount
    status = 'SUCCESS'; payType = 1; timestamp = $timestamp; sign = $sign
}
$callback = Invoke-Api -Method Post -Uri "$Gateway/api/pay/callback/MOCK" -Body $callbackBody
Add-Result '1' '支付回调' 'code=0' ("code={0}, paymentNo={1}" -f $callback.code, $paymentNo) ($callback.code -eq 0)

# 销量回写是异步消费；Broker/消费者重平衡时可能超过 20 秒，这里最多等 60 秒
$expected = $before + $Qty
$after = $before
$deadline = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $deadline) {
    $after = Get-ProductSales $productId
    if ($after -eq $expected) { break }
    Start-Sleep -Milliseconds 500
}
Add-Result '2' '累计销量回写' "sales=$expected" ("before={0}, after={1}, productId={2}" -f $before, $after, $productId) ($after -eq $expected)

$recordCount = Get-Sql 'mall_product' "select count(*) from product_sales_record where order_no = '$orderNo';"
Add-Result '3' '认领记录唯一' '1 条' ("records={0}" -f $recordCount) ($recordCount -eq '1')

# 重复回调：支付单已成功，只重放通知；销量必须不变
$timestamp2 = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$content2 = "amount=$amount&channelTradeNo=$tradeNo&paymentNo=$paymentNo&status=SUCCESS&timestamp=$timestamp2"
$sign2 = Get-Sign $content2 $PaySecret
$repeatBody = @{
    paymentNo = $paymentNo; channelTradeNo = $tradeNo; amount = [decimal]$amount
    status = 'SUCCESS'; payType = 1; timestamp = $timestamp2; sign = $sign2
}
$repeat = Invoke-Api -Method Post -Uri "$Gateway/api/pay/callback/MOCK" -Body $repeatBody
Start-Sleep -Seconds 3
$afterRepeat = Get-ProductSales $productId
Add-Result '4' '重复回调不重复加销量' "sales=$expected" ("callbackCode={0}, sales={1}" -f $repeat.code, $afterRepeat) ($afterRepeat -eq $expected)

# 30 日销量：接口 vs SQL
$sql30d = Get-Sql 'mall_order' "select coalesce(sum(i.qty),0) from order_item i join orders o on o.order_no=i.order_no where o.deleted=0 and o.status in (20,30,40,60) and o.paid_at is not null and o.paid_at >= date_sub(curdate(), interval 29 day) and o.paid_at < date_add(curdate(), interval 1 day) and i.product_id=$productId;"
$api30d = Invoke-Api -Method Post -Uri "$Gateway/api/order/admin/product-sales?days=30" -Token $admin.accessToken -Body (,$productId)
$apiValue = 0
if ($api30d.code -eq 0 -and $api30d.data) { $apiValue = [int]$api30d.data[0].quantity }
Add-Result '5' '30 日销量接口' "sql=$sql30d" ("api={0}, sql={1}" -f $apiValue, $sql30d) ($api30d.code -eq 0 -and $apiValue -eq [int]$sql30d)

# 后台商品列表也要带上 30 日销量
$page = Invoke-Api -Method Post -Uri "$Gateway/api/admin/product/page" -Token $admin.accessToken -Body @{ pageNo = 1; pageSize = 50 }
$row = $null
if ($page.code -eq 0) { $row = $page.data.records | Where-Object { $_.id -eq $productId } | Select-Object -First 1 }
$sales30d = if ($row) { $row.sales30d } else { 'row-missing' }
Add-Result '6' '商品列表 30 日销量' "sales30d=$sql30d" ("code={0}, sales30d={1}" -f $page.code, $sales30d) `
    ($page.code -eq 0 -and $row -and $null -ne $row.sales30d -and [int]$row.sales30d -eq [int]$sql30d)

$anon = Invoke-Api -Method Post -Uri "$Gateway/api/order/admin/product-sales?days=30" -Body (,$productId)
Add-Result '7' '匿名查询 30 日销量' 'HTTP 401 / 20001' ("http={0}, code={1}" -f $anon.httpStatus, $anon.code) `
    ($anon.httpStatus -eq 401 -and $anon.code -eq 20001)

$buyerRes = Invoke-Api -Method Post -Uri "$Gateway/api/order/admin/product-sales?days=30" -Token $buyer.accessToken -Body (,$productId)
Add-Result '8' '买家查询 30 日销量' 'HTTP 403 / 20003' ("http={0}, code={1}" -f $buyerRes.httpStatus, $buyerRes.code) `
    ($buyerRes.httpStatus -eq 403 -and $buyerRes.code -eq 20003)

Write-Host ''
$script:Results | Format-Table -AutoSize | Out-String -Width 240 | Write-Host
$failed = @($script:Results | Where-Object { -not $_.Pass })
Write-Host ("通过 {0}/{1}" -f ($script:Results.Count - $failed.Count), $script:Results.Count)
if ($failed.Count -gt 0) { exit 1 }
