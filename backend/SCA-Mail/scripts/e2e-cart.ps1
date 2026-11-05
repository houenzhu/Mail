<#
.SYNOPSIS
    mall-cart 端到端验证（UC-CART-01 / 02 / 03）。

.DESCRIPTION
    前置条件：
      - Redis / MySQL / Nacos 可用；
      - mall-auth、mall-user、mall-product、mall-inventory、mall-cart、mall-gateway 已启动；
      - 默认测试账号 buyer01 / Buyer@123456。

    脚本使用测试账号自己的 Redis 购物车 key，开始与结束都会清理，不影响其他用户。
    失效商品用例通过直接向 Redis 写入一个不存在的 SKU 构造，不改数据库。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\e2e-cart.ps1
#>
param(
    [string]$Gateway = 'http://127.0.0.1:10000',
    [string]$CartBase = 'http://127.0.0.1:8109',
    [string]$Username = 'buyer01',
    [string]$Password = 'Buyer@123456',
    [string]$InternalToken = $env:INTERNAL_TOKEN,
    [string]$RedisHost = '192.168.198.133',
    [int]$RedisPort = 6379,
    [string]$RedisPassword = $env:REDIS_PASSWORD,
    [int]$RedisDatabase = 1,
    [switch]$KeepData
)

$ErrorActionPreference = 'Stop'
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
        $Body,
        [hashtable]$Headers
    )
    $allHeaders = @{}
    if ($Token) { $allHeaders['Authorization'] = "Bearer $Token" }
    if ($Headers) { $Headers.GetEnumerator() | ForEach-Object { $allHeaders[$_.Key] = $_.Value } }
    $params = @{ Method = $Method; Uri = $Uri; Headers = $allHeaders; TimeoutSec = 20 }
    if ($null -ne $Body) {
        $params['ContentType'] = 'application/json; charset=utf-8'
        $params['Body'] = ($Body | ConvertTo-Json -Compress -Depth 8)
    }
    try {
        return Invoke-RestMethod @params
    } catch {
        $status = 0
        $payload = $null
        $response = $_.Exception.Response
        if ($response) {
            $status = [int]$response.StatusCode
            try {
                $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
                $payload = $reader.ReadToEnd() | ConvertFrom-Json
            } catch { }
            if (-not $payload -and $_.ErrorDetails.Message) {
                try { $payload = $_.ErrorDetails.Message | ConvertFrom-Json } catch { }
            }
        }
        if (-not $payload) { $payload = [pscustomobject]@{ code = -1; message = $_.Exception.Message; data = $null } }
        $payload | Add-Member -NotePropertyName httpStatus -NotePropertyValue $status -Force
        return $payload
    }
}

function RedisCli([string]$Command) {
    $script = Join-Path $PSScriptRoot 'redis-cli.ps1'
    & powershell -NoProfile -ExecutionPolicy Bypass -File $script $Command -RedisHost $RedisHost -Port $RedisPort -Password $RedisPassword -Database $RedisDatabase
}

function Reset-Cart([long]$UserId) {
    RedisCli "DEL mall:cart:$UserId" | Out-Null
}

Write-Host '=== mall-cart E2E ==='

$login = Invoke-Api -Method Post -Uri "$Gateway/api/auth/login" -Body @{ username = $Username; password = $Password }
if ($login.code -ne 0) { throw "登录失败: $($login.message)" }
$buyer = $login.data
$token = $buyer.accessToken
$userId = [long]$buyer.userId

# ---------------------------------------------------------------- #1 鉴权
$anon = Invoke-Api -Method Get -Uri "$Gateway/api/cart"
Add-Result '1' '匿名访问购物车' 'HTTP 401 / 20001' `
    ("http={0}, code={1}" -f $anon.httpStatus, $anon.code) `
    ($anon.httpStatus -eq 401 -and $anon.code -eq 20001)

$forged = Invoke-Api -Method Get -Uri "$CartBase/api/cart" -Headers @{ 'X-User-Id' = [string]$userId; 'X-Internal-Token' = 'forged-token' }
Add-Result '2' '直连伪造身份头' '不采信身份，20001' `
    ("http={0}, code={1}" -f $forged.httpStatus, $forged.code) `
    ($forged.code -eq 20001)

