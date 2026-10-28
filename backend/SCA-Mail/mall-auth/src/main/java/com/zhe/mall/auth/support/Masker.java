package com.zhe.mall.auth.support;

/**
 * 敏感字段脱敏（见需求文档 21.4），用于响应与日志。
 */
public final class Masker {

    private static final String MASK_SEGMENT = "****";

    private static final int PHONE_PREFIX_LENGTH = 3;

    private static final int PHONE_SUFFIX_LENGTH = 4;

    private Masker() {
    }

    public static String phone(String phone) {
        if (phone == null || phone.length() < PHONE_PREFIX_LENGTH + PHONE_SUFFIX_LENGTH) {
            return phone;
        }
        return phone.substring(0, PHONE_PREFIX_LENGTH) + MASK_SEGMENT
                + phone.substring(phone.length() - PHONE_SUFFIX_LENGTH);
    }
}