<#
.SYNOPSIS
    收货地址 + C 端下单 + Mock 支付 + 后台发货 + 确认收货 全链路 E2E。

.DESCRIPTION
    前置条件：
      - MySQL / Redis / Nacos / RocketMQ 可用，已执行 sql/16_mall_user_address.sql；
      - mall-auth / mall-user / mall-product / mall-inventory / mall-cart /
        mall-order / mall-pay / mall-admin / mall-gateway 已启动；
      - 默认测试账号 buyer01 / Buyer@123456、buyer02 / Buyer@123456、admin / Admin@123456。

    覆盖：
      地址 CRUD（首条默认、默认互斥、删除默认补位、越权）
      购物车 → 结算 → 下单 → 幂等重放 → 分页 / 详情（收货信息与明细）
      越权查单 → 发起支付 → Mock 支付 → 订单已支付 → 后台发货 → 确认收货 → 取消

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\e2e-order.ps1
#>
param(
    [string]$Gateway = 'http://127.0.0.1:10000',
    [string]$Username = 'buyer01',
    [string]$Password = 'Buyer@123456',
    [string]$OtherUsername = 'buyer02',
    [string]$OtherPassword = 'Buyer@123456',
    [string]$AdminUsername = 'admin',
    [string]$AdminPassword = 'Admin@123456',
    [long]$SkuId = 1001,
    [int]$Qty = 1,
    [string]$RunTag = ''
)

$ErrorActionPreference = 'Stop'
if (-not $RunTag) { $RunTag = (Get-Date -Format 'HHmmss') }

$script:Results = @()

function Add-Result {
    param([string]$Id, [string]$Case, [string]$Expect, [string]$Actual, [bool]$Pass)
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
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(20)
    # 不能直接 cast 字符串到 HttpMethod：PowerShell 会选错构造器，DELETE 会被判成非法方法
    $httpMethod = switch ($Method.ToUpperInvariant()) {
        'GET' { [System.Net.Http.HttpMethod]::Get }
        'POST' { [System.Net.Http.HttpMethod]::Post }
        'PUT' { [System.Net.Http.HttpMethod]::Put }
        'DELETE' { [System.Net.Http.HttpMethod]::Delete }
        'PATCH' { [System.Net.Http.HttpMethod]::Patch }
        default { throw "不支持的 HTTP 方法: $Method" }
    }
    $request = New-Object System.Net.Http.HttpRequestMessage($httpMethod, $Uri)
    if ($Token) {
        $request.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token)
    }
    if ($Headers) {
        $Headers.GetEnumerator() | ForEach-Object { $request.Headers.TryAddWithoutValidation($_.Key, [string]$_.Value) | Out-Null }
    }
    try {
        if ($null -ne $Body) {
            $json = ConvertTo-Json -InputObject $Body -Compress -Depth 8
            $request.Content = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, 'application/json')
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
        $client.Dispose()
    }
}

function Get-Token {
    param([string]$User, [string]$Pass)
    $r = Invoke-Api -Method Post -Uri "$Gateway/api/auth/login" -Body @{ username = $User; password = $Pass }
    if ($r.code -ne 0) { throw "登录失败: $User -> $($r.message)" }
    return $r.data
}

function New-TestUser {
    param([string]$User, [string]$PassReg, [string]$Phone)
    $reg = Invoke-Api -Method Post -Uri "$Gateway/api/auth/register" -Body @{
        username = $User; password = $PassReg; phone = $Phone; nickname = 'E2E'
    }
    if ($reg.code -ne 0 -and $reg.code -ne 31002 -and $reg.code -ne 31003) {
        throw "注册失败: $User -> $($reg.message)"
    }
}

function New-Address {
    param([string]$Token, [string]$Name, [string]$Phone, [string]$Region, [string]$Detail, [bool]$Default)
    return Invoke-Api -Method Post -Uri "$Gateway/api/user/address" -Token $Token -Body @{
        receiverName = $Name; receiverPhone = $Phone
        region = $Region; detailAddress = $Detail; isDefault = $Default
    }
}

Write-Host "=== 地址 / 下单 / 支付 E2E (tag=$RunTag) ==="

New-TestUser $OtherUsername $OtherPassword '13800000002'
$buyer = Get-Token $Username $Password
$other = Get-Token $OtherUsername $OtherPassword
$admin = Get-Token $AdminUsername $AdminPassword

# ---------------------------------------------------------------- #1 地址新增
$before = Invoke-Api -Method Get -Uri "$Gateway/api/user/address" -Token $buyer.accessToken
foreach ($row in @($before.data)) {
    if ($row -and $row.id) {
        Invoke-Api -Method Delete -Uri "$Gateway/api/user/address/$($row.id)" -Token $buyer.accessToken | Out-Null
    }
}

