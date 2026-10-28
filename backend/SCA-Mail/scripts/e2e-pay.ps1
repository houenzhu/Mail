<#
.SYNOPSIS
    mall-pay 端到端验证脚本（需求文档 28.11 的 18 条用例）。

.DESCRIPTION
    前置条件：7 个服务已启动（scripts\start-all.ps1），Nacos / MySQL / Redis 可用。
    为了让状态可复现，本脚本假设 mall-pay 以 PAY_QUERY_ENABLED=false 启动
    （查询兜底任务会在一分钟内把支付单自动推到成功，导致"回调类"用例观察不到中间态）；
    用例 #11 用内部同步接口手工触发同一条兜底链路。

    脚本会创建测试订单与支付单，并在结尾打印结果表。可用 -KeepData 保留数据供排障。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\e2e-pay.ps1
#>
param(
    [string]$Gateway = 'http://127.0.0.1:10000',
    [string]$OrderBase = 'http://127.0.0.1:8105',
    [string]$PayBase = 'http://127.0.0.1:8106',
    [string]$InternalToken = $env:INTERNAL_TOKEN,
    [string]$PaySecret = $env:PAY_CHANNEL_SECRET,
    [string]$RedisHost = '192.168.198.133',
    [int]$RedisPort = 6379,
    [string]$RedisPassword = $env:REDIS_PASSWORD,
    [int]$RedisDatabase = 1,
    [string]$RunTag = '',
    [switch]$KeepData
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $RunTag) { $RunTag = (Get-Date -Format 'HHmmss') }
if (-not $InternalToken) { throw 'INTERNAL_TOKEN is required; set it in the environment before running this script.' }
if (-not $PaySecret) { throw 'PAY_CHANNEL_SECRET is required; set it in the environment before running this script.' }

$script:Results = @()
$script:Context = [ordered]@{}

function Add-Result([string]$Id, [string]$Case, [string]$Expect, [string]$Actual, [bool]$Pass) {
    $script:Results += [pscustomobject]@{ Id = $Id; Case = $Case; Expect = $Expect; Actual = $Actual; Pass = $Pass }
    $mark = if ($Pass) { 'PASS' } else { 'FAIL' }
    Write-Host ("[{0}] #{1} {2} -> {3}" -f $mark, $Id, $Case, $Actual)
}

function Get-Sign([string]$Content, [string]$Secret) {
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($Secret)
    $hash = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Content))
    ($hash | ForEach-Object { $_.ToString('X2') }) -join ''
}

function New-CallbackSign([string]$PaymentNo, [string]$TradeNo, [string]$Amount, [string]$Status, [long]$Timestamp) {
    $content = "amount=$Amount&channelTradeNo=$TradeNo&paymentNo=$PaymentNo&status=$Status&timestamp=$Timestamp"
    Get-Sign $content $PaySecret
}

function Invoke-Api {
    param(
        [string]$Method = 'Get',
        [string]$Uri,
        [string]$Token,
        $Body,
        [string]$InternalTokenHeader
    )
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    if ($InternalTokenHeader) { $headers['X-Gateway-Token'] = $InternalTokenHeader }
    $params = @{ Method = $Method; Uri = $Uri; Headers = $headers; TimeoutSec = 20 }
    if ($null -ne $Body) {
        $params['ContentType'] = 'application/json; charset=utf-8'
        $params['Body'] = ($Body | ConvertTo-Json -Compress -Depth 6)
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
        }
        if (-not $payload) { $payload = [pscustomobject]@{ code = -1; message = $_.Exception.Message; data = $null } }
        $payload | Add-Member -NotePropertyName httpStatus -NotePropertyValue $status -Force
        return $payload
    }
}

function Get-Token([string]$Username, [string]$Password) {
    $r = Invoke-Api -Method Post -Uri "$Gateway/api/auth/login" -Body @{ username = $Username; password = $Password }
    if ($r.code -ne 0) { throw "登录失败: $Username -> $($r.message)" }
    return $r.data
}

function New-Order([string]$Token, [string]$RequestId, [int]$SkuId = 1001) {
    $r = Invoke-Api -Method Post -Uri "$Gateway/api/order" -Token $Token -Body @{
        requestId = $RequestId
        receiverName = 'E2E'
        receiverPhone = '13800000010'
        receiverAddress = '上海市浦东新区世纪大道 1 号'
        items = @(@{ skuId = $SkuId; qty = 1 })
    }
    if ($r.code -ne 0) { throw "下单失败: $($r.message)" }
    return $r.data
}

