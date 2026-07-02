<template>
  <div class="app-container">
    <header class="app-header">
      <div class="header-brand">
        <span class="brand-icon">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 20h9"></path>
            <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path>
          </svg>
        </span>
        <h1>智能批阅系统</h1>
      </div>
      <p class="header-subtitle">AI-powered grading platform</p>
    </header>

    <main class="main-content">
      <section class="input-section">
        <div class="form-group">
          <label class="form-label">试卷图片</label>
          <div class="file-upload-wrapper">
            <input type="file" @change="handleFileChange" accept="image/*" id="fileInput" class="file-input" />
            <label for="fileInput" class="file-upload-btn">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 6px;">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                <polyline points="17 8 12 3 7 8"></polyline>
                <line x1="12" y1="3" x2="12" y2="15"></line>
              </svg>
              {{ form.file ? form.file.name : '选择图片文件' }}
            </label>
          </div>
        </div>

        <div class="form-group">
          <label class="form-label" for="standardAnswer">标准答案</label>
          <textarea
            id="standardAnswer"
            v-model="form.standardAnswer"
            placeholder="请输入标准答案..."
            rows="3"
            class="form-textarea"
          ></textarea>
        </div>

        <button @click="submit" :disabled="loading" class="btn-primary">
          <svg v-if="!loading" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 6px;">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg>
          <span v-if="loading" class="spinner"></span>
          {{ loading ? '批阅中...' : '开始批阅' }}
        </button>
      </section>

      <section v-if="result" class="result-section">
        <div class="result-header">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 8px; color: #059669;">
            <polyline points="9 11 12 14 22 4"></polyline>
            <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path>
          </svg>
          <h2>批阅结果</h2>
        </div>

        <div class="result-body">
          <div class="image-section">
            <img :src="'data:image/png;base64,' + result.markedImageBase64" class="result-image" />
            <button @click="downloadImage" class="btn-download">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 6px;">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                <polyline points="7 10 12 15 17 10"></polyline>
                <line x1="12" y1="15" x2="12" y2="3"></line>
              </svg>
              下载批阅图片
            </button>
          </div>

          <div v-if="result.overallComment" class="comment-section">
            <label class="comment-label">整体评价</label>
            <textarea
              readonly
              :value="result.overallComment"
              rows="3"
              class="comment-textarea"
              @click="selectAllText"
              ref="commentTextarea"
            ></textarea>
            <p class="comment-hint">点击文本框自动全选，方便复制</p>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import axios from 'axios'

const form = reactive({
  standardAnswer: '',
  file: null
})

const loading = ref(false)
const result = ref(null)
const commentTextarea = ref(null)

const handleFileChange = (e) => {
  const file = e.target.files[0]
  if (file) {
    form.file = file
  }
}

const submit = async () => {
  result.value = null

  if (!form.file) {
    alert('请选择图片')
    return
  }
  if (!form.standardAnswer.trim()) {
    alert('请填写标准答案')
    return
  }

  loading.value = true

  const formData = new FormData()
  formData.append('file', form.file)
  formData.append('standardAnswer', form.standardAnswer.trim())

  try {
    const res = await axios.post('http://localhost:8080/api/grading/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    console.log('响应数据:', res.data)
    if (res.data) {
      result.value = res.data
    }
  } catch (error) {
    console.error('请求失败:', error)
    alert('批阅失败：' + (error.response?.data?.message || error.message))
  } finally {
    loading.value = false
  }
}

const downloadImage = () => {
  if (!result.value || !result.value.markedImageBase64) return
  const link = document.createElement('a')
  link.href = 'data:image/png;base64,' + result.value.markedImageBase64
  link.download = '批阅结果.png'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const selectAllText = (event) => {
  event.target.select()
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  background: #f0f2f5;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  color: #1f2937;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px 80px;
}

.app-header {
  text-align: center;
  margin-bottom: 32px;
}

.header-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.brand-icon {
  display: inline-flex;
  color: #2563eb;
}

.app-header h1 {
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  letter-spacing: -0.5px;
}

.header-subtitle {
  margin-top: 6px;
  font-size: 14px;
  color: #6b7280;
  letter-spacing: 0.3px;
}

.main-content {
  width: 100%;
  max-width: 720px;
}

.input-section {
  background: #ffffff;
  border-radius: 12px;
  padding: 28px 32px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06), 0 1px 2px rgba(0, 0, 0, 0.04);
  border: 1px solid #e5e7eb;
}

.form-group {
  margin-bottom: 20px;
}

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}

.file-upload-wrapper {
  position: relative;
}

.file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
}

.file-upload-btn {
  display: inline-flex;
  align-items: center;
  padding: 10px 20px;
  border: 2px dashed #d1d5db;
  border-radius: 8px;
  background: #f9fafb;
  color: #4b5563;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-upload-btn:hover {
  border-color: #2563eb;
  background: #eff6ff;
  color: #2563eb;
}

.form-textarea {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  color: #1f2937;
  background: #ffffff;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  resize: vertical;
  font-family: inherit;
}

.form-textarea:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.form-textarea::placeholder {
  color: #9ca3af;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 12px 24px;
  background: #2563eb;
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease, transform 0.1s ease;
}

.btn-primary:hover:not(:disabled) {
  background: #1d4ed8;
}

.btn-primary:active:not(:disabled) {
  transform: scale(0.99);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  margin-right: 8px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Result Section */
.result-section {
  margin-top: 28px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06), 0 1px 2px rgba(0, 0, 0, 0.04);
  border: 1px solid #e5e7eb;
  overflow: hidden;
}

.result-header {
  display: flex;
  align-items: center;
  padding: 20px 28px 0;
}

.result-header h2 {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.result-body {
  padding: 20px 28px 28px;
}

.image-section {
  text-align: center;
}

.result-image {
  max-width: 100%;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
}

.btn-download {
  display: inline-flex;
  align-items: center;
  margin-top: 14px;
  padding: 10px 20px;
  background: #059669;
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s ease;
}

.btn-download:hover {
  background: #047857;
}

.comment-section {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #e5e7eb;
}

.comment-label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}

.comment-textarea {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  color: #1f2937;
  background: #ffffff;
  resize: vertical;
  font-family: inherit;
  cursor: pointer;
}

.comment-textarea:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.comment-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #9ca3af;
}

@media (max-width: 600px) {
  .app-container {
    padding: 20px 16px 60px;
  }

  .input-section,
  .result-body {
    padding-left: 20px;
    padding-right: 20px;
  }

  .result-header {
    padding-left: 20px;
    padding-right: 20px;
  }

  .app-header h1 {
    font-size: 24px;
  }
}
</style>