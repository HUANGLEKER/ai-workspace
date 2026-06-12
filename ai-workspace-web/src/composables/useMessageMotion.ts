/**
 * 聊天消息入场动效预设（基于 VueUse Motion 的 v-motion 指令）。
 *
 * 设计目标：ChatGPT / Claude 风格的「专业、克制」入场——只做透明度 + 轻微上移，
 * 不缩放、不旋转、不大幅弹跳。每条消息在自身挂载时独立播放，已存在的消息不受影响，
 * 因此新消息插入不会触发整列重排动画，也不会产生页面抖动。
 *
 * - 用户消息：纯缓动（tween），TranslateY 10px → 0，250ms。干脆利落。
 * - AI 消息：弹簧（spring），TranslateY 12px → 0。damping 偏大、几乎无过冲，
 *   呈现「轻微回弹」的细腻感而非夸张弹跳。
 */
import type { MotionVariants } from '@vueuse/motion'

/** 用户消息：Fade In + 上移 10px，250ms 缓动 */
export const userMessageMotion: MotionVariants<never> = {
  initial: { opacity: 0, y: 10 },
  enter: {
    opacity: 1,
    y: 0,
    transition: {
      duration: 250,
      // easeOutQuart：起步快、收尾稳，符合现代 UI 的「果断」手感
      ease: [0.22, 1, 0.36, 1],
    },
  },
}

/** AI 消息：Fade In + 上移 12px，克制的弹簧（几乎无过冲） */
export const assistantMessageMotion: MotionVariants<never> = {
  initial: { opacity: 0, y: 12 },
  enter: {
    opacity: 1,
    y: 0,
    transition: {
      type: 'spring',
      stiffness: 220, // 适中刚度：到位迅速但不生硬
      damping: 26,    // 高阻尼：抑制过冲，杜绝夸张弹跳
      mass: 0.9,
    },
  },
}

/**
 * 「不播放」预设：用于流式气泡定稿后提交进列表的那条消息。
 * 该消息此前已作为流式气泡可见，故直接以最终态出现，避免淡入重影 / 闪烁。
 */
export const staticMotion: MotionVariants<never> = {
  initial: { opacity: 1, y: 0 },
  enter: { opacity: 1, y: 0 },
}

/** 按角色取入场预设；noAnimate 为 true 时返回静态预设（不播放入场） */
export function messageMotion(role: string, noAnimate = false): MotionVariants<never> {
  if (noAnimate) return staticMotion
  return role === 'user' ? userMessageMotion : assistantMessageMotion
}