function Get-AmountText($Amount) { '{0:F2}' -f [decimal]$Amount }

Write-Host "=== mall-pay E2E (tag=$RunTag) ==="

# ---------------------------------------------------------------- 准备
$buyer = Get-Token 'buyer01' 'Buyer@123456'
$admin = Get-Token 'admin' 'Admin@123456'
$otherName = "paytester$RunTag"
# 手机号必须满足 ^1[3-9]\d{9}$，用随机 8 位数字，避免 RunTag 含字母时校验失败
$otherPhone = '139' + (Get-Random -Minimum 10000000 -Maximum 99999999)
$reg = Invoke-Api -Method Post -Uri "$Gateway/api/auth/register" -Body @{
    username = $otherName; password = 'Pay@123456'; phone = $otherPhone; nickname = 'E2E 越权测试'
}
if ($reg.code -ne 0) {
    Write-Host ("[WARN] 注册未成功（继续尝试登录）: code={0} message={1}" -f $reg.code, $reg.message)
}
$other = Get-Token $otherName 'Pay@123456'
$script:Context['buyerId'] = $buyer.userId
$script:Context['otherId'] = $other.userId

$orderA = New-Order $buyer.accessToken "e2e-pay-$RunTag-a"
$orderB = New-Order $buyer.accessToken "e2e-pay-$RunTag-b"
$orderC = New-Order $buyer.accessToken "e2e-pay-$RunTag-c"
$script:Context['orderA'] = $orderA.orderNo
$script:Context['orderB'] = $orderB.orderNo
$script:Context['orderC'] = $orderC.orderNo
Write-Host ("orders: A={0} B={1} C={2}" -f $orderA.orderNo, $orderB.orderNo, $orderC.orderNo)

# ---------------------------------------------------------------- #1 发起支付
$payA = Invoke-Api -Method Post -Uri "$Gateway/api/pay" -Token $buyer.accessToken -Body @{
    orderNo = $orderA.orderNo; payType = 1; requestId = "req-$RunTag-001"
}
$paymentNo = $payA.data.paymentNo
$script:Context['paymentNo'] = $paymentNo
$script:Context['tradeNo'] = "MOCK_TRADE_$paymentNo"
$amountA = Get-AmountText $payA.data.amount
Add-Result '1' '发起支付' 'paymentNo + status=10 + channelParams.sign' `
    ("paymentNo={0}, status={1}, prepayId={2}, sign={3}" -f $paymentNo, $payA.data.status, $payA.data.channelParams.prepayId, $payA.data.channelParams.sign.Substring(0,8)) `
    ($payA.code -eq 0 -and $payA.data.status -eq 10 -and $payA.data.channelParams.sign -and $payA.data.channelParams.prepayId -notlike '*null*')

# ---------------------------------------------------------------- #2 发起支付幂等
$payA2 = Invoke-Api -Method Post -Uri "$Gateway/api/pay" -Token $buyer.accessToken -Body @{
    orderNo = $orderA.orderNo; payType = 1; requestId = "req-$RunTag-001"
}
Add-Result '2' '发起支付幂等' '返回同一 paymentNo' `
    ("paymentNo={0}, idempotentReplay={1}" -f $payA2.data.paymentNo, $payA2.data.idempotentReplay) `
    ($payA2.data.paymentNo -eq $paymentNo -and $payA2.data.idempotentReplay)

# ---------------------------------------------------------------- #3 越权发起
$pay3 = Invoke-Api -Method Post -Uri "$Gateway/api/pay" -Token $other.accessToken -Body @{
    orderNo = $orderA.orderNo; payType = 1; requestId = "req-$RunTag-003"
}
Add-Result '3' '越权发起' '20003' ("code={0}, message={1}" -f $pay3.code, $pay3.message) ($pay3.code -eq 20003)

