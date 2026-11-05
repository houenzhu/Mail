import { ApiError, CODE } from '@/api/client'

const CART_MESSAGES = {
  [CODE.CART_ITEM_NOT_FOUND]: '购物车中的商品已发生变化，请刷新后重试',
  [CODE.CART_QUANTITY_INVALID]: '商品数量不合法，请重新选择',
  [CODE.CART_ITEM_LIMIT_EXCEEDED]: '购物车最多保存 100 种商品，请先清理部分商品',
  [CODE.CART_PRODUCT_UNAVAILABLE]: '商品已下架，暂时无法购买',
  [CODE.CART_SKU_UNAVAILABLE]: '该规格已下架或售罄，请选择其他规格',
  [CODE.CART_SELECTION_EMPTY]: '请至少选择一件有效商品',
  [CODE.CART_QUANTITY_EXCEEDED]: '单件商品最多购买 99 件',
  [CODE.CART_STOCK_NOT_ENOUGH]: '库存不足，请减少购买数量',
  [CODE.PRODUCT_NOT_FOUND]: '商品不存在或已被删除',
  [CODE.PRODUCT_OFF_SHELF]: '商品已下架，看看其他好物吧',
  34001: '订单不存在或已被删除',
  34002: '当前订单状态不允许该操作，请刷新后重试',
  34003: '订单已提交，请勿重复操作',
  35001: '支付单不存在',
  35004: '支付单状态已变化，请刷新后重试',
  35007: '支付渠道暂不可用，请稍后重试',
  35008: '支付已超时，请重新发起',
  31007: '收货地址不存在或已删除',
  31008: '收货地址数量已达上限，请先删除不用的地址',
  31009: '默认地址设置冲突，请刷新后重试',
  37006: '请至少选择一件有效商品',
}

const COMMON_MESSAGES = {
  NETWORK: '网络异常，请检查连接后重试',
  TIMEOUT: '请求超时，请稍后重试',
}

/** 把接口错误转换为适合直接展示给买家的文案。 */
export function messageForError(error, fallback = '操作失败，请稍后重试') {
  if (!(error instanceof ApiError)) return fallback
  return CART_MESSAGES[error.code]
    || COMMON_MESSAGES[error.code]
    || error.message
    || fallback
}