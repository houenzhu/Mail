package com.zhe.mall.admin.upload;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置（商品图上传）。
 *
 * <p>密钥只从环境变量读取，不写入仓库：{@code AccessKeyID} / {@code AccessKeySecret}
 * 与桶名、Endpoint 分离，便于不同环境复用同一份代码。
 *
 * <p>商品图默认公共读（{@code publicRead=true}），返回不带签名的稳定 URL；
 * 若桶是私有读，把 {@code publicRead} 设为 false，会返回有效期由
 * {@code urlExpireSeconds} 控制的签名 URL——此时不要把签名 URL 长期保存为商品主图。
 */
@ConfigurationProperties(prefix = "mall.oss")
public class OssProperties {

    /** 是否启用上传；未配置密钥时保持 false，接口返回明确的存储不可用，而不是启动失败。 */
    private boolean enabled;

    private String endpoint;

    private String bucket;

    /** 可选：自定义访问域名（CDN 或绑定的自定义域名）；为空时用 bucket.endpoint。 */
    private String publicBaseUrl;

    /** 桶是否公共读；true 时返回稳定 URL，false 时返回签名 URL。 */
    private boolean publicRead = true;

    /** 私有桶签名 URL 有效期（秒）。 */
    private long urlExpireSeconds = 86400;

    /** 单文件上限（字节），默认 5 MB。 */
    private long maxFileSize = 5 * 1024 * 1024;

    /** 允许的图片扩展名（按文件头识别后落盘）。 */
    private String[] allowedExtensions = {"jpg", "png", "webp", "gif"};

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getEndpoint() { return endpoint; }

    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getBucket() { return bucket; }

    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getPublicBaseUrl() { return publicBaseUrl; }

    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }

    public boolean isPublicRead() { return publicRead; }

    public void setPublicRead(boolean publicRead) { this.publicRead = publicRead; }

    public long getUrlExpireSeconds() { return urlExpireSeconds; }

    public void setUrlExpireSeconds(long urlExpireSeconds) { this.urlExpireSeconds = urlExpireSeconds; }

    public long getMaxFileSize() { return maxFileSize; }

    public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }

    public String[] getAllowedExtensions() { return allowedExtensions; }

    public void setAllowedExtensions(String[] allowedExtensions) { this.allowedExtensions = allowedExtensions; }
}
