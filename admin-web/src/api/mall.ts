import { http } from '@/utils/http/axios'

/**
 * 商城数据管理 API（管理端）。
 *
 * 四类目录数据：分类 / 商品 / 优惠券 / 活动。
 * 均为软删（后端置 is_deleted），save 接口按 id 是否存在自动区分新增/更新。
 */
export interface MallCategory {
  id?: number
  code: string
  name: string
  icon?: string
  sort?: number
  isEnable?: boolean
}

export interface MallProduct {
  id?: number
  name: string
  categoryCode: string
  price: number
  originalPrice?: number
  monthlySales?: number
  promoTags?: string
  couponHint?: string
  remark?: string
  isEnable?: boolean
}

export interface MallCoupon {
  id?: number
  code: string
  title: string
  amount: number
  minSpend?: number
  totalCount?: number
  claimedCount?: number
  expireTime?: string
  isEnable?: boolean
}

export interface MallActivity {
  id?: number
  title: string
  description?: string
  icon?: string
  sort?: number
  isEnable?: boolean
}

function fetchCategories() {
  return http.request({ url: '/admin/mall/category/list', method: 'get' })
}
function saveCategory(data: MallCategory) {
  return http.request({ url: '/admin/mall/category/save', method: 'post', data })
}
function deleteCategory(id: number) {
  return http.request({ url: `/admin/mall/category/delete?id=${id}`, method: 'post' })
}

function fetchProducts(categoryCode?: string) {
  return http.request({
    url: '/admin/mall/product/list',
    method: 'get',
    params: categoryCode ? { categoryCode } : undefined,
  })
}
function saveProduct(data: MallProduct) {
  return http.request({ url: '/admin/mall/product/save', method: 'post', data })
}
function deleteProduct(id: number) {
  return http.request({ url: `/admin/mall/product/delete?id=${id}`, method: 'post' })
}

function fetchCoupons() {
  return http.request({ url: '/admin/mall/coupon/list', method: 'get' })
}
function saveCoupon(data: MallCoupon) {
  return http.request({ url: '/admin/mall/coupon/save', method: 'post', data })
}
function deleteCoupon(id: number) {
  return http.request({ url: `/admin/mall/coupon/delete?id=${id}`, method: 'post' })
}

function fetchActivities() {
  return http.request({ url: '/admin/mall/activity/list', method: 'get' })
}
function saveActivity(data: MallActivity) {
  return http.request({ url: '/admin/mall/activity/save', method: 'post', data })
}
function deleteActivity(id: number) {
  return http.request({ url: `/admin/mall/activity/delete?id=${id}`, method: 'post' })
}

export default {
  fetchCategories,
  saveCategory,
  deleteCategory,
  fetchProducts,
  saveProduct,
  deleteProduct,
  fetchCoupons,
  saveCoupon,
  deleteCoupon,
  fetchActivities,
  saveActivity,
  deleteActivity,
}