# ---------------------------------------------------------------- #4 未待支付订单发起（用已支付后的 A 复测，见 #5 之后）
# ---------------------------------------------------------------- #5 回调成功
$ts5 = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$sign5 = New-CallbackSign $paymentNo "MOCK_TRADE_$paymentNo" $amountA 'SUCCESS' $ts5
$cb5 = Invoke-Api -Method Post -Uri "$Gateway/api/pay/callback/MOCK" -Body @{
    paymentNo = $paymentNo; channelTradeNo = "MOCK_TRADE_$paymentNo"; amount = [decimal]$amountA
    status = 'SUCCESS'; payType = 1; timestamp = $ts5; sign = $sign5
}
$orderAfterPay = Invoke-Api -Method Get -Uri "$Gateway/api/order/$($orderA.orderNo)" -Token $buyer.accessToken
$paidAt = $orderAfterPay.data.order.paidAt
$payDetail = Invoke-Api -Method Get -Uri "$Gateway/api/pay/$paymentNo" -Token $buyer.accessToken
Add-Result '5' '回调成功' 'payment 20 / orders 20 / paid_at 落库 / operator=pay' `
    ("callbackCode={0}, payment.status={1}, order.status={2}, paidAt={3}" -f $cb5.code, $payDetail.data.status, $orderAfterPay.data.order.status, $paidAt) `
    ($cb5.code -eq 0 -and $payDetail.data.status -eq 20 -and $orderAfterPay.data.order.status -eq 20 -and $paidAt)

# ---------------------------------------------------------------- #4 未待支付订单发起
$pay4 = Invoke-Api -Method Post -Uri "$Gateway/api/pay" -Token $buyer.accessToken -Body @{
    orderNo = $orderA.orderNo; payType = 1; requestId = "req-$RunTag-004"
}
Add-Result '4' '未待支付订单发起' '34002' ("code={0}, message={1}" -f $pay4.code, $pay4.message) ($pay4.code -eq 34002)

# ---------------------------------------------------------------- #6 重复回调
$cb6 = Invoke-Api -Method Post -Uri "$Gateway/api/pay/callback/MOCK" -Body @{
    paymentNo = $paymentNo; channelTradeNo = "MOCK_TRADE_$paymentNo"; amount = [decimal]$amountA
    status = 'SUCCESS'; payType = 1; timestamp = $ts5; sign = $sign5
}
$payDetail6 = Invoke-Api -Method Get -Uri "$Gateway/api/pay/$paymentNo" -Token $buyer.accessToken
Add-Result '6' '重复回调' '幂等成功，状态不再变更' `
    ("code={0}, idempotentReplay={1}, payment.status={2}, order.status={3}" -f $cb6.code, $cb6.data.idempotentReplay, $payDetail6.data.status, (Invoke-Api -Method Get -Uri "$Gateway/api/order/$($orderA.orderNo)" -Token $buyer.accessToken).data.order.status) `
    ($cb6.code -eq 0 -and $cb6.data.idempotentReplay -and $payDetail6.data.status -eq 20)

# ---------------------------------------------------------------- #7 验签失败
$ts7 = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$sign7 = New-CallbackSign $paymentNo "MOCK_TRADE_$paymentNo" $amountA 'SUCCESS' $ts7
$cb7 = Invoke-Api -Method Post -Uri "$Gateway/api/pay/callback/MOCK" -Body @{
    paymentNo = $paymentNo; channelTradeNo = "MOCK_TRADE_BAD_$RunTag"; amount = [decimal]$amountA
    status = 'SUCCESS'; payType = 1; timestamp = $ts7; sign = ('0' * 64)
}
$payDetail7 = Invoke-Api -Method Get -Uri "$Gateway/api/pay/$paymentNo" -Token $buyer.accessToken
Add-Result '7' '验签失败' '35002，payment 状态不变' `
    ("code={0}, message={1}, payment.status={2}" -f $cb7.code, $cb7.message, $payDetail7.data.status) `
    ($cb7.code -eq 35002 -and $payDetail7.data.status -eq 20)

# ---------------------------------------------------------------- #8 超时间窗
$oldTs = [DateTimeOffset]::UtcNow.AddMinutes(-10).ToUnixTimeMilliseconds()
$sign8 = New-CallbackSign $paymentNo "MOCK_TRADE_$paymentNo" $amountA 'SUCCESS' $oldTs
$cb8 = Invoke-Api -Method Post -Uri "$Gateway/api/pay/callback/MOCK" -Body @{
    paymentNo = $paymentNo; channelTradeNo = "MOCK_TRADE_$paymentNo"; amount = [decimal]$amountA
    status = 'SUCCESS'; payType = 1; timestamp = $oldTs; sign = $sign8
}
Add-Result '8' '超时间窗' '35008' ("code={0}, message={1}" -f $cb8.code, $cb8.message) ($cb8.code -eq 35008)

