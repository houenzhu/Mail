<script setup>
/**
 * 发布 / 编辑商品。
 *
 * 与后端的契约（mall-admin BFF，见 M3 计划）：
 *  - POST /api/admin/product       新增（含 SKU 与库存初始化）
 *  - GET  /api/admin/product/{id}  编辑回显（商品 + 真实 SKU 列表）
 *  - PUT  /api/admin/product/{id}  编辑（SKU **差量**提交：带 skuId 的更新、不带的新增、未出现的软删）
 *
 * ⚠️ 两条容易踩的规则：
 *  1. 编辑时**未出现在表单里的 SKU 会被软删**，所以回显必须拿真实 SKU，不能是空列表；
 *  2. 已有 SKU 的库存不在这里改（改了也不生效）——统一走「库存管理」并写流水，可审计、可对账。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import {
  createProduct,
  getProductDetail,
  listBrands,
  listCategories,
  updateProduct,
} from '@/api/product'
import { formatMoney } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const formRef = ref()
const loading = ref(false)
const submitting = ref(false)
const categories = ref([])
const brands = ref([])

const productId = computed(() => route.params.id)
const isEdit = computed(() => Boolean(productId.value))

const form = reactive({
  name: '',
  subtitle: '',
  categoryId: undefined,
  brandId: undefined,
  mainImage: '',
  skus: [emptySku()],
})

const rules = {
  name: [
    { required: true, message: '请输入商品名称', trigger: 'blur' },
    { max: 128, message: '商品名称不超过 128 字', trigger: 'blur' },
  ],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  mainImage: [{ max: 255, message: '图片地址不超过 255 字', trigger: 'blur' }],
}

/** 新增一行 SKU：不带 skuId，提交时会被识别为"新增" */
function emptySku() {
  return { skuId: null, skuCode: '', name: '', price: undefined, marketPrice: undefined, stock: 0 }
}

const totalPrice = computed(() =>
  form.skus.reduce((sum, sku) => sum + (Number(sku.price) || 0), 0),
)

onMounted(async () => {
  try {
    const [cats, brs] = await Promise.all([listCategories(), listBrands()])
    categories.value = cats ?? []
    brands.value = brs ?? []
  } catch {
    // 字典失败不阻塞页面（分类下拉为空时表单校验会拦住提交）
  }
  if (isEdit.value) await loadProduct()
})

async function loadProduct() {
  loading.value = true
  try {
    const detail = await getProductDetail(productId.value)
    const product = detail?.product
    if (!product) {
      ElMessage.error('商品不存在或已删除')
      return
    }
    Object.assign(form, {
      name: product.name,
      subtitle: product.subtitle ?? '',
      categoryId: product.categoryId,
      brandId: product.brandId || undefined,
      mainImage: product.mainImage ?? '',
    })
    const skus = detail.skus ?? []
    // 回显真实 SKU，并保留 skuId：它是"更新而不是新增 + 软删旧行"的关键
    form.skus = skus.length
      ? skus.map((sku) => ({
          skuId: sku.id,
          skuCode: sku.skuCode,
          name: sku.name,
          price: sku.price,
          marketPrice: sku.marketPrice,
          stock: undefined,
        }))
      : [emptySku()]
  } catch (e) {
    if (!e?.code) ElMessage.error('加载商品失败')
  } finally {
    loading.value = false
  }
}

function addSku() {
  form.skus.push(emptySku())
}

function removeSku(index) {
  if (form.skus.length === 1) {
    ElMessage.warning('至少保留一个 SKU')
    return
  }
  form.skus.splice(index, 1)
}