$a1 = New-Address $buyer.accessToken '张三' '13800000001' '广东省广州市天河区' '珠江新城 1 号' $false
$a2 = New-Address $buyer.accessToken '李四' '13800000003' '广东省深圳市南山区' '科技园 2 号' $false
Add-Result -Id '1' -Case '首条自动默认 / 第二条非默认' -Expect '两条地址，a1 默认，a2 非默认' -Actual ("code={0}/{1}, a1Default={2}, a2Default={3}" -f $a1.code, $a2.code, $a1.data.isDefault, $a2.data.isDefault) -Pass ($a1.code -eq 0 -and $a2.code -eq 0 -and $a1.data.isDefault -eq $true -and $a2.data.isDefault -eq $false)

Add-Result -Id '2' -Case 'fullAddress 拼接' -Expect 'region + 空格 + detail' -Actual ("full={0}" -f $a1.data.fullAddress) -Pass ($a1.data.fullAddress -eq '广东省广州市天河区 珠江新城 1 号')

# ---------------------------------------------------------------- #3 默认互斥
$setDefault = Invoke-Api -Method Put -Uri "$Gateway/api/user/address/$($a2.data.id)/default" -Token $buyer.accessToken
$list = Invoke-Api -Method Get -Uri "$Gateway/api/user/address" -Token $buyer.accessToken
$defaultCount = @($list.data | Where-Object { $_.isDefault }).Count
$a1After = @($list.data | Where-Object { $_.id -eq $a1.data.id })[0]
Add-Result -Id '3' -Case '切换默认地址互斥' -Expect '默认恰好 1 条，a1 被取消' -Actual ("code={0}, defaults={1}, a1Default={2}" -f $setDefault.code, $defaultCount, $a1After.isDefault) -Pass ($setDefault.code -eq 0 -and $defaultCount -eq 1 -and $a1After.isDefault -eq $false)

# ---------------------------------------------------------------- #4 越权
$stolen = Invoke-Api -Method Put -Uri "$Gateway/api/user/address/$($a1.data.id)" -Token $other.accessToken -Body @{
    receiverName = '入侵'; receiverPhone = '13800000009'
    region = 'x'; detailAddress = 'y'; isDefault = $false
}
Add-Result -Id '4' -Case '修改他人地址' -Expect '31007（不暴露存在性）' -Actual ("code={0}" -f $stolen.code) -Pass ($stolen.code -eq 31007)

# ---------------------------------------------------------------- #5 删除默认补位
$del = Invoke-Api -Method Delete -Uri "$Gateway/api/user/address/$($a2.data.id)" -Token $buyer.accessToken
$afterDel = Invoke-Api -Method Get -Uri "$Gateway/api/user/address" -Token $buyer.accessToken
$promoted = @($afterDel.data | Where-Object { $_.isDefault })
$defaultId = if ($promoted.Count) { $promoted[0].id } else { 'none' }
Add-Result -Id '5' -Case '删除默认地址自动补位' -Expect '剩 1 条且为默认' -Actual ("delCode={0}, rows={1}, defaultId={2}" -f $del.code, @($afterDel.data).Count, $defaultId) -Pass ($del.code -eq 0 -and @($afterDel.data).Count -eq 1 -and $promoted.Count -eq 1 -and $promoted[0].id -eq $a1.data.id)

# ---------------------------------------------------------------- #6 购物车 → 结算
# 清空购物车，确保结算金额只包含本用例的商品
$cartBefore = Invoke-Api -Method Get -Uri "$Gateway/api/cart" -Token $buyer.accessToken
foreach ($line in @($cartBefore.data.items)) {
    if ($line -and $line.skuId) {
        Invoke-Api -Method Delete -Uri "$Gateway/api/cart/$($line.skuId)" -Token $buyer.accessToken | Out-Null
    }
}
$add = Invoke-Api -Method Post -Uri "$Gateway/api/cart" -Token $buyer.accessToken -Body @{ skuId = $SkuId; quantity = $Qty }
# 历史用例可能留了同 SKU 的数量，这里显式改成目标值，保证金额断言可比
Invoke-Api -Method Put -Uri "$Gateway/api/cart" -Token $buyer.accessToken -Body @{ items = @(@{ skuId = $SkuId; quantity = $Qty }) } | Out-Null
$checkout = Invoke-Api -Method Post -Uri "$Gateway/api/cart/checkout" -Token $buyer.accessToken
Add-Result -Id '6' -Case '加购 → 结算预览' -Expect 'code=0，件数与金额一致' -Actual ("add={0}, checkout={1}, qty={2}, pay={3}" -f $add.code, $checkout.code, $checkout.data.totalQuantity, $checkout.data.payAmount) -Pass ($add.code -eq 0 -and $checkout.code -eq 0 -and $checkout.data.totalQuantity -eq $Qty -and [decimal]$checkout.data.payAmount -gt 0)

