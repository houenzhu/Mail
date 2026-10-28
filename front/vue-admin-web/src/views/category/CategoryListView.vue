<script setup>
/**
 * 分类与品牌管理（发布商品的前置字典）。
 *
 * 后端契约（mall-admin BFF → mall-product，见 M3 计划）：
 *  - 分类：POST/PUT/DELETE /api/admin/category[/{id}]，同级同名会被拒（32006），
 *          有子分类或有商品引用时不可删（32007）；
 *  - 品牌：POST/PUT/DELETE /api/admin/brand[/{id}]，全局同名会被拒（32009），
 *          有商品引用时不可删（32010）。
 *  层级（level）由后端按父分类推导，前端不提交。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  createBrand,
  createCategory,
  deleteBrand,
  deleteCategory,
  listBrands,
  listCategories,
  updateBrand,
  updateCategory,
} from '@/api/product'

const categories = ref([])
const brands = ref([])
const loading = ref(false)
const submitting = ref(false)

const dialogVisible = ref(false)
/** 'category' | 'brand' */
const dialogType = ref('category')
/** null 表示新增 */
const editingId = ref(null)
const dialogTitle = computed(() => {
  const noun = dialogType.value === 'category' ? '分类' : '品牌'
  return editingId.value ? `编辑${noun}` : `新增${noun}`
})

const categoryForm = reactive({ parentId: 0, name: '', sortNo: 0, status: 1 })
const brandForm = reactive({ name: '', logoUrl: '', description: '', sortNo: 0, status: 1 })

/** 可选的父分类：仅 1/2 级（3 级已是末级），且排除自身 */
const parentOptions = computed(() =>
  categories.value.filter((c) => c.level < 3 && c.id !== editingId.value),
)

async function load() {
  loading.value = true
  try {
    const [cats, brs] = await Promise.all([listCategories(), listBrands()])
    categories.value = cats ?? []
    brands.value = brs ?? []
  } catch {
    // 失败提示由请求层统一处理
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate(type) {
  dialogType.value = type
  editingId.value = null
  if (type === 'category') {
    Object.assign(categoryForm, { parentId: 0, name: '', sortNo: 0, status: 1 })
  } else {
    Object.assign(brandForm, { name: '', logoUrl: '', description: '', sortNo: 0, status: 1 })
  }
  dialogVisible.value = true
}

function openEditCategory(row) {
  dialogType.value = 'category'
  editingId.value = row.id
  Object.assign(categoryForm, {
    parentId: row.parentId ?? 0,
    name: row.name,
    sortNo: row.sortNo ?? 0,
    status: row.status ?? 1,
  })
  dialogVisible.value = true
}

function openEditBrand(row) {
  dialogType.value = 'brand'
  editingId.value = row.id
  Object.assign(brandForm, {
    name: row.name,
    logoUrl: row.logoUrl ?? '',
    description: row.description ?? '',
    sortNo: row.sortNo ?? 0,
    status: row.status ?? 1,
  })
  dialogVisible.value = true
}

async function submit() {
  if (dialogType.value === 'category') {
    if (!categoryForm.name.trim()) {
      ElMessage.warning('请输入分类名称')
      return
    }
  } else if (!brandForm.name.trim()) {
    ElMessage.warning('请输入品牌名称')
    return
  }

  submitting.value = true
  try {
    if (dialogType.value === 'category') {
      const payload = {
        parentId: categoryForm.parentId || 0,
        name: categoryForm.name.trim(),
        sortNo: categoryForm.sortNo ?? 0,
        status: categoryForm.status ?? 1,
      }
      if (editingId.value) await updateCategory(editingId.value, payload)
      else await createCategory(payload)
    } else {
      const payload = {
        name: brandForm.name.trim(),
        logoUrl: brandForm.logoUrl || null,
        description: brandForm.description || null,
        sortNo: brandForm.sortNo ?? 0,
        status: brandForm.status ?? 1,
      }
      if (editingId.value) await updateBrand(editingId.value, payload)
      else await createBrand(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } catch {
    // 业务失败（同名/被引用等）文案由请求层统一提示，保持弹窗打开便于修改
  } finally {
    submitting.value = false
  }
}

/** 删除前二次确认；被引用时后端会拒绝（32007/32010），原因由请求层提示 */
async function confirmRemove(type, row) {
  const noun = type === 'category' ? '分类' : '品牌'
  try {
    await ElMessageBox.confirm(`确定删除${noun}「${row.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return // 用户取消
  }
  try {
    if (type === 'category') await deleteCategory(row.id)
    else await deleteBrand(row.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    // 失败原因（有子分类 / 有商品引用）由请求层统一提示
  }
}
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-toolbar">
        <h2 class="page-title">分类与品牌</h2>
        <span class="text-muted text-xs">发布商品的必填字典；层级由后端按父分类自动推导</span>
      </div>
    </div>

    <div class="two-col">
      <div class="page-card">
        <div class="section-head">
          <h3 class="section-title">分类</h3>
          <el-button type="primary" size="small" @click="openCreate('category')">
            <el-icon><Plus /></el-icon>新增分类
          </el-button>
        </div>
        <el-table v-loading="loading" :data="categories" border size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="分类名称" min-width="140" />
          <el-table-column prop="level" label="层级" width="70" align="center" />
          <el-table-column prop="sortNo" label="排序" width="70" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openEditCategory(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="confirmRemove('category', row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="page-card">
        <div class="section-head">
          <h3 class="section-title">品牌</h3>
          <el-button type="primary" size="small" @click="openCreate('brand')">
            <el-icon><Plus /></el-icon>新增品牌
          </el-button>
        </div>
        <el-table v-loading="loading" :data="brands" border size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="品牌名称" min-width="140" />
          <el-table-column prop="sortNo" label="排序" width="70" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openEditBrand(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="confirmRemove('brand', row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="460px">
      <el-form v-if="dialogType === 'category'" label-width="80px">
        <el-form-item label="父分类">
          <el-select v-model="categoryForm.parentId" style="width: 100%">
            <el-option label="顶级分类" :value="0" />
            <el-option v-for="c in parentOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="categoryForm.name" maxlength="64" placeholder="如 手机数码" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryForm.sortNo" :min="0" :precision="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="categoryForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>

      <el-form v-else label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="brandForm.name" maxlength="64" placeholder="如 华为" />
        </el-form-item>
        <el-form-item label="LOGO">
          <el-input v-model="brandForm.logoUrl" maxlength="255" placeholder="图片 URL" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="brandForm.description" maxlength="255" placeholder="选填" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="brandForm.sortNo" :min="0" :precision="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="brandForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 8px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.section-title {
  font-size: 14px;
  margin: 0;
}

@media (max-width: 1100px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>