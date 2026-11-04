<script setup>
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import { footerColumns } from '@/data/homeContent'
</script>

<template>
  <footer class="site-footer">
    <div class="mall-container">
      <div class="site-footer__top">
        <div class="site-footer__brand">
          <div class="site-footer__logo">
            <span class="site-footer__logo-mark">好</span>
            <span>好物集</span>
          </div>
          <p>把日子过成喜欢的样子。<br />精选好物，认真生活。</p>
          <div class="site-footer__social">
            <span><AppIcon name="clock" :size="14" /> 客服时间 9:00 - 22:00</span>
            <span><AppIcon name="shield-check" :size="14" /> 平台担保交易</span>
          </div>
        </div>

        <div v-for="column in footerColumns" :key="column.title" class="site-footer__column">
          <h3>{{ column.title }}</h3>
          <ul>
            <li v-for="link in column.links" :key="link">
              <RouterLink :to="{ name: 'feature', params: { feature: 'help' } }">
                {{ link }}
              </RouterLink>
            </li>
          </ul>
        </div>
      </div>

      <div class="site-footer__bottom">
        <p>
          关于我们 · 联系我们 · 商家入驻 · 隐私政策 · 服务协议
        </p>
        <p class="site-footer__copy">
          © 2026 好物集 GOODMALL · 本站为学习演示项目，不提供真实交易
        </p>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.site-footer {
  margin-top: 48px;
  padding: 40px 0 24px;
  background: #fff;
  border-top: 1px solid var(--mall-line);
}

.site-footer__top {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) repeat(4, minmax(0, 1fr));
  gap: 32px;
  padding-bottom: 32px;
  border-bottom: 1px solid var(--mall-line);
}

.site-footer__logo {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  font-size: 20px;
  font-weight: 800;
}

.site-footer__logo-mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--mall-brand);
  color: #fff;
  font-size: 16px;
}

.site-footer__brand p {
  margin-top: 12px;
  color: var(--mall-ink-3);
  font-size: 12px;
  line-height: 1.8;
}

.site-footer__social {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 14px;
  color: var(--mall-ink-2);
  font-size: 11px;
}

.site-footer__social span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.site-footer__column h3 {
  font-size: 13px;
  font-weight: 700;
}

.site-footer__column ul {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.site-footer__column a {
  color: var(--mall-ink-3);
  font-size: 12px;
  transition: color 0.15s var(--mall-ease);
}

.site-footer__column a:hover {
  color: var(--mall-brand);
}

.site-footer__bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 18px;
  color: var(--mall-ink-4);
  font-size: 11px;
}

@media (max-width: 900px) {
  .site-footer__top {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 26px;
  }

  .site-footer__brand {
    grid-column: 1 / -1;
  }
}

@media (max-width: 560px) {
  .site-footer {
    margin-top: 32px;
    padding-top: 28px;
  }

  .site-footer__top {
    gap: 22px;
  }

  .site-footer__bottom {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }
}
</style>
