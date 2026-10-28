<template>
  <div>
    <n-card :bordered="false">
      <n-alert type="info" :show-icon="false" style="margin-bottom: 12px">
        {{ t('mall.tip') }}
      </n-alert>
      <n-tabs v-model:value="activeTab" type="line" animated>
        <!-- 分类 -->
        <n-tab-pane name="category" :tab="t('mall.categoryTab')">
          <n-space justify="end" style="margin-bottom: 8px">
            <n-button type="primary" @click="openCategory()">{{ t('common.create') }}</n-button>
          </n-space>
          <n-data-table :columns="categoryColumns" :data="categories" :loading="loading" :row-key="(r: MallCategory) => r.id" />
        </n-tab-pane>

        <!-- 商品 -->
        <n-tab-pane name="product" :tab="t('mall.productTab')">
          <n-space justify="space-between" style="margin-bottom: 8px">
            <n-select
              v-model:value="productCategoryFilter"
              :options="categoryFilterOptions"
              :placeholder="t('mall.allCategories')"
              clearable
              style="width: 200px"
              @update:value="loadProducts"
            />
            <n-button type="primary" @click="openProduct()">{{ t('common.create') }}</n-button>
          </n-space>
          <n-data-table :columns="productColumns" :data="products" :loading="loading" :row-key="(r: MallProduct) => r.id!" :scroll-x="900" />
        </n-tab-pane>

        <!-- 优惠券 -->
        <n-tab-pane name="coupon" :tab="t('mall.couponTab')">
          <n-space justify="end" style="margin-bottom: 8px">
            <n-button type="primary" @click="openCoupon()">{{ t('common.create') }}</n-button>
          </n-space>
          <n-data-table :columns="couponColumns" :data="coupons" :loading="loading" :row-key="(r: MallCoupon) => r.id!" :scroll-x="900" />
        </n-tab-pane>

        <!-- 活动 -->
        <n-tab-pane name="activity" :tab="t('mall.activityTab')">
          <n-space justify="end" style="margin-bottom: 8px">
            <n-button type="primary" @click="openActivity()">{{ t('common.create') }}</n-button>
          </n-space>
          <n-data-table :columns="activityColumns" :data="activities" :loading="loading" :row-key="(r: MallActivity) => r.id!" :scroll-x="700" />
        </n-tab-pane>
      </n-tabs>
    </n-card>

    <!-- 分类编辑 -->
    <n-modal v-model:show="categoryVisible" preset="dialog" :title="t('mall.categoryEdit')" style="width: 520px">
      <n-form label-placement="left" label-width="100">
        <n-form-item :label="t('mall.categoryCode')" required>
          <n-input v-model:value="categoryForm.code" :disabled="!!categoryForm.id" :placeholder="t('mall.categoryCodePlaceholder')" />
        </n-form-item>
        <n-form-item :label="t('mall.categoryName')" required>
          <n-input v-model:value="categoryForm.name" />
        </n-form-item>
        <n-form-item :label="t('common.sort')">
          <n-input-number v-model:value="categoryForm.sort" style="width: 140px" />
        </n-form-item>
        <n-form-item :label="t('common.isEnable')">
          <n-switch v-model:value="categoryForm.isEnable" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="categoryVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="saving" @click="saveCategoryRow">{{ t('common.confirm') }}</n-button>
      </template>
    </n-modal>

    <!-- 商品编辑 -->
    <n-modal v-model:show="productVisible" preset="dialog" :title="t('mall.productEdit')" style="width: 620px">
      <n-form label-placement="left" label-width="100">
        <n-form-item :label="t('mall.productName')" required>
          <n-input v-model:value="productForm.name" />
        </n-form-item>
        <n-form-item :label="t('mall.categoryTab')" required>
          <n-select v-model:value="productForm.categoryCode" :options="categoryFilterOptions" />
        </n-form-item>
        <n-form-item :label="t('mall.price')" required>
          <n-input-number v-model:value="productForm.price" :min="0" :precision="2" style="width: 160px" />
        </n-form-item>
        <n-form-item :label="t('mall.originalPrice')">
          <n-input-number v-model:value="productForm.originalPrice" :min="0" :precision="2" style="width: 160px" />
        </n-form-item>
        <n-form-item :label="t('mall.monthlySales')">
          <n-input-number v-model:value="productForm.monthlySales" :min="0" style="width: 160px" />
        </n-form-item>
        <n-form-item :label="t('mall.promoTags')">
          <n-input v-model:value="productForm.promoTags" :placeholder="t('mall.promoTagsPlaceholder')" />
        </n-form-item>
        <n-form-item :label="t('mall.couponHint')">
          <n-input v-model:value="productForm.couponHint" />
        </n-form-item>
        <n-form-item :label="t('mall.remark')">
          <n-input v-model:value="productForm.remark" type="textarea" :rows="2" />
        </n-form-item>
        <n-form-item :label="t('common.isEnable')">
          <n-switch v-model:value="productForm.isEnable" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="productVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="saving" @click="saveProductRow">{{ t('common.confirm') }}</n-button>
      </template>
    </n-modal>

    <!-- 优惠券编辑 -->
    <n-modal v-model:show="couponVisible" preset="dialog" :title="t('mall.couponEdit')" style="width: 560px">
      <n-form label-placement="left" label-width="110">
        <n-form-item :label="t('mall.couponCode')" required>
          <n-input v-model:value="couponForm.code" :disabled="!!couponForm.id" :placeholder="t('mall.couponCodePlaceholder')" />
        </n-form-item>
        <n-form-item :label="t('mall.couponTitle')" required>
          <n-input v-model:value="couponForm.title" />
        </n-form-item>
        <n-form-item :label="t('mall.couponAmount')" required>
          <n-input-number v-model:value="couponForm.amount" :min="0" :precision="2" style="width: 160px" />
        </n-form-item>
        <n-form-item :label="t('mall.minSpend')">
          <n-input-number v-model:value="couponForm.minSpend" :min="0" :precision="2" style="width: 160px" />
        </n-form-item>
        <n-form-item :label="t('mall.totalCount')">
          <n-input-number v-model:value="couponForm.totalCount" :min="-1" style="width: 160px" />
          <span style="margin-left: 8px; color: #999">{{ t('mall.totalCountHint') }}</span>
        </n-form-item>
        <n-form-item :label="t('mall.expireTime')">
          <n-date-picker v-model:value="couponForm.expireTs" type="datetime" clearable style="width: 220px" />
        </n-form-item>
        <n-form-item :label="t('common.isEnable')">
          <n-switch v-model:value="couponForm.isEnable" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="couponVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="saving" @click="saveCouponRow">{{ t('common.confirm') }}</n-button>
      </template>
    </n-modal>

    <!-- 活动编辑 -->
    <n-modal v-model:show="activityVisible" preset="dialog" :title="t('mall.activityEdit')" style="width: 560px">
      <n-form label-placement="left" label-width="110">
        <n-form-item :label="t('mall.activityTitle')" required>
          <n-input v-model:value="activityForm.title" />
        </n-form-item>
        <n-form-item :label="t('mall.activityDesc')">
          <n-input v-model:value="activityForm.description" type="textarea" :rows="3" />
        </n-form-item>
        <n-form-item :label="t('common.sort')">
          <n-input-number v-model:value="activityForm.sort" style="width: 140px" />
        </n-form-item>
        <n-form-item :label="t('common.isEnable')">
          <n-switch v-model:value="activityForm.isEnable" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="activityVisible = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="saving" @click="saveActivityRow">{{ t('common.confirm') }}</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, reactive, ref } from 'vue'
  import { NSwitch, useDialog, useMessage } from 'naive-ui'
  import { TableAction } from '@/components/Table'
  import mallApi from '@/api/mall'
  import type { MallActivity, MallCategory, MallCoupon, MallProduct } from '@/api/mall'
  import { t } from '@/locales'

  const message = useMessage()
  const dialog = useDialog()

  const activeTab = ref('category')
  const loading = ref(false)
  const saving = ref(false)

  const categories = ref<MallCategory[]>([])
  const products = ref<MallProduct[]>([])
  const coupons = ref<MallCoupon[]>([])
  const activities = ref<MallActivity[]>([])
  const productCategoryFilter = ref<string | null>(null)

  const categoryFilterOptions = computed(() =>
    categories.value.map(c => ({ label: c.name, value: c.code })),
  )

  // ---------------- 加载 ----------------
  async function loadCategories() {
    const res = await mallApi.fetchCategories()
    categories.value = (res.data || []) as MallCategory[]
  }
  async function loadProducts() {
    const res = await mallApi.fetchProducts(productCategoryFilter.value || undefined)
    products.value = (res.data || []) as MallProduct[]
  }
  async function loadCoupons() {
    const res = await mallApi.fetchCoupons()
    coupons.value = (res.data || []) as MallCoupon[]
  }
  async function loadActivities() {
    const res = await mallApi.fetchActivities()
    activities.value = (res.data || []) as MallActivity[]
  }
  async function loadAll() {
    loading.value = true
    try {
      await Promise.all([loadCategories(), loadProducts(), loadCoupons(), loadActivities()])
    }
    catch (error) {
      console.error('loadMall error', error)
      message.error(t('common.operationFailed'))
    }
    finally {
      loading.value = false
    }
  }

  // ---------------- 分类 ----------------
  const categoryVisible = ref(false)
  const categoryForm = reactive<Required<Pick<MallCategory, 'code' | 'name'>> & { id?: number; sort: number; isEnable: boolean }>({
    id: undefined,
    code: '',
    name: '',
    sort: 0,
    isEnable: true,
  })
  function openCategory(row?: MallCategory) {
    Object.assign(categoryForm, {
      id: row?.id,
      code: row?.code || '',
      name: row?.name || '',
      sort: row?.sort ?? 0,
      isEnable: row?.isEnable !== false,
    })
    categoryVisible.value = true
  }
  async function saveCategoryRow() {
    if (!categoryForm.code.trim() || !categoryForm.name.trim()) {
      message.warning(t('common.fillCompleteInfo'))
      return
    }
    saving.value = true
    try {
      await mallApi.saveCategory({ ...categoryForm })
      message.success(t('common.operationSuccess'))
      categoryVisible.value = false
      await Promise.all([loadCategories(), loadProducts()])
    }
    finally {
      saving.value = false
    }
  }
  function doDeleteCategory(row: MallCategory) {
    dialog.warning({
      title: t('common.delete'),
      content: `${t('common.deleteConfirmPrefix')}「${row.name}」${t('common.deleteConfirmSuffix')}`,
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await mallApi.deleteCategory(row.id!)
        message.success(t('common.deleteSuccess'))
        await Promise.all([loadCategories(), loadProducts()])
      },
    })
  }

  // ---------------- 商品 ----------------
  const productVisible = ref(false)
  const productForm = reactive({
    id: undefined as number | undefined,
    name: '',
    categoryCode: '',
    price: 0,
    originalPrice: 0,
    monthlySales: 0,
    promoTags: '',
    couponHint: '',
    remark: '',
    isEnable: true,
  })
  function openProduct(row?: MallProduct) {
    Object.assign(productForm, {
      id: row?.id,
      name: row?.name || '',
      categoryCode: row?.categoryCode || '',
      price: row?.price ?? 0,
      originalPrice: row?.originalPrice ?? 0,
      monthlySales: row?.monthlySales ?? 0,
      promoTags: row?.promoTags || '',
      couponHint: row?.couponHint || '',
      remark: row?.remark || '',
      isEnable: row?.isEnable !== false,
    })
    productVisible.value = true
  }
  async function saveProductRow() {
    if (!productForm.name.trim() || !productForm.categoryCode) {
      message.warning(t('common.fillCompleteInfo'))
      return
    }
    saving.value = true
    try {
      await mallApi.saveProduct({ ...productForm })
      message.success(t('common.operationSuccess'))
      productVisible.value = false
      await loadProducts()
    }
    finally {
      saving.value = false
    }
  }
  function doDeleteProduct(row: MallProduct) {
    dialog.warning({
      title: t('common.delete'),
      content: `${t('common.deleteConfirmPrefix')}「${row.name}」${t('common.deleteConfirmSuffix')}`,
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await mallApi.deleteProduct(row.id!)
        message.success(t('common.deleteSuccess'))
        await loadProducts()
      },
    })
  }

  // ---------------- 优惠券 ----------------
  const couponVisible = ref(false)
  const couponForm = reactive({
    id: undefined as number | undefined,
    code: '',
    title: '',
    amount: 0,
    minSpend: 0,
    totalCount: -1,
    expireTs: null as number | null,
    isEnable: true,
  })
  function openCoupon(row?: MallCoupon) {
    Object.assign(couponForm, {
      id: row?.id,
      code: row?.code || '',
      title: row?.title || '',
      amount: row?.amount ?? 0,
      minSpend: row?.minSpend ?? 0,
      totalCount: row?.totalCount ?? -1,
      expireTs: row?.expireTime ? new Date(row.expireTime).getTime() : null,
      isEnable: row?.isEnable !== false,
    })
    couponVisible.value = true
  }
  async function saveCouponRow() {
    if (!couponForm.code.trim() || !couponForm.title.trim()) {
      message.warning(t('common.fillCompleteInfo'))
      return
    }
    saving.value = true
    try {
      await mallApi.saveCoupon({
        id: couponForm.id,
        code: couponForm.code.trim(),
        title: couponForm.title.trim(),
        amount: couponForm.amount,
        minSpend: couponForm.minSpend,
        totalCount: couponForm.totalCount,
        expireTime: couponForm.expireTs ? new Date(couponForm.expireTs).toISOString() : undefined,
        isEnable: couponForm.isEnable,
      })
      message.success(t('common.operationSuccess'))
      couponVisible.value = false
      await loadCoupons()
    }
    finally {
      saving.value = false
    }
  }
  function doDeleteCoupon(row: MallCoupon) {
    dialog.warning({
      title: t('common.delete'),
      content: `${t('common.deleteConfirmPrefix')}「${row.title}」${t('common.deleteConfirmSuffix')}`,
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await mallApi.deleteCoupon(row.id!)
        message.success(t('common.deleteSuccess'))
        await loadCoupons()
      },
    })
  }

  // ---------------- 活动 ----------------
  const activityVisible = ref(false)
  const activityForm = reactive({
    id: undefined as number | undefined,
    title: '',
    description: '',
    sort: 0,
    isEnable: true,
  })
  function openActivity(row?: MallActivity) {
    Object.assign(activityForm, {
      id: row?.id,
      title: row?.title || '',
      description: row?.description || '',
      sort: row?.sort ?? 0,
      isEnable: row?.isEnable !== false,
    })
    activityVisible.value = true
  }
  async function saveActivityRow() {
    if (!activityForm.title.trim()) {
      message.warning(t('common.fillCompleteInfo'))
      return
    }
    saving.value = true
    try {
      await mallApi.saveActivity({ ...activityForm })
      message.success(t('common.operationSuccess'))
      activityVisible.value = false
      await loadActivities()
    }
    finally {
      saving.value = false
    }
  }
  function doDeleteActivity(row: MallActivity) {
    dialog.warning({
      title: t('common.delete'),
      content: `${t('common.deleteConfirmPrefix')}「${row.title}」${t('common.deleteConfirmSuffix')}`,
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        await mallApi.deleteActivity(row.id!)
        message.success(t('common.deleteSuccess'))
        await loadActivities()
      },
    })
  }

  // ---------------- 表格列 ----------------
  const enableSwitch = (value: boolean, onUpdate: (v: boolean) => void) =>
    h(NSwitch, { value, 'onUpdate:value': onUpdate })

  const categoryColumns = computed(() => [
    { title: t('mall.categoryCode'), key: 'code', width: 140 },
    { title: t('mall.categoryName'), key: 'name', minWidth: 140 },
    { title: t('common.sort'), key: 'sort', width: 80 },
    {
      title: t('common.isEnable'),
      key: 'isEnable',
      width: 90,
      render: (row: MallCategory) => enableSwitch(row.isEnable !== false, async () => {
        await mallApi.saveCategory({ ...row, isEnable: !(row.isEnable !== false) })
        await loadCategories()
      }),
    },
    {
      title: t('common.action'),
      key: 'action',
      width: 150,
      render: (row: MallCategory) =>
        h(TableAction as any, {
          style: 'button',
          actions: [
            { label: t('common.edit'), onClick: () => openCategory(row) },
            { label: t('common.delete'), onClick: () => doDeleteCategory(row) },
          ],
        }),
    },
  ])

  const productColumns = computed(() => [
    { title: t('mall.productName'), key: 'name', minWidth: 180 },
    { title: t('mall.categoryTab'), key: 'categoryCode', width: 110 },
    {
      title: t('mall.price'),
      key: 'price',
      width: 110,
      render: (row: MallProduct) => `¥${Number(row.price).toFixed(2)}`,
    },
    {
      title: t('mall.monthlySales'),
      key: 'monthlySales',
      width: 100,
      render: (row: MallProduct) => String(row.monthlySales ?? 0),
    },
    { title: t('mall.promoTags'), key: 'promoTags', minWidth: 140 },
    {
      title: t('common.isEnable'),
      key: 'isEnable',
      width: 90,
      render: (row: MallProduct) => enableSwitch(row.isEnable !== false, async () => {
        await mallApi.saveProduct({ ...row, isEnable: !(row.isEnable !== false) })
        await loadProducts()
      }),
    },
    {
      title: t('common.action'),
      key: 'action',
      width: 150,
      render: (row: MallProduct) =>
        h(TableAction as any, {
          style: 'button',
          actions: [
            { label: t('common.edit'), onClick: () => openProduct(row) },
            { label: t('common.delete'), onClick: () => doDeleteProduct(row) },
          ],
        }),
    },
  ])

  const couponColumns = computed(() => [
    { title: t('mall.couponCode'), key: 'code', width: 120 },
    { title: t('mall.couponTitle'), key: 'title', minWidth: 160 },
    {
      title: t('mall.couponAmount'),
      key: 'amount',
      width: 100,
      render: (row: MallCoupon) => `¥${Number(row.amount).toFixed(2)}`,
    },
    {
      title: t('mall.minSpend'),
      key: 'minSpend',
      width: 110,
      render: (row: MallCoupon) => (row.minSpend ? `¥${Number(row.minSpend).toFixed(2)}` : t('mall.noThreshold')),
    },
    {
      title: t('mall.claimed'),
      key: 'claimedCount',
      width: 110,
      render: (row: MallCoupon) =>
        row.totalCount === -1 ? `${row.claimedCount ?? 0} / ∞` : `${row.claimedCount ?? 0} / ${row.totalCount}`,
    },
    {
      title: t('common.isEnable'),
      key: 'isEnable',
      width: 90,
      render: (row: MallCoupon) => enableSwitch(row.isEnable !== false, async () => {
        await mallApi.saveCoupon({ ...row, isEnable: !(row.isEnable !== false) })
        await loadCoupons()
      }),
    },
    {
      title: t('common.action'),
      key: 'action',
      width: 150,
      render: (row: MallCoupon) =>
        h(TableAction as any, {
          style: 'button',
          actions: [
            { label: t('common.edit'), onClick: () => openCoupon(row) },
            { label: t('common.delete'), onClick: () => doDeleteCoupon(row) },
          ],
        }),
    },
  ])

  const activityColumns = computed(() => [
    { title: t('mall.activityTitle'), key: 'title', minWidth: 160 },
    { title: t('mall.activityDesc'), key: 'description', minWidth: 240, ellipsis: { tooltip: true } },
    { title: t('common.sort'), key: 'sort', width: 80 },
    {
      title: t('common.isEnable'),
      key: 'isEnable',
      width: 90,
      render: (row: MallActivity) => enableSwitch(row.isEnable !== false, async () => {
        await mallApi.saveActivity({ ...row, isEnable: !(row.isEnable !== false) })
        await loadActivities()
      }),
    },
    {
      title: t('common.action'),
      key: 'action',
      width: 150,
      render: (row: MallActivity) =>
        h(TableAction as any, {
          style: 'button',
          actions: [
            { label: t('common.edit'), onClick: () => openActivity(row) },
            { label: t('common.delete'), onClick: () => doDeleteActivity(row) },
          ],
        }),
    },
  ])

  onMounted(loadAll)
</script>