# ---------------------------------------------------------------- #3 加购 / 累加
Reset-Cart $userId
$empty = Invoke-Api -Method Get -Uri "$Gateway/api/cart" -Token $token
$add1 = Invoke-Api -Method Post -Uri "$Gateway/api/cart" -Token $token -Body @{ skuId = 1001; quantity = 2 }
$add2 = Invoke-Api -Method Post -Uri "$Gateway/api/cart" -Token $token -Body @{ skuId = 1001; quantity = 2 }
Add-Result '3' '加购 + 同 SKU 累加' '行数 1，数量 4，金额 1996' `
    ("emptyRows={0}, rows={1}, qty={2}, amount={3}" -f $empty.data.items.Count, $add2.data.items.Count, $add2.data.items[0].quantity, $add2.data.selectedAmount) `
    ($empty.data.items.Count -eq 0 -and $add2.data.items.Count -eq 1 -and $add2.data.items[0].quantity -eq 4 -and $add2.data.selectedAmount -eq 1996)

# ---------------------------------------------------------------- #4 数量上限
$over = Invoke-Api -Method Post -Uri "$Gateway/api/cart" -Token $token -Body @{ skuId = 1001; quantity = 96 }
$still = Invoke-Api -Method Get -Uri "$Gateway/api/cart" -Token $token
Add-Result '4' '单品数量上限 99' '37007，原数量不变' `
    ("code={0}, qty={1}" -f $over.code, $still.data.items[0].quantity) `
    ($over.code -eq 37007 -and $still.data.items[0].quantity -eq 4)

# ---------------------------------------------------------------- #5 库存 / 售罄
$okStock = Invoke-Api -Method Post -Uri "$Gateway/api/cart" -Token $token -Body @{ skuId = 1005; quantity = 1 }
$overStock = Invoke-Api -Method Post -Uri "$Gateway/api/cart" -Token $token -Body @{ skuId = 1005; quantity = 5 }
$soldOut = Invoke-Api -Method Post -Uri "$Gateway/api/cart" -Token $token -Body @{ skuId = 1004; quantity = 1 }
Add-Result '5' '库存内可加购 / 超库存 / 售罄' '1005 成功，超量 37008，售罄 37005' `
    ("add1005={0}, over={1}, soldOut={2}" -f $okStock.code, $overStock.code, $soldOut.code) `
    ($okStock.code -eq 0 -and $overStock.code -eq 37008 -and $soldOut.code -eq 37005)

# ---------------------------------------------------------------- #6 改数量 / 勾选 / 金额
$update = Invoke-Api -Method Put -Uri "$Gateway/api/cart" -Token $token -Body @{ items = @(
    @{ skuId = 1001; quantity = 3 }, @{ skuId = 1005; quantity = 2 }) }
$deselect = Invoke-Api -Method Put -Uri "$Gateway/api/cart/selection" -Token $token -Body @{ skuIds = @(1001); selected = $false }
Add-Result '6' '批量改数量 + 取消勾选' 'total=5, selected=2, amount=598' `
    ("updateCode={0}, total={1}, selected={2}, amount={3}, allSelected={4}" -f $update.code, $deselect.data.cart.totalQuantity, $deselect.data.cart.selectedCount, $deselect.data.cart.selectedAmount, $deselect.data.cart.allSelected) `
    ($update.code -eq 0 -and $deselect.data.cart.totalQuantity -eq 5 -and $deselect.data.cart.selectedCount -eq 2 -and $deselect.data.cart.selectedAmount -eq 598 -and -not $deselect.data.cart.allSelected)

# ---------------------------------------------------------------- #7 结算
$checkout = Invoke-Api -Method Post -Uri "$Gateway/api/cart/checkout" -Token $token
Add-Result '7' '结算预览只看勾选有效行' '1 行，数量 2，金额 598' `
    ("code={0}, rows={1}, qty={2}, pay={3}" -f $checkout.code, $checkout.data.items.Count, $checkout.data.totalQuantity, $checkout.data.payAmount) `
    ($checkout.code -eq 0 -and $checkout.data.items.Count -eq 1 -and $checkout.data.items[0].skuId -eq 1005 -and $checkout.data.totalQuantity -eq 2 -and $checkout.data.payAmount -eq 598)

