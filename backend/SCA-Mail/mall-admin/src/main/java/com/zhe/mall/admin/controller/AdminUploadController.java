package com.zhe.mall.admin.controller;

import com.zhe.mall.admin.upload.OssUploadService;
import com.zhe.mall.admin.upload.UploadResult;
import com.zhe.mall.common.auth.RequireStaff;
import com.zhe.mall.common.auth.UserRole;
import com.zhe.mall.common.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台商品图上传（G-09）。
 *
 * <p>只允许运营/管理员：客服可查看商品，但不应修改商品素材。
 * 真正的安全边界在服务端角色校验；前端隐藏上传按钮只是体验。
 */
@RestController
@RequestMapping("/api/admin/upload")
@RequireStaff(UserRole.OPERATOR)
public class AdminUploadController {

    private final OssUploadService ossUploadService;

    public AdminUploadController(OssUploadService ossUploadService) {
        this.ossUploadService = ossUploadService;
    }

    @PostMapping
    public Result<UploadResult> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(ossUploadService.upload(file));
    }
}