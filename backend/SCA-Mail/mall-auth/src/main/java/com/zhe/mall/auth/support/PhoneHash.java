package com.zhe.mall.auth.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 手机号哈希（见需求文档 21.4）：库中同时保存明文与 {@code SHA-256(phone + salt)}，
 * 唯一索引与按手机号查询走哈希列，避免明文索引泄露。
 */
public final class PhoneHash {

    private PhoneHash() {
    }

    public static String of(String phone, String salt) {
        if (phone == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(phone.getBytes(StandardCharsets.UTF_8));
            digest.update((salt == null ? "" : salt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }
}