package com.zhe.mall.admin.upload;

import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 上传入口的业务边界：文件为空、超限、伪装图片都必须在上传 OSS 之前被拒绝。 */
class OssUploadServiceTest {

    private OssProperties properties() {
        OssProperties properties = new OssProperties();
        properties.setEnabled(true);
        properties.setEndpoint("oss-cn-guangzhou.aliyuncs.com");
        properties.setBucket("zheliving-10000");
        properties.setMaxFileSize(1024);
        return properties;
    }

    @Test
    @DisplayName("空文件拒绝")
    void rejectsEmptyFile() {
        OssUploadService service = new OssUploadService(properties());

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "a.png", "image/png", new byte[0])))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.UPLOAD_FILE_EMPTY.getCode());
    }

    @Test
    @DisplayName("超过大小限制拒绝")
    void rejectsTooLargeFile() {
        OssUploadService service = new OssUploadService(properties());
        byte[] png = pngBytes(2000);

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "a.png", "image/png", png)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.UPLOAD_FILE_TOO_LARGE.getCode());
    }

    @Test
    @DisplayName("伪装成图片的文本按文件头拒绝")
    void rejectsFakeImageByMagicNumber() {
        OssUploadService service = new OssUploadService(properties());

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "a.png", "image/png",
                "not-an-image".getBytes())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.UPLOAD_FILE_TYPE_UNSUPPORTED.getCode());
    }

    @Test
    @DisplayName("未配置 OSS 时返回可读的存储不可用，而不是启动失败")
    void rejectsWhenStorageNotConfigured() {
        OssProperties properties = properties();
        properties.setEnabled(false);
        OssUploadService service = new OssUploadService(properties);

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "a.png", "image/png", pngBytes(20))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.UPLOAD_STORAGE_UNAVAILABLE.getCode());
    }

    @Test
    @DisplayName("PNG 文件头可识别（不依赖扩展名）")
    void detectsPngByMagicNumber() {
        OssProperties properties = properties();
        properties.setMaxFileSize(1024 * 1024);
        OssUploadService service = new OssUploadService(properties);

        // 到这里已经通过类型校验，因未配置环境密钥，下一步会抛存储不可用；
        // 这证明文件头识别通过，而不是被 38003 拒绝。
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "a.txt", "text/plain", pngBytes(20))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.UPLOAD_STORAGE_UNAVAILABLE.getCode());
    }

    @Test
    @DisplayName("公共读桶返回不带签名的稳定 URL")
    void buildsStableUrlForPublicBucket() {
        OssProperties properties = properties();
        properties.setPublicRead(true);
        OssUploadService service = new OssUploadService(properties);

        assertThat(service.buildUrl("product/2026/11/05/a.png"))
                .isEqualTo("https://zheliving-10000.oss-cn-guangzhou.aliyuncs.com/product/2026/11/05/a.png");
    }

    @Test
    @DisplayName("自定义访问域名会规范尾部斜杠")
    void usesCustomPublicBaseUrl() {
        OssProperties properties = properties();
        properties.setPublicRead(true);
        properties.setPublicBaseUrl("https://cdn.example.com/");
        OssUploadService service = new OssUploadService(properties);

        assertThat(service.buildUrl("product/a.png")).isEqualTo("https://cdn.example.com/product/a.png");
    }

    private static byte[] pngBytes(int size) {
        byte[] bytes = new byte[Math.max(size, 8)];
        bytes[0] = (byte) 0x89;
        bytes[1] = 'P';
        bytes[2] = 'N';
        bytes[3] = 'G';
        bytes[4] = 0x0D;
        bytes[5] = 0x0A;
        bytes[6] = 0x1A;
        bytes[7] = 0x0A;
        return bytes;
    }
}
