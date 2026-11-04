import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 轻量轮播状态机：自动播放、暂停、键盘方向键、减少动效感知。
 * 不依赖任何组件库，任何横向内容区都可用。
 */
export function useCarousel({ length, interval = 5200, autoplay = true } = {}) {
  const activeIndex = ref(0)
  const isPaused = ref(false)
  const prefersReducedMotion = ref(false)
  let timer = null

  function normalize(index) {
    const total = typeof length === 'function' ? length() : Number(length) || 0
    if (total <= 0) return 0
    return ((index % total) + total) % total
  }

  function goTo(index) {
    activeIndex.value = normalize(index)
  }

  function next() {
    goTo(activeIndex.value + 1)
  }

  function prev() {
    goTo(activeIndex.value - 1)
  }

  function stop() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  function start() {
    stop()
    if (!autoplay || prefersReducedMotion.value || isPaused.value) return
    timer = setInterval(next, interval)
  }

  function pause() {
    isPaused.value = true
    stop()
  }

  function resume() {
    isPaused.value = false
    start()
  }

  function toggle() {
    isPaused.value ? resume() : pause()
  }

  function handleKeydown(event) {
    if (event.key === 'ArrowRight') {
      next()
      start()
    } else if (event.key === 'ArrowLeft') {
      prev()
      start()
    }
  }

  onMounted(() => {
    if (typeof window.matchMedia === 'function') {
      const media = window.matchMedia('(prefers-reduced-motion: reduce)')
      prefersReducedMotion.value = media.matches
      const onChange = (event) => {
        prefersReducedMotion.value = event.matches
        event.matches ? stop() : start()
      }
      media.addEventListener?.('change', onChange)
      onBeforeUnmount(() => media.removeEventListener?.('change', onChange))
    }
    start()
  })

  onBeforeUnmount(stop)

  return {
    activeIndex,
    isPaused,
    prefersReducedMotion,
    goTo,
    next,
    prev,
    pause,
    resume,
    toggle,
    handleKeydown,
  }
}