# ---------------------------------------------------------------- #8 全选
$selectAll = Invoke-Api -Method Put -Uri "$Gateway/api/cart/selection" -Token $token -Body @{ selected = $true }
Add-Result '8' '全选' 'changed=1，selected=5，allSelected=true' `
    ("code={0}, changed={1}, selected={2}, allSelected={3}" -f $selectAll.code, $selectAll.data.changed, $selectAll.data.cart.selectedCount, $selectAll.data.cart.allSelected) `
    ($selectAll.code -eq 0 -and $selectAll.data.changed -eq 1 -and $selectAll.data.cart.selectedCount -eq 5 -and $selectAll.data.cart.allSelected)

# ---------------------------------------------------------------- #9 失效商品
RedisCli "HSET mall:cart:$userId qty:999999 1 sel:999999 1 at:999999 1793845699999" | Out-Null
$invalid = Invoke-Api -Method Get -Uri "$Gateway/api/cart" -Token $token
$invalidRow = @($invalid.data.items | Where-Object { $_.skuId -eq 999999 })[0]
$invalidCheckout = Invoke-Api -Method Post -Uri "$Gateway/api/cart/checkout" -Token $token
Add-Result '9' '失效商品保留但不计金额' 'invalid=true，金额仍 2095，结算只含 2 个有效行' `
    ("invalid={0}, reason={1}, amount={2}, checkoutRows={3}" -f $invalidRow.invalid, $invalidRow.invalidReason, $invalid.data.selectedAmount, $invalidCheckout.data.items.Count) `
    ($invalidRow.invalid -eq $true -and $invalid.data.selectedAmount -eq 2095 -and $invalidCheckout.data.items.Count -eq 2)

# ---------------------------------------------------------------- #10 删除 / 清理已购
$deleted = Invoke-Api -Method Delete -Uri "$Gateway/api/cart/1005" -Token $token
$purchased = Invoke-Api -Method Post -Uri "$Gateway/api/cart/purchased/remove" -Token $token -Body @{ skuIds = @(1001) }
Add-Result '10' '删除单品 + 下单后清理' '删除 1005 后剩 2 行，清理 1001 后剩 1 行（失效行）' `
    ("deleteCode={0}, afterDelete={1}, cleanCode={2}, afterClean={3}" -f $deleted.code, $deleted.data.items.Count, $purchased.code, $purchased.data.items.Count) `
    ($deleted.code -eq 0 -and $deleted.data.items.Count -eq 2 -and $purchased.code -eq 0 -and $purchased.data.items.Count -eq 1)

# ---------------------------------------------------------------- #11 参数校验 / 空结算
$badClean = Invoke-Api -Method Post -Uri "$Gateway/api/cart/purchased/remove" -Token $token -Body @{}
$emptyCheckout = Invoke-Api -Method Post -Uri "$Gateway/api/cart/checkout" -Token $token
Add-Result '11' '清理参数校验 / 空结算' '10001 + 37006' `
    ("badClean={0}, emptyCheckout={1}" -f $badClean.code, $emptyCheckout.code) `
    ($badClean.code -eq 10001 -and $emptyCheckout.code -eq 37006)

if (-not $KeepData) { Reset-Cart $userId }
else { Write-Host ("[INFO] KeepData: mall:cart:{0} 未清理" -f $userId) }

Write-Host ''
Write-Host '=== 结果汇总 ==='
$script:Results | Format-Table -AutoSize | Out-String -Width 220 | Write-Host
$failed = @($script:Results | Where-Object { -not $_.Pass })
Write-Host ("通过 {0}/{1}" -f ($script:Results.Count - $failed.Count), $script:Results.Count)
if ($failed.Count -gt 0) {
    Write-Host '存在失败用例：'
    $failed | Format-Table -AutoSize | Out-String -Width 220 | Write-Host
    exit 1
}