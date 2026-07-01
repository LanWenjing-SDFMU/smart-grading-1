<template>
  <div style="padding: 20px; max-width: 800px; margin: 0 auto; font-family: Arial, sans-serif;">
    <h1>智能批阅系统</h1>
    <div style="margin-bottom: 15px;">
      <label>试卷图片：</label>
      <input type="file" @change="handleFileChange" accept="image/*" />
    </div>
    <div style="margin-bottom: 15px;">
      <label>标准答案：</label>
      <input v-model="form.standardAnswer" placeholder="输入标准答案" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" />
    </div>
    <button @click="submit" :disabled="loading" style="background: #409EFF; color: white; border: none; padding: 10px 20px; border-radius: 4px; cursor: pointer; font-size: 16px;">
      {{ loading ? '批阅中...' : '开始批阅' }}
    </button>

    <div v-if="result" style="margin-top: 30px; border-top: 1px solid #eee; padding-top: 20px;">
      <h2>批阅结果</h2>
      <p><strong>结果：</strong> {{ result.result }}</p>
      <!-- 以下文本可选显示，因图片已包含 -->
      <!-- <p><strong>解析：</strong> {{ result.explanation }}</p>
      <p v-if="result.result === '错误'"><strong>错因分析：</strong> {{ result.errorAnalysis }}</p> -->
      <div style="margin-top: 10px; text-align: center;">
        <img :src="'data:image/png;base64,' + result.markedImageBase64" style="max-width: 100%; border: 1px solid #ddd;" id="resultImage" />
        <br />
        <button @click="downloadImage" style="margin-top: 10px; background: #67C23A; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; font-size: 14px;">
          下载批阅图片
        </button>
      </div>
    </div>
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

const handleFileChange = (e) => {
  const file = e.target.files[0]
  if (file) {
    form.file = file
    console.log('文件已选择:', file.name)
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
</script>