# ---------------------------------------------------------------- #7 下单
$orderBody = @{
    requestId = "e2e-order-$RunTag"
    receiverName = '张三'
    receiverPhone = '13800000001'
    receiverAddress = $a1.data.fullAddress
    remark = "E2E $RunTag"
    items = @(@{ skuId = $SkuId; qty = $Qty })
}
$order = Invoke-Api -Method Post -Uri "$Gateway/api/order" -Token $buyer.accessToken -Body $orderBody
$orderNo = $order.data.orderNo
Add-Result -Id '7' -Case '提交订单' -Expect 'code=0，status=10，金额与结算一致' -Actual ("code={0}, orderNo={1}, status={2}, pay={3}" -f $order.code, $orderNo, $order.data.status, $order.data.payAmount) -Pass ($order.code -eq 0 -and $order.data.status -eq 10 -and [decimal]$order.data.payAmount -eq [decimal]$checkout.data.payAmount)

# ---------------------------------------------------------------- #8 幂等
$replay = Invoke-Api -Method Post -Uri "$Gateway/api/order" -Token $buyer.accessToken -Body $orderBody
Add-Result -Id '8' -Case '重复 requestId 幂等' -Expect '同一 orderNo，idempotentReplay=true' -Actual ("code={0}, same={1}, replay={2}" -f $replay.code, ($replay.data.orderNo -eq $orderNo), $replay.data.idempotentReplay) -Pass ($replay.code -eq 0 -and $replay.data.orderNo -eq $orderNo -and $replay.data.idempotentReplay -eq $true)

# ---------------------------------------------------------------- #9 详情 / 分页
$detail = Invoke-Api -Method Get -Uri "$Gateway/api/order/$orderNo" -Token $buyer.accessToken
$page = Invoke-Api -Method Get -Uri "$Gateway/api/order/page?status=10&pageNo=1&pageSize=10" -Token $buyer.accessToken
$row = @($page.data.list | Where-Object { $_.order.orderNo -eq $orderNo })
$rowItems = if ($row.Count) { $row[0].items.Count } else { -1 }
Add-Result -Id '9' -Case '详情带收货信息 / 分页带明细' -Expect 'receiver 一致，分页含该单与 1 条明细' -Actual ("receiver={0}, items={1}, pageTotal={2}, rowItems={3}" -f $detail.data.order.receiverName, $detail.data.items.Count, $page.data.total, $rowItems) -Pass ($detail.code -eq 0 -and $detail.data.order.receiverName -eq '张三' -and $detail.data.order.receiverAddress -eq $a1.data.fullAddress -and $detail.data.items.Count -eq 1 -and $row.Count -eq 1 -and $row[0].items.Count -eq 1)

# ---------------------------------------------------------------- #10 越权查单
$peek = Invoke-Api -Method Get -Uri "$Gateway/api/order/$orderNo" -Token $other.accessToken
$peekCancel = Invoke-Api -Method Post -Uri "$Gateway/api/order/$orderNo/cancel" -Token $other.accessToken -Body @{ reason = 'x' }
Add-Result -Id '10' -Case '他人查看 / 取消订单' -Expect '均 20003' -Actual ("detail={0}, cancel={1}" -f $peek.code, $peekCancel.code) -Pass ($peek.code -eq 20003 -and $peekCancel.code -eq 20003)

# ---------------------------------------------------------------- #11 发起支付
$pay = Invoke-Api -Method Post -Uri "$Gateway/api/pay" -Token $buyer.accessToken -Body @{
    orderNo = $orderNo; payType = 1; requestId = "e2e-pay-$RunTag"
}
$paymentNo = $pay.data.paymentNo
Add-Result -Id '11' -Case '发起支付' -Expect 'code=0，channel=MOCK，status=10' -Actual ("code={0}, paymentNo={1}, channel={2}, status={3}" -f $pay.code, $paymentNo, $pay.data.channel, $pay.data.status) -Pass ($pay.code -eq 0 -and $pay.data.channel -eq 'MOCK' -and $pay.data.status -eq 10 -and [bool]$pay.data.channelParams.prepayId)

# ---------------------------------------------------------------- #12 越权支付
$payPeek = Invoke-Api -Method Post -Uri "$Gateway/api/pay/mock/$paymentNo/pay" -Token $other.accessToken
Add-Result -Id '12' -Case '他人模拟支付' -Expect '20003' -Actual ("code={0}" -f $payPeek.code) -Pass ($payPeek.code -eq 20003)

# ---------------------------------------------------------------- #13 模拟支付
$mock = Invoke-Api -Method Post -Uri "$Gateway/api/pay/mock/$paymentNo/pay" -Token $buyer.accessToken
$payInfo = Invoke-Api -Method Get -Uri "$Gateway/api/pay/$paymentNo" -Token $buyer.accessToken
$orderPaid = Invoke-Api -Method Get -Uri "$Gateway/api/order/$orderNo" -Token $buyer.accessToken
Add-Result -Id '13' -Case 'Mock 支付闭环' -Expect '支付单 20，订单 20，paidAt 非空' -Actual ("mock={0}, payStatus={1}, orderStatus={2}, paidAt={3}" -f $mock.code, $payInfo.data.status, $orderPaid.data.order.status, [bool]$orderPaid.data.order.paidAt) -Pass ($mock.code -eq 0 -and $payInfo.data.status -eq 20 -and $orderPaid.data.order.status -eq 20 -and [bool]$orderPaid.data.order.paidAt)

# ---------------------------------------------------------------- #14 后台发货
$ship = Invoke-Api -Method Post -Uri "$Gateway/api/order/admin/$orderNo/ship" -Token $admin.accessToken -Body @{
    shippingCompany = '顺丰速运'; trackingNo = "SF$RunTag"
}
Add-Result -Id '14' -Case '后台发货' -Expect 'code=0，status=30，物流单号写入' -Actual ("code={0}, status={1}, tracking={2}" -f $ship.code, $ship.data.order.status, $ship.data.order.trackingNo) -Pass ($ship.code -eq 0 -and $ship.data.order.status -eq 30 -and $ship.data.order.trackingNo -eq "SF$RunTag")

# ---------------------------------------------------------------- #15 买家确认收货
$confirm = Invoke-Api -Method Post -Uri "$Gateway/api/order/$orderNo/confirm" -Token $buyer.accessToken
Add-Result -Id '15' -Case '买家确认收货' -Expect 'status=40' -Actual ("code={0}, status={1}" -f $confirm.code, $confirm.data.status) -Pass ($confirm.code -eq 0 -and $confirm.data.status -eq 40)

# ---------------------------------------------------------------- #16 取消另一单
$cancelBody = @{
    requestId = "e2e-cancel-$RunTag"
    receiverName = '张三'; receiverPhone = '13800000001'
    receiverAddress = $a1.data.fullAddress
    items = @(@{ skuId = $SkuId; qty = 1 })
}
$cancelOrder = Invoke-Api -Method Post -Uri "$Gateway/api/order" -Token $buyer.accessToken -Body $cancelBody
$cancel = Invoke-Api -Method Post -Uri "$Gateway/api/order/$($cancelOrder.data.orderNo)/cancel" -Token $buyer.accessToken -Body @{ reason = 'E2E 不想要了' }
Add-Result -Id '16' -Case '取消待支付订单' -Expect 'status=50，cancelReason 写入' -Actual ("code={0}, status={1}, reason={2}" -f $cancel.code, $cancel.data.status, $cancel.data.cancelReason) -Pass ($cancel.code -eq 0 -and $cancel.data.status -eq 50 -and $cancel.data.cancelReason -eq 'E2E 不想要了')

# ---------------------------------------------------------------- #17 状态日志
$final = Invoke-Api -Method Get -Uri "$Gateway/api/order/$orderNo" -Token $buyer.accessToken
$toStatus = @($final.data.logs | ForEach-Object { $_.toStatus })
Add-Result -Id '17' -Case '状态流转日志完整' -Expect '10 → 20 → 30 → 40' -Actual ("logs={0}" -f ($toStatus -join ',')) -Pass (($toStatus -join ',') -eq '10,20,30,40')

# ---------------------------------------------------------------- #18 购物车清理
Invoke-Api -Method Post -Uri "$Gateway/api/cart/purchased/remove" -Token $buyer.accessToken -Body @{ skuIds = @($SkuId) } | Out-Null
$cart = Invoke-Api -Method Get -Uri "$Gateway/api/cart" -Token $buyer.accessToken
$left = @($cart.data.items | Where-Object { $_.skuId -eq $SkuId }).Count
Add-Result -Id '18' -Case '下单后清理已购' -Expect '购物车无该 SKU' -Actual ("rows={0}" -f $left) -Pass ($left -eq 0)

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