# ---------------------------------------------------------------- #9 金额不一致
$ts9 = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$sign9 = New-CallbackSign $paymentNo "MOCK_TRADE_AMT_$RunTag" '1.00' 'SUCCESS' $ts9
$cb9 = Invoke-Api -Method Post -Uri "$Gateway/api/pay/callback/MOCK" -Body @{
    paymentNo = $paymentNo; channelTradeNo = "MOCK_TRADE_AMT_$RunTag"; amount = [decimal]'1.00'
    status = 'SUCCESS'; payType = 1; timestamp = $ts9; sign = $sign9
}
Add-Result '9' '金额不一致' '34004' ("code={0}, message={1}" -f $cb9.code, $cb9.message) ($cb9.code -eq 34004)

# ---------------------------------------------------------------- #10 支付单查询
$self = Invoke-Api -Method Get -Uri "$Gateway/api/pay/$paymentNo" -Token $buyer.accessToken
$otherView = Invoke-Api -Method Get -Uri "$Gateway/api/pay/$paymentNo" -Token $other.accessToken
$staffView = Invoke-Api -Method Get -Uri "$Gateway/api/pay/$paymentNo" -Token $admin.accessToken
Add-Result '10' '支付单查询' '本人可查、他人 20003、客服可查' `
    ("self={0}, other={1}, staff={2}" -f $self.code, $otherView.code, $staffView.code) `
    ($self.code -eq 0 -and $otherView.code -eq 20003 -and $staffView.code -eq 0)

# ---------------------------------------------------------------- #11 回调丢失兜底
$payB = Invoke-Api -Method Post -Uri "$Gateway/api/pay" -Token $buyer.accessToken -Body @{
    orderNo = $orderB.orderNo; payType = 2; requestId = "req-$RunTag-011"
}
# 内部接口必须直连服务：网关会一刀切 403 拦截含 /internal/ 的外部访问（ADR-022）
$syncB = Invoke-Api -Method Post -Uri "$PayBase/api/pay/internal/sync/$($payB.data.paymentNo)" -InternalTokenHeader $InternalToken
$orderBAfter = Invoke-Api -Method Get -Uri "$Gateway/api/order/$($orderB.orderNo)" -Token $buyer.accessToken
Add-Result '11' '回调丢失兜底（内部同步）' 'payment 20，订单 20' `
    ("syncCode={0}, payment.status={1}, order.status={2}" -f $syncB.code, $syncB.data.status, $orderBAfter.data.order.status) `
    ($syncB.code -eq 0 -and $syncB.data.status -eq 20 -and $orderBAfter.data.order.status -eq 20)

# ---------------------------------------------------------------- #12 退款申请
$refund = Invoke-Api -Method Post -Uri "$Gateway/api/pay/refund" -Token $buyer.accessToken -Body @{
    orderNo = $orderA.orderNo; reason = 'E2E 全额退款'
}
$payAfterRefund = Invoke-Api -Method Get -Uri "$Gateway/api/pay/$paymentNo" -Token $buyer.accessToken
$orderAfterRefund = Invoke-Api -Method Get -Uri "$Gateway/api/order/$($orderA.orderNo)" -Token $buyer.accessToken
Add-Result '12' '退款申请' 'refund 20 / payment 50 / orders 70' `
    ("refundNo={0}, refund.status={1}, payment.status={2}, order.status={3}" -f $refund.data.refundNo, $refund.data.status, $payAfterRefund.data.status, $orderAfterRefund.data.order.status) `
    ($refund.code -eq 0 -and $refund.data.status -eq 20 -and $payAfterRefund.data.status -eq 50 -and $orderAfterRefund.data.order.status -eq 70)

# ---------------------------------------------------------------- #13 退款幂等
$refund2 = Invoke-Api -Method Post -Uri "$Gateway/api/pay/refund" -Token $buyer.accessToken -Body @{
    orderNo = $orderA.orderNo; reason = 'E2E 重复申请'
}
$refundView = Invoke-Api -Method Get -Uri "$Gateway/api/pay/refund/$($refund.data.refundNo)" -Token $buyer.accessToken
Add-Result '13' '退款幂等' '返回同一 refundNo' `
    ("refundNo={0}, same={1}, idempotentReplay={2}, 查询={3}" -f $refund2.data.refundNo, ($refund2.data.refundNo -eq $refund.data.refundNo), $refund2.data.idempotentReplay, $refundView.code) `
    ($refund2.data.refundNo -eq $refund.data.refundNo -and $refund2.data.idempotentReplay -and $refundView.code -eq 0)

