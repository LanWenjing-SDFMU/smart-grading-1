<template>
  <div class="app-layout">
    <!-- 顶栏 -->
    <header class="app-topbar">
      <div class="topbar-brand">
        <span class="brand-logo">
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 20h9"></path>
            <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path>
          </svg>
        </span>
        <h1 class="brand-title">智能批阅系统</h1>
      </div>
      <div class="topbar-meta">
        <span class="meta-text">AI-powered grading platform</span>
      </div>
    </header>

    <div class="app-body">
      <!-- 侧边栏 -->
      <aside class="app-sidebar">
        <nav class="sidebar-nav">
          <div
            v-for="item in menuItems"
            :key="item.key"
            class="nav-item"
            :class="{ active: activeMenu === item.key }"
            @click="activeMenu = item.key"
          >
            <span class="nav-icon" v-html="item.icon"></span>
            <span class="nav-label">{{ item.label }}</span>
            <span v-if="item.badge" class="nav-badge">{{ item.badge }}</span>
          </div>
        </nav>
        <div class="sidebar-footer">
          <div class="footer-info">
            <div class="footer-dot"></div>
            <span class="footer-text">系统在线</span>
          </div>
        </div>
      </aside>

      <!-- 内容区 -->
      <main class="app-content">
        <PaperGrading v-if="activeMenu === 'paper'" />
        <EssayGrading v-else-if="activeMenu === 'essay'" />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PaperGrading from './components/PaperGrading.vue'
import EssayGrading from './components/EssayGrading.vue'

const activeMenu = ref('paper')

const menuItems = [
  {
    key: 'paper',
    label: '试卷批阅',
    icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="9" y1="13" x2="15" y2="13"></line><line x1="9" y1="17" x2="13" y2="17"></line></svg>'
  },
  {
    key: 'essay',
    label: '作文批阅',
    badge: '待开放',
    icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"></path><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path></svg>'
  }
]
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }

html, body, #app {
  height: 100%;
}

body {
  background: #f0f2f5;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  color: #1f2937;
  -webkit-font-smoothing: antialiased;
}

/* 整体布局 */
.app-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* 顶栏 */
.app-topbar {
  height: 64px;
  background: linear-gradient(90deg, #1e3a8a 0%, #2563eb 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  box-shadow: 0 2px 8px rgba(30, 58, 138, 0.15);
  position: sticky;
  top: 0;
  z-index: 100;
  flex-shrink: 0;
}

.topbar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  backdrop-filter: blur(8px);
}

.brand-title {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 1px;
}

.topbar-meta {
  display: flex;
  align-items: center;
}

.meta-text {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
  letter-spacing: 0.5px;
}

/* 主体区域 */
.app-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

/* 侧边栏 */
.app-sidebar {
  width: 220px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  position: sticky;
  top: 64px;
  height: calc(100vh - 64px);
  overflow-y: auto;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 8px;
  cursor: pointer;
  color: #4b5563;
  font-size: 15px;
  font-weight: 500;
  transition: all 0.2s;
  margin-bottom: 4px;
  position: relative;
}

.nav-item:hover {
  background: #f3f4f6;
  color: #2563eb;
}

.nav-item.active {
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  color: #2563eb;
  font-weight: 600;
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 60%;
  background: #2563eb;
  border-radius: 0 3px 3px 0;
}

.nav-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nav-label {
  flex: 1;
}

.nav-badge {
  font-size: 11px;
  padding: 2px 8px;
  background: #fef3c7;
  color: #d97706;
  border-radius: 10px;
  font-weight: 500;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid #f3f4f6;
}

.footer-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.footer-dot {
  width: 8px;
  height: 8px;
  background: #22c55e;
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.2);
}

.footer-text {
  font-size: 12px;
  color: #9ca3af;
}

/* 内容区 */
.app-content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 24px 32px;
}

/* 响应式 */
@media (max-width: 768px) {
  .app-sidebar {
    width: 64px;
  }
  .nav-label, .nav-badge, .sidebar-footer, .meta-text {
    display: none;
  }
  .nav-item {
    justify-content: center;
    padding: 12px;
  }
  .app-content {
    padding: 16px;
  }
  .brand-title {
    font-size: 17px;
  }
}
</style>