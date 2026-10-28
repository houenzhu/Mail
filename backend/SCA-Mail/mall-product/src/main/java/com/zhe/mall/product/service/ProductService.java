package com.zhe.mall.product.service;

import com.zhe.mall.api.InventoryApi;
import com.zhe.mall.api.dto.AdminProductPageQuery;
import com.zhe.mall.api.dto.AdminProductStatusCounts;
import com.zhe.mall.api.dto.BrandDTO;
import com.zhe.mall.api.dto.CategoryDTO;
import com.zhe.mall.api.dto.InitStockRequest;
import com.zhe.mall.api.dto.ProductDTO;
import com.zhe.mall.api.dto.ProductSaveRequest;
import com.zhe.mall.api.dto.SkuDTO;
import com.zhe.mall.api.dto.SkuSaveRequest;
import com.zhe.mall.common.cache.MallCache;
import com.zhe.mall.common.cache.MallCacheKeys;
import com.zhe.mall.common.exception.BusinessException;
import com.zhe.mall.common.result.PageResult;
import com.zhe.mall.common.result.Result;
import com.zhe.mall.common.result.ResultCode;
import com.zhe.mall.product.dto.ProductDetailVO;
import com.zhe.mall.product.dto.ProductPageQuery;
import com.zhe.mall.product.entity.Product;
import com.zhe.mall.product.entity.ProductSku;
import com.zhe.mall.product.mapper.BrandMapper;
import com.zhe.mall.product.mapper.CategoryMapper;
import com.zhe.mall.product.mapper.ProductMapper;
import com.zhe.mall.product.mapper.ProductSkuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final int STATUS_OFF_SHELF = 0;

    private static final int STATUS_ON_SHELF = 1;

    /** 时间统一格式：与需求文档 10.2 的口径一致（服务端时间为准，BR-20）。 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductMapper productMapper;

    private final ProductSkuMapper productSkuMapper;

    private final CategoryMapper categoryMapper;

    private final BrandMapper brandMapper;

    private final InventoryApi inventoryApi;

    private final MallCache mallCache;

    public ProductService(ProductMapper productMapper, ProductSkuMapper productSkuMapper,
                          CategoryMapper categoryMapper, BrandMapper brandMapper,
                          InventoryApi inventoryApi, MallCache mallCache) {
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
        this.inventoryApi = inventoryApi;
        this.mallCache = mallCache;
    }

    /**
     * 商品详情：与 {@link #detailWithSkus(Long)} 共用同一个缓存 key（19.2 说明），只取其中的商品部分。
     */
    public ProductDTO detail(Long productId) {
        return detailWithSkus(productId).product();
    }

    /**
     * 商品详情（含 SKU 列表）：Cache-Aside + 空值防穿透 + 互斥重建 + TTL 抖动，见 27.4。
     */
    public ProductDetailVO detailWithSkus(Long productId) {
        if (productId == null || productId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "非法的商品 ID: " + productId);
        }
        ProductDetailVO cached = mallCache.getOrLoad(MallCacheKeys.productDetail(productId),
                ProductDetailVO.class, () -> loadDetail(productId));
        if (cached == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "商品不存在: " + productId);
        }
        return cached;
    }

    /** 回源：返回 null 表示数据不存在，由缓存层写成空值标记（防穿透）。 */
    private ProductDetailVO loadDetail(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return null;
        }
        List<SkuDTO> skus = productSkuMapper.selectByProductId(productId).stream().map(this::toSkuDTO).toList();
        return new ProductDetailVO(toProductDTO(product), skus);
    }

    public PageResult<ProductDTO> page(ProductPageQuery query) {
        long total = productMapper.countPage(query);
        if (total == 0) {
            return PageResult.empty(query.pageNo(), query.pageSize());
        }
        List<ProductDTO> list = productMapper.selectPage(query, query.offset(), query.pageSize())
                .stream().map(this::toProductDTO).toList();
        return PageResult.of(total, query.pageNo(), query.pageSize(), list);
    }

    public SkuDTO sku(Long skuId) {
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ResultCode.SKU_NOT_FOUND, "SKU 不存在: " + skuId);
        }
        return toSkuDTO(sku);
    }

    /* ---------------- 管理端（mall-admin 调用，见计划文档 G-02 / G-08） ---------------- */

    /**
     * 管理端商品分页：支持按已删除态与商品 ID 精确查，关键字同时匹配名称与卖点。
     *
     * <p>与 C 端 {@link #page(ProductPageQuery)} 共用同一条 SQL，只是条件不同——
     * 避免维护两份近乎重复的查询（字段一旦不同步就会出现"后台看得到、C 端看不到"这类怪现象）。
     */
    public PageResult<ProductDTO> adminPage(AdminProductPageQuery query) {
        ProductPageQuery condition = new ProductPageQuery();
        condition.setKeyword(query.keyword());
        condition.setProductId(query.productId());
        condition.setCategoryId(query.categoryId());
        condition.setBrandId(query.brandId());
        condition.setStatus(query.status());
        condition.setDeleted(query.deleted() == null ? 0 : query.deleted());
        condition.setPageNo(query.safePageNo());
        condition.setPageSize(query.safePageSize());

        long total = productMapper.countPage(condition);
        if (total == 0) {
            return PageResult.empty(condition.pageNo(), condition.pageSize());
        }
        List<ProductDTO> list = productMapper
                .selectPage(condition, condition.offset(), condition.pageSize())
                .stream().map(this::toProductDTO).toList();
        return PageResult.of(total, condition.pageNo(), condition.pageSize(), list);
    }

    /**
     * 管理端商品列表的 Tab 角标：一次返回各状态数量。
     *
     * <p>做成一个接口而不是让前端对每个状态各发一次分页请求，是为了避免
     * "打开列表就发多个 count 查询"这种放大（计划文档 8.1）。
     * 未删除商品的按状态分组 + 已删除总数，共两次轻量 group by。
     */
    public AdminProductStatusCounts statusCounts() {
        Map<String, Long> alive = countByStatus(0);
        long deleted = countByStatus(1).getOrDefault("all", 0L);
        return new AdminProductStatusCounts(
                alive.getOrDefault("all", 0L),
                alive.getOrDefault("on", 0L),
                alive.getOrDefault("off", 0L),
                deleted);
    }

    /** 指定 deleted 标记下的 {all,on,off} 计数。 */
    private Map<String, Long> countByStatus(int deleted) {
        long all = 0L;
        long on = 0L;
        long off = 0L;
        for (Map<String, Object> row : productMapper.countGroupByStatus(deleted)) {
            Object statusValue = row.get("status");
            Object countValue = row.get("cnt");
            if (statusValue == null || countValue == null) {
                continue;
            }
            long count = ((Number) countValue).longValue();
            all += count;
            int status = ((Number) statusValue).intValue();
            if (status == STATUS_ON_SHELF) {
                on += count;
            } else if (status == STATUS_OFF_SHELF) {
                off += count;
            }
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", all);
        counts.put("on", on);
        counts.put("off", off);
        return counts;
    }

    public List<CategoryDTO> listCategories() {
        return categoryMapper.selectAll().stream()
                .map(item -> new CategoryDTO(item.getId(), item.getParentId(), item.getName(),
                        item.getLevel(), item.getSortNo(), item.getStatus()))
                .toList();
    }

    public List<BrandDTO> listBrands() {
        return brandMapper.selectAll().stream()
                .map(item -> new BrandDTO(item.getId(), item.getName(), item.getLogoUrl(),
                        item.getSortNo(), item.getStatus()))
                .toList();
    }

    /**
     * 按商品 ID 批量取 SKU，按商品分组。
     *
     * <p>一次查完整页商品的 SKU：管理端列表既要用它算 SKU 数量，也要用它拿到 SKU ID 去批量查库存，
     * 逐个商品查会变成 N+1（见计划文档 8.2）。
     */
    public Map<Long, List<SkuDTO>> skusByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return productSkuMapper.selectByProductIds(distinct).stream()
                .map(this::toSkuDTO)
                .collect(Collectors.groupingBy(SkuDTO::productId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ProductSaveRequest request) {
        for (SkuSaveRequest sku : request.skus()) {
            if (productSkuMapper.countBySkuCode(sku.skuCode()) > 0) {
                throw new BusinessException(ResultCode.SKU_CODE_DUPLICATED, "SKU 编码已存在: " + sku.skuCode());
            }
        }

        Product product = new Product();
        product.setName(request.name());
        product.setSubtitle(request.subtitle());
        product.setCategoryId(request.categoryId());
        product.setBrandId(request.brandId() == null ? 0L : request.brandId());
        product.setMainImage(request.mainImage());
        product.setDetail(request.detail());
        product.setStatus(request.status() == null ? STATUS_OFF_SHELF : request.status());
        product.setSales(0);
        product.setDeleted(0);
        product.setPriceMin(priceOf(request.skus(), true));
        product.setPriceMax(priceOf(request.skus(), false));
        productMapper.insert(product);

        List<ProductSku> skus = new ArrayList<>();
        for (SkuSaveRequest skuRequest : request.skus()) {
            ProductSku sku = new ProductSku();
            sku.setProductId(product.getId());
            sku.setSkuCode(skuRequest.skuCode());
            sku.setName(skuRequest.name());
            sku.setSpecJson(skuRequest.specJson());
            sku.setPrice(skuRequest.price());
            sku.setMarketPrice(skuRequest.marketPrice() == null ? skuRequest.price() : skuRequest.marketPrice());
            sku.setImage(skuRequest.image());
            sku.setStatus(STATUS_ON_SHELF);
            sku.setDeleted(0);
            skus.add(sku);
        }
        productSkuMapper.insertBatch(skus);

        for (int i = 0; i < skus.size(); i++) {
            Integer stock = request.skus().get(i).stock();
            if (stock != null && stock > 0) {
                initStock(skus.get(i).getId(), stock);
            }
        }
        evictProductCacheAfterCommit(product.getId());
        return product.getId();
    }

    /**
     * 编辑商品（管理端）：SKU 按**差量**处理，而不是"软删全部再重插"。
     *
     * <p>为什么必须差量：{@code inventory.sku_id} 与订单明细都引用 SKU 主键，
     * 整表重建会让库存与历史订单全部对不上（见 M3 计划「决策 2」）。
     * <ul>
     *   <li>{@code skuId} 非空且属于本商品 → {@code updateBatch} 原地更新（主键不变，库存自然保留）；</li>
     *   <li>{@code skuId} 为空 → 新增（{@code stock > 0} 时同步初始化库存）；</li>
     *   <li>库中存在但请求里没有 → {@code softDeleteByIds} 逻辑删除。</li>
     * </ul>
     *
     * <p>请求 DTO 带 {@code @NotEmpty}，因此"请求里一个 SKU 都没有"这一步已被参数校验挡住，
     * 编辑后必然至少剩一个未删除 SKU，无需在此重复判空。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long productId, ProductSaveRequest request) {
        Product product = requireProduct(productId);

        Map<Long, ProductSku> existingById = productSkuMapper.selectByProductId(productId).stream()
                .collect(Collectors.toMap(ProductSku::getId, sku -> sku));

        List<SkuSaveRequest> requested = request.skus();
        for (SkuSaveRequest sku : requested) {
            if (sku.skuId() != null && !existingById.containsKey(sku.skuId())) {
                throw new BusinessException(ResultCode.SKU_NOT_FOUND, "SKU 不属于该商品: " + sku.skuId());
            }
        }

        // 编码唯一：已有 SKU 排除自身（否则"没改编码"会被误报重复）；同一请求内也不允许重复。
        // 新增 SKU 传 null 作为排除项 → 退化为全局查重。
        Set<String> seenCodes = new HashSet<>();
        for (SkuSaveRequest sku : requested) {
            if (!seenCodes.add(sku.skuCode())) {
                throw new BusinessException(ResultCode.SKU_CODE_DUPLICATED, "SKU 编码在本次提交中重复: " + sku.skuCode());
            }
            if (productSkuMapper.countBySkuCodeExcluding(sku.skuCode(), sku.skuId()) > 0) {
                throw new BusinessException(ResultCode.SKU_CODE_DUPLICATED, "SKU 编码已存在: " + sku.skuCode());
            }
        }

        List<SkuSaveRequest> toUpdate = requested.stream().filter(sku -> sku.skuId() != null).toList();
        List<SkuSaveRequest> toCreate = requested.stream().filter(sku -> sku.skuId() == null).toList();
        Set<Long> keptIds = toUpdate.stream().map(SkuSaveRequest::skuId).collect(Collectors.toSet());
        List<Long> toDeleteIds = existingById.keySet().stream().filter(id -> !keptIds.contains(id)).toList();

        if (!toUpdate.isEmpty()) {
            productSkuMapper.updateBatch(toUpdate.stream().map(sku -> toSkuEntity(
                    sku, productId, sku.skuId())).toList());
        }
        if (!toCreate.isEmpty()) {
            List<ProductSku> created = toCreate.stream()
                    .map(sku -> toSkuEntity(sku, productId, null))
                    .toList();
            productSkuMapper.insertBatch(created);
            // 初始库存只对**新增** SKU 生效：已有 SKU 的库存统一走库存管理页（写流水，可审计）
            for (int i = 0; i < created.size(); i++) {
                Integer stock = toCreate.get(i).stock();
                if (stock != null && stock > 0) {
                    initStock(created.get(i).getId(), stock);
                }
            }
        }
        if (!toDeleteIds.isEmpty()) {
            productSkuMapper.softDeleteByIds(toDeleteIds);
        }

        Product toSave = new Product();
        toSave.setId(productId);
        toSave.setName(request.name());
        toSave.setSubtitle(request.subtitle());
        toSave.setCategoryId(request.categoryId());
        toSave.setBrandId(request.brandId() == null ? 0L : request.brandId());
        toSave.setMainImage(request.mainImage());
        toSave.setDetail(request.detail());
        // 编辑页不提交 status 时保持原状态，避免"改个名字被顺手下架"
        toSave.setStatus(request.status() == null ? product.getStatus() : request.status());
        productMapper.update(toSave);
        productMapper.updatePriceRange(productId, priceOf(requested, true), priceOf(requested, false));

        evictProductCacheAfterCommit(productId);
    }

    /** 按 SKU ID 批量取 SKU（管理端库存流水补 SKU 编码/名称用）。 */
    public List<SkuDTO> skusByIds(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = skuIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        return productSkuMapper.selectBySkuIds(distinct).stream().map(this::toSkuDTO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long productId, Integer status) {
        if (status == null || (status != STATUS_OFF_SHELF && status != STATUS_ON_SHELF)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "非法的商品状态: " + status);
        }
        Product product = requireProduct(productId);
        if (status.equals(product.getStatus())) {
            return;
        }
        productMapper.updateStatus(productId, status);
        evictProductCacheAfterCommit(productId);
    }

    /**
     * 逻辑删除（管理端）：只置 deleted，保留数据以便追溯。
     *
     * <p>{@code selectById} 与 {@code softDelete} 都带 {@code deleted = 0} 条件，
     * 因此重复删除会得到"商品不存在"而不是静默成功——与列表口径一致（见 ProductMapper.xml）。
     * 删除后必须失效详情缓存，否则已删商品还会在 TTL 内从缓存命中（27.4）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long productId) {
        requireProduct(productId);
        productMapper.softDelete(productId);
        evictProductCacheAfterCommit(productId);
    }

    /**
     * 写路径失效缓存（27.4）：在事务**提交后**删除，避免回滚时白删一次；
     * 删除失败只告警，靠 TTL 自然过期兜底（12.20）。
     */
    private void evictProductCacheAfterCommit(Long productId) {
        Runnable evict = () -> {
            mallCache.evict(MallCacheKeys.productDetail(productId));
            mallCache.evictNull(MallCacheKeys.productDetail(productId));
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }

    private void initStock(Long skuId, Integer stock) {
        Result<Void> result = inventoryApi.init(new InitStockRequest(skuId, stock));
        if (result == null || !result.isSuccess()) {
            String message = result == null ? "库存服务无响应" : result.message();
            throw new BusinessException(ResultCode.DOWNSTREAM_UNAVAILABLE, "初始化库存失败: " + message);
        }
    }

    private Product requireProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "商品不存在: " + productId);
        }
        return product;
    }

    private BigDecimal priceOf(List<SkuSaveRequest> skus, boolean min) {
        return skus.stream()
                .map(SkuSaveRequest::price)
                .reduce((left, right) -> min ? left.min(right) : left.max(right))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 请求 SKU → 实体。
     *
     * <p>{@code skuId} 为空时用于 insert（{@code insertBatch} 回填主键），
     * 非空时用于 update（按主键原地更新，主键不变）。
     */
    private ProductSku toSkuEntity(SkuSaveRequest request, Long productId, Long skuId) {
        ProductSku sku = new ProductSku();
        sku.setId(skuId);
        sku.setProductId(productId);
        sku.setSkuCode(request.skuCode());
        sku.setName(request.name());
        sku.setSpecJson(request.specJson());
        sku.setPrice(request.price());
        sku.setMarketPrice(request.marketPrice() == null ? request.price() : request.marketPrice());
        sku.setImage(request.image());
        sku.setStatus(STATUS_ON_SHELF);
        sku.setDeleted(0);
        return sku;
    }

    private ProductDTO toProductDTO(Product product) {
        return new ProductDTO(product.getId(), product.getName(), product.getSubtitle(), product.getMainImage(),
                product.getPriceMin(), product.getPriceMax(), product.getStatus(),
                product.getCategoryId(), product.getBrandId(), product.getSales(),
                formatDateTime(product.getCreatedAt()), formatDateTime(product.getUpdatedAt()));
    }

    /** 统一时间格式为 yyyy-MM-dd HH:mm:ss（对齐需求文档 10.2 的时间口径）。 */
    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    private SkuDTO toSkuDTO(ProductSku sku) {
        return new SkuDTO(sku.getId(), sku.getProductId(), sku.getSkuCode(), sku.getName(),
                sku.getSpecJson(), sku.getPrice(), sku.getMarketPrice(), sku.getImage(), sku.getStatus());
    }
}