# ---------------------------------------------------------------- #14 非法退款
$refund14 = Invoke-Api -Method Post -Uri "$Gateway/api/pay/refund" -Token $buyer.accessToken -Body @{
    orderNo = $orderC.orderNo; reason = 'E2E 未支付订单'
}
Add-Result '14' '非法退款（未支付订单）' '34002' ("code={0}, message={1}" -f $refund14.code, $refund14.message) ($refund14.code -eq 34002)

# ---------------------------------------------------------------- #15 回调越权修复
$direct = Invoke-Api -Method Post -Uri "$OrderBase/api/order/internal/$($orderC.orderNo)/pay-callback" -Body @{
    paymentNo = 'P-HACK'; payType = 1; amount = 0.01
}
$viaGateway = Invoke-Api -Method Post -Uri "$Gateway/api/order/internal/$($orderC.orderNo)/pay-callback" -Body @{
    paymentNo = 'P-HACK'; payType = 1; amount = 0.01
}
$orderCAfter = Invoke-Api -Method Get -Uri "$Gateway/api/order/$($orderC.orderNo)" -Token $buyer.accessToken
Add-Result '15' '回调越权修复' '直连 20003；经网关 403；订单仍待支付' `
    ("directCode={0}, gatewayHttp={1}, gatewayCode={2}, orderC.status={3}" -f $direct.code, $viaGateway.httpStatus, $viaGateway.code, $orderCAfter.data.order.status) `
    ($direct.code -eq 20003 -and $viaGateway.httpStatus -eq 403 -and $orderCAfter.data.order.status -eq 10)

# ---------------------------------------------------------------- #16 对账差异
$today = (Get-Date).ToString('yyyy-MM-dd')
$fakeTrade = "MOCK_TRADE_FAKE_$RunTag"
& (Join-Path $PSScriptRoot 'redis-cli.ps1') "HSET mall:pay:mock:bill:$today $fakeTrade PFAKE$RunTag`|1.00`|SUCCESS" -RedisHost $RedisHost -Port $RedisPort -Password $RedisPassword -Database $RedisDatabase | Out-Null
$rec16 = Invoke-Api -Method Post -Uri "$PayBase/api/pay/internal/reconcile/$today" -InternalTokenHeader $InternalToken
$channelOnly = @($rec16.data.diffs | Where-Object { $_.diffType -eq 'CHANNEL_ONLY' -and $_.channelTradeNo -eq $fakeTrade })
Add-Result '16' '对账差异（渠道有库中无）' '返回并落表 1 条 CHANNEL_ONLY' `
    ("diffCount={0}, CHANNEL_ONLY命中={1}, channelCount={2}, localCount={3}" -f $rec16.data.diffCount, $channelOnly.Count, $rec16.data.channelCount, $rec16.data.localCount) `
    ($rec16.code -eq 0 -and $channelOnly.Count -eq 1)

# ---------------------------------------------------------------- #17 对账无差异 + 幂等
$clean = '2026-09-01'
$rec17a = Invoke-Api -Method Post -Uri "$PayBase/api/pay/internal/reconcile/$clean" -InternalTokenHeader $InternalToken
$rec17b = Invoke-Api -Method Post -Uri "$PayBase/api/pay/internal/reconcile/$today" -InternalTokenHeader $InternalToken
Add-Result '17' '对账无差异 + 重复跑不重复落' '干净日 diffCount=0；同日重跑差异数不变' `
    ("cleanDay.diffCount={0}, today.rerun.diffCount={1}" -f $rec17a.data.diffCount, $rec17b.data.diffCount) `
    ($rec17a.data.diffCount -eq 0 -and $rec17b.data.diffCount -eq $rec16.data.diffCount)

# ---------------------------------------------------------------- 汇总
Write-Host ''
Write-Host '=== 结果汇总 ==='
$script:Results | Format-Table -AutoSize | Out-String -Width 240 | Write-Host
$failed = @($script:Results | Where-Object { -not $_.Pass })
Write-Host ("通过 {0}/{1}" -f ($script:Results.Count - $failed.Count), $script:Results.Count)
Write-Host ("上下文: " + ($script:Context.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', ')
if ($failed.Count -gt 0) { Write-Host '存在失败用例：'; $failed | Format-Table -AutoSize | Out-String -Width 240 | Write-Host }
