<template>
  <div class="essay-grading">
    <div class="placeholder-card">
      <div class="placeholder-icon">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
          <polyline points="14 2 14 8 20 8"></polyline>
          <line x1="8" y1="13" x2="16" y2="13"></line>
          <line x1="8" y1="17" x2="14" y2="17"></line>
        </svg>
      </div>
      <h2 class="placeholder-title">作文批阅</h2>
      <p class="placeholder-desc">该模块正在开发中，敬请期待</p>
      <div class="placeholder-badge">Coming Soon</div>
    </div>
  </div>
</template>

<script setup>
// 作文批阅模块 - 待开发
</script>

<style scoped>
.essay-grading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 64px);
  padding: 40px 20px;
}

.placeholder-card {
  background: #fff;
  border-radius: 16px;
  padding: 60px 48px;
  text-align: center;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  border: 1px solid #e5e7eb;
  max-width: 480px;
  width: 100%;
}

.placeholder-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 112px;
  height: 112px;
  border-radius: 50%;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  color: #2563eb;
  margin-bottom: 28px;
}

.placeholder-title {
  font-size: 26px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 12px;
}

.placeholder-desc {
  font-size: 15px;
  color: #6b7280;
  line-height: 1.6;
  margin: 0 0 24px;
}

.placeholder-badge {
  display: inline-block;
  padding: 6px 18px;
  background: linear-gradient(135deg, #2563eb 0%, #7c3aed 100%);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  border-radius: 20px;
  letter-spacing: 0.5px;
}
</style>