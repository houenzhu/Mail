package com.zhe.mall.admin.upload;

/** 上传结果：objectKey 用于排查/删除，url 直接给前端保存到商品主图。 */
public record UploadResult(String objectKey, String url, long size) {
}