/** 表单 → 请求体：字段名与后端 ProductSaveRequest / SkuSaveRequest 一一对应 */
function buildPayload() {
  return {
    name: form.name,
    subtitle: form.subtitle || null,
    categoryId: form.categoryId,
    brandId: form.brandId ?? null,
    mainImage: form.mainImage || null,
    skus: form.skus.map((sku) => ({
      skuId: sku.skuId ?? null,
      skuCode: sku.skuCode,
      name: sku.name,
      price: sku.price,
      marketPrice: sku.marketPrice ?? null,
      // 只对新增 SKU 有意义；已有 SKU 传 null，后端不会改动其库存
      stock: sku.skuId ? null : sku.stock ?? 0,
    })),
  }
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  // 前端只做基础校验；唯一性（SKU 编码）与库存初始化由后端判定，
  // 对应错误码 32004 / 下游失败，文案直接用后端 message。
  const badSku = form.skus.find(
    (sku) => !sku.skuCode || !sku.name || sku.price === undefined || sku.price === null,
  )
  if (badSku) {
    ElMessage.warning('请补全每个 SKU 的编码、名称与售价')
    return
  }

  submitting.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value) {
      await updateProduct(productId.value, payload)
    } else {
      await createProduct(payload)
    }
    ElMessage.success(isEdit.value ? '保存成功' : '发布成功')
    router.push({ name: 'product-list' })
  } catch (e) {
    // 业务错误（编码重复、库存初始化失败等）已由请求层统一提示，这里只兜底网络类异常
    if (!e?.code) ElMessage.error('提交失败，请检查后端服务')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-card">
      <div class="page-toolbar">
        <h2 class="page-title">{{ isEdit ? '编辑商品' : '发布商品' }}</h2>
        <div>
          <el-button @click="router.back()">返回</el-button>
          <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="product-form">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入商品名称" maxlength="128" show-word-limit />
        </el-form-item>

        <el-form-item label="卖点副标题">
          <el-input v-model="form.subtitle" placeholder="如：卫星通话 · 昆仑玻璃" maxlength="255" />
        </el-form-item>

        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 240px">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <span class="text-muted text-xs hint">分类可在「分类与品牌」中维护</span>
        </el-form-item>

        <el-form-item label="品牌">
          <el-select v-model="form.brandId" placeholder="可不选" clearable style="width: 240px">
            <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="主图地址" prop="mainImage">
          <el-input v-model="form.mainImage" placeholder="图片 URL（文件上传接口待建，先填外链）" />
          <span class="text-muted text-xs hint">后端暂无上传接口（G-09），当前只支持填 URL</span>
        </el-form-item>

        <el-divider content-position="left">SKU 与库存</el-divider>

        <el-table :data="form.skus" border size="small" class="sku-table">
          <el-table-column label="SKU 编码" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.skuCode" size="small" placeholder="业务唯一，如 SKU-1-1" />
            </template>
          </el-table-column>
          <el-table-column label="规格名称" min-width="130">
            <template #default="{ row }">
              <el-input v-model="row.name" size="small" placeholder="如 默认规格" />
            </template>
          </el-table-column>
          <el-table-column label="售价" width="130">
            <template #default="{ row }">
              <el-input-number
                v-model="row.price"
                size="small"
                :min="0"
                :precision="2"
                :step="1"
                controls-position="right"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="划线价" width="130">
            <template #default="{ row }">
              <el-input-number
                v-model="row.marketPrice"
                size="small"
                :min="0"
                :precision="2"
                :step="1"
                controls-position="right"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="库存" width="180">
            <template #default="{ row }">
              <!-- 已有 SKU 的库存不在这里改：统一走库存管理（写流水，可审计） -->
              <span v-if="row.skuId" class="text-muted text-xs">请在「库存管理」中调整</span>
              <el-input-number
                v-else
                v-model="row.stock"
                size="small"
                :min="0"
                :precision="0"
                controls-position="right"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="removeSku($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="sku-foot">
          <el-button size="small" @click="addSku">
            <el-icon><Plus /></el-icon>添加 SKU
          </el-button>
          <span class="text-muted text-xs">
            共 {{ form.skus.length }} 个 SKU · 售价合计 {{ formatMoney(totalPrice) }}
          </span>
        </div>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="tip"
          title="库存由 mall-inventory 初始化"
          description="新增商品时会同步调用库存服务初始化库存，任一步失败即整单失败（BR-02）。编辑时已有 SKU 的库存不变，改动只对新增 SKU 生效。价格与金额统一两位小数（BR-01）。"
        />
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.tip {
  margin-bottom: 14px;
}

.product-form {
  max-width: 1000px;
}

.hint {
  margin-left: 10px;
}

.sku-table {
  margin-bottom: 10px;
}

.sku-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
</style>