package com.zhe.mall.pay.channel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * HMAC-SHA256 签名 / 验签（28.4 / 28.6 第 3 步）。
 *
 * <p>签名串口径（两侧必须完全一致）：
 * <ul>
 *   <li>{@code key=value} 按 key <b>升序</b>排列，{@code &} 连接；</li>
 *   <li><b>空值不参与</b>签名（避免"字段缺失"与"字段为空"产生两个不同的签名串）；</li>
 *   <li>{@code sign} 本身永不参与签名。</li>
 * </ul>
 * 示例：{@code amount=499.00&channelTradeNo=MOCK_TRADE_001&paymentNo=P...&status=SUCCESS&timestamp=1786000000000}
 */
public final class PaySigner {

    private static final String ALGORITHM = "HmacSHA256";

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private PaySigner() {
    }

    /**
     * 金额文本（签名串专用）。
     *
     * <p>必须固定 2 位小数：{@code 499} 与 {@code 499.00} 是两个不同的签名串，
     * 两侧任一方少写一位小数就会"验签永远失败且看不出原因"。
     */
    public static String amountText(java.math.BigDecimal amount) {
        return amount == null ? "0.00"
                : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /** 拼接签名串。 */    public static String signContent(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>();
        params.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                sorted.put(key, value);
            }
        });
        StringBuilder sb = new StringBuilder();
        sorted.forEach((key, value) -> {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(key).append('=').append(value);
        });
        return sb.toString();
    }

    /** 计算签名，返回十六进制大写。 */
    public static String sign(Map<String, String> params, String secret) {
        String content = signContent(params);
        return hmacSha256Hex(content, secret);
    }

    /**
     * 验签。
     *
     * <p>用 {@link MessageDigest#isEqual} 做<b>常量时间比较</b>，避免按字符提前返回导致的时序侧信道（28.6 第 3 步）。
     * 大小写不敏感（Hex 大写是约定，但容忍小写输入）。
     */
    public static boolean verify(Map<String, String> params, String sign, String secret) {
        if (sign == null || sign.isBlank()) {
            return false;
        }
        String expected = sign(params, secret);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                sign.trim().toUpperCase().getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha256Hex(String content, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] raw = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            char[] out = new char[raw.length * 2];
            for (int i = 0; i < raw.length; i++) {
                int v = raw[i] & 0xFF;
                out[i * 2] = HEX[v >>> 4];
                out[i * 2 + 1] = HEX[v & 0x0F];
            }
            return new String(out);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }
}
