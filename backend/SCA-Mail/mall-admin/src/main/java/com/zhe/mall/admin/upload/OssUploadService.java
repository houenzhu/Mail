package com.zhe.mall.admin.upload;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 商品图上传：服务端校验后写入阿里云 OSS。
 *
 * <p>不把 AccessKey 下发给浏览器，也不做前端直传：直传需要临时 STS 凭据与回调校验，
 * 当前后台规模用一个受角色保护的服务端上传接口更简单可控。
 *
 * <p>文件类型按文件头（magic number）识别，而不是只看客户端传来的 Content-Type
 * 或文件名后缀——后者可以被任意伪造，直接落盘会带来 XSS / 存储滥用风险。
 */
@Service
public class OssUploadService {

    private static final Logger log = LoggerFactory.getLogger(OssUploadService.class);

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final OssProperties properties;

    /** 延迟创建：未配置 OSS 的本地环境不启动也不会因缺少 endpoint 报错。 */
    private volatile OSS client;

    public OssUploadService(OssProperties properties) {
        this.properties = properties;
    }

    public UploadResult upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.UPLOAD_FILE_EMPTY);
        }
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new BusinessException(ResultCode.UPLOAD_FILE_TOO_LARGE,
                    "图片不能超过 " + (properties.getMaxFileSize() / 1024 / 1024) + " MB");
        }
        ImageType type = detect(file);
        String objectKey = "product/" + LocalDate.now().format(DATE_PATH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + type.extension();
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(type.contentType());
            // 浏览器直接按图片渲染，不要再让 OSS 以 attachment 下载
            metadata.setContentDisposition("inline");
            try (InputStream input = file.getInputStream()) {
                client().putObject(properties.getBucket(), objectKey, input, metadata);
            }
        } catch (IOException e) {
            log.warn("读取上传文件失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "读取上传文件失败");
        } catch (BusinessException e) {
            // 配置缺失属于部署问题，用 WARN；真正的 SDK 失败在下面记 ERROR
            log.warn("OSS 未就绪: bucket={}, key={}, reason={}", properties.getBucket(), objectKey, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.error("OSS 上传失败: bucket={}, key={}, error={}", properties.getBucket(), objectKey, e.getMessage());
            throw new BusinessException(ResultCode.UPLOAD_STORAGE_UNAVAILABLE);
        }
        return new UploadResult(objectKey, buildUrl(objectKey), file.getSize());
    }

    private OSS client() {
        ensureConfigured();
        OSS current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) {
                    client = new OSSClientBuilder().build(properties.getEndpoint(),
                            System.getenv("AccessKeyID"), System.getenv("AccessKeySecret"));
                }
                current = client;
            }
        }
        return current;
    }

    private void ensureConfigured() {
        if (!properties.isEnabled() || isBlank(properties.getEndpoint()) || isBlank(properties.getBucket())
                || isBlank(System.getenv("AccessKeyID")) || isBlank(System.getenv("AccessKeySecret"))) {
            throw new BusinessException(ResultCode.UPLOAD_STORAGE_UNAVAILABLE,
                    "文件存储未配置：请设置 mall.oss.* 与 AccessKeyID / AccessKeySecret");
        }
    }

    /**
     * 公共读桶返回稳定 URL；私有桶才生成带签名的临时 URL。
     *
     * <p>商品主图会直接落库保存这个 URL，若默认按私有桶返回签名地址，签名过期后
     * 历史商品会集体破图，因此公共读是商品图场景的默认配置。
     */
    String buildUrl(String objectKey) {
        String base = properties.getPublicBaseUrl();
        if (isBlank(base)) {
            base = "https://" + properties.getBucket() + "." + normalizeEndpoint(properties.getEndpoint());
        }
        base = base.endsWith("/") ? base : base + "/";
        if (properties.isPublicRead()) {
            return base + objectKey;
        }
        // 私有桶：返回带签名的临时 URL。调用方应把它保存到商品主图，签名过期前由前端展示/抓取。
        URL signed = client().generatePresignedUrl(properties.getBucket(), objectKey,
                new java.util.Date(System.currentTimeMillis() + properties.getUrlExpireSeconds() * 1000));
        // SDK 按 endpoint 生成签名 URL，默认可能是 http；统一提升为 https，避免混合内容被浏览器拦截
        return signed.toString().replaceFirst("^http://", "https://");
    }

    private static String normalizeEndpoint(String endpoint) {
        String value = endpoint.trim();
        value = value.replaceFirst("^https?://", "");
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static ImageType detect(MultipartFile file) {
        byte[] header = new byte[12];
        int read;
        try (InputStream input = file.getInputStream()) {
            read = input.read(header);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "读取上传文件失败");
        }
        if (read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        if (read >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return ImageType.PNG;
        }
        if (read >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F') {
            return ImageType.GIF;
        }
        if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return ImageType.WEBP;
        }
        throw new BusinessException(ResultCode.UPLOAD_FILE_TYPE_UNSUPPORTED);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }

    private enum ImageType {
        JPEG("jpg", "image/jpeg"), PNG("png", "image/png"), GIF("gif", "image/gif"), WEBP("webp", "image/webp");

        private final String extension;
        private final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        String extension() { return extension; }

        String contentType() { return contentType; }
    }
}
