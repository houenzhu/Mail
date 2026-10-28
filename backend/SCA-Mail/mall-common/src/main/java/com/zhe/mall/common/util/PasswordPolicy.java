package com.zhe.mall.common.util;

/**
 * 口令强度策略（见需求文档 21.4）：8~32 位，必须同时包含字母与数字，不允许空白字符。
 *
 * <p>注册、改密、后台建号共用同一份规则，避免规则散落在各服务（12.17）。
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    public static final int MAX_LENGTH = 32;

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return describeViolation(password) == null;
    }

    /**
     * 合规返回 {@code null}，否则返回可直接回给用户的提示。
     */
    public static String describeViolation(String password) {
        if (password == null || password.isEmpty()) {
            return "密码不能为空";
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return "密码长度需为 " + MIN_LENGTH + "~" + MAX_LENGTH + " 位";
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            return "密码不能包含空白字符";
        }
        if (!password.chars().anyMatch(Character::isLetter) || !password.chars().anyMatch(Character::isDigit)) {
            return "密码需同时包含字母与数字";
        }
        return null;
    }
}