package com.zhe.mall.product.service;

import com.zhe.mall.api.dto.BrandSaveRequest;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.product.entity.Brand;
import com.zhe.mall.product.mapper.BrandMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 品牌维护（管理端，M3）。
 *
 * <p>名称**全局唯一**（对齐唯一键 {@code uk_name}）；删除前挡"被商品引用"，
 * 与 {@link CategoryService#delete(Long)} 同一条「决策 5」。
 */
@Service
public class BrandService {

    private static final int STATUS_ENABLED = 1;

    private final BrandMapper brandMapper;

    public BrandService(BrandMapper brandMapper) {
        this.brandMapper = brandMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(BrandSaveRequest request) {
        if (brandMapper.countByNameExcluding(request.name(), null) > 0) {
            throw new BusinessException(ResultCode.BRAND_NAME_DUPLICATED, "品牌名称已存在: " + request.name());
        }
        Brand brand = new Brand();
        brand.setName(request.name());
        brand.setLogoUrl(request.logoUrl());
        brand.setDescription(request.description());
        brand.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        brand.setStatus(request.status() == null ? STATUS_ENABLED : request.status());
        brandMapper.insert(brand);
        return brand.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long brandId, BrandSaveRequest request) {
        Brand existing = requireBrand(brandId);
        if (brandMapper.countByNameExcluding(request.name(), brandId) > 0) {
            throw new BusinessException(ResultCode.BRAND_NAME_DUPLICATED, "品牌名称已存在: " + request.name());
        }
        Brand toSave = new Brand();
        toSave.setId(brandId);
        toSave.setName(request.name());
        toSave.setLogoUrl(request.logoUrl());
        toSave.setDescription(request.description());
        toSave.setSortNo(request.sortNo() == null ? existing.getSortNo() : request.sortNo());
        toSave.setStatus(request.status() == null ? existing.getStatus() : request.status());
        brandMapper.update(toSave);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long brandId) {
        requireBrand(brandId);
        int referenced = brandMapper.countProductsByBrandId(brandId);
        if (referenced > 0) {
            throw new BusinessException(ResultCode.BRAND_IN_USE, "该品牌下还有 " + referenced + " 个商品，无法删除");
        }
        brandMapper.softDelete(brandId);
    }

    private Brand requireBrand(Long brandId) {
        Brand brand = brandId == null ? null : brandMapper.selectById(brandId);
        if (brand == null) {
            throw new BusinessException(ResultCode.BRAND_NOT_FOUND, "品牌不存在: " + brandId);
        }
        return brand;
    }
}