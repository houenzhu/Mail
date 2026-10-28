import { dictLabel, dictTag } from '@/constants/dict'

/**
 * 状态字典 → 展示文案 / 标签色。
 * 具体字典值仍从 `@/constants/dict` 引入（那里是唯一来源，对齐需求文档 16.5）。
 */
export function useDict() {
  return {
    label: (dict, value) => dictLabel(dict, value),
    tag: (dict, value) => dictTag(dict, value),
  }
}
