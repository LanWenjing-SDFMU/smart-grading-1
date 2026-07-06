<template>
  <div class="paper-grading">
    <main class="main-content">
      <section class="input-section">
        <div class="form-group">
          <label class="form-label">试卷图片（可多选）</label>
          <div class="file-upload-wrapper">
            <input type="file" @change="handleFileChange" accept="image/*" id="fileInput" class="file-input" multiple />
            <label for="fileInput" class="file-upload-btn">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 6px;">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                <polyline points="17 8 12 3 7 8"></polyline>
                <line x1="12" y1="3" x2="12" y2="15"></line>
              </svg>
              {{ files.length > 0 ? files.length + ' 个文件已选择' : '选择图片文件（可多选）' }}
            </label>
          </div>
          <div v-if="files.length > 0" class="file-list">
            <div v-for="(f, i) in files" :key="i" class="file-item">{{ i + 1 }}. {{ f.name }}</div>
          </div>
        </div>
        <div class="form-group">
          <label class="form-label" for="standardAnswer">标准答案</label>
          <textarea id="standardAnswer" v-model="standardAnswer" placeholder="请输入标准答案..." rows="3" class="form-textarea"></textarea>
        </div>
        <button @click="submit" :disabled="loading" class="btn-primary">
          <svg v-if="!loading" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 6px;"><polyline points="20 6 9 17 4 12"></polyline></svg>
          <span v-if="loading" class="spinner"></span>
          {{ loading ? '批阅中...' : '开始批阅' }}
        </button>
      </section>

      <!-- 多页结果 -->
      <template v-if="pages.length > 0">
        <!-- 页面导航标签 -->
        <div class="page-tabs">
          <button v-for="(p, i) in pages" :key="i" class="page-tab" :class="{ active: currentPage === i }" @click="switchPage(i)">
            第{{ i + 1 }}页
            <span class="page-filename">{{ p.pageFilename }}</span>
          </button>
        </div>

        <section class="result-section">
          <div class="result-header">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 8px; color: #059669;"><polyline points="9 11 12 14 22 4"></polyline><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path></svg>
            <h2>批阅结果 - 第{{ currentPage + 1 }}页</h2>
            <span class="badge-editable">可编辑</span>
          </div>
          <div class="result-body">
            <div class="editor-section">
              <div class="editor-canvas-wrapper" ref="editorWrapper">
                <img :src="'data:image/png;base64,' + currentResult.originalImageBase64" class="editor-bg-image" ref="bgImage" @load="initAnnotations" />
                <div class="annotations-layer" v-if="currentAnnotations.length > 0">
                  <div v-for="(ann, i) in currentAnnotations" :key="'mark-' + i" class="mark-overlay" :style="getMarkStyle(ann)">
                    <span class="mark-text" :class="{ 'mark-correct': ann.isCorrect, 'mark-wrong': !ann.isCorrect }" @click.stop="toggleMark(i)">{{ ann.isCorrect ? '✓' : '×' }}</span>
                  </div>
                  <template v-for="(ann, i) in currentAnnotations" :key="'box-' + i">
                    <div v-if="!ann.isCorrect" class="annotation-box" :class="{ 'dragging': ann.dragging }" :style="getBoxStyle(ann)" :data-box-id="'box-' + pageKey + '-' + i" @mousedown.prevent="startDrag($event, i)">
                      <div class="box-content" :contenteditable="true" @mousedown.stop @input="onBoxEdit(i, $event)" @blur="onBoxBlur(i, $event)" :data-ann-idx="i"></div>
                      <div class="resize-handle" @mousedown.stop="startResize($event, i)"></div>
                    </div>
                  </template>
                  <template v-for="(ann, i) in currentAnnotations" :key="'line-' + i">
                    <div v-if="!ann.isCorrect" class="connector-line" :style="getLineStyle(ann)"></div>
                  </template>
                </div>

                <div v-if="currentSummaryBox" class="summary-box" :style="getSummaryStyle()">
                  <div class="summary-label"> 整体评价</div>
                  <div class="summary-content" contenteditable="true" @input="onSummaryEdit" ref="summaryContentRef"></div>
                </div>
              </div>
            </div>
            <div class="editor-actions">
              <button @click="downloadCurrentPage" class="btn-download">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 6px;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                下载当前页
              </button>
              <button @click="resetAnnotations" class="btn-reset">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 6px;"><polyline points="1 4 1 10 7 10"></polyline><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"></path></svg>
                重置位置
              </button>
            </div>
          </div>
        </section>
      </template>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick } from 'vue'
import axios from 'axios'

const files = ref([])
const standardAnswer = ref('')
const loading = ref(false)
const pages = ref([])
const currentPage = ref(0)
const editorWrapper = ref(null)
const bgImage = ref(null)
const summaryContentRef = ref(null)

// 当前页的响应式数据（每次切页时重建）
const annotations = ref([])
const imageScale = ref(1)
const summaryBox = ref(null)
const MARK_SIZE = 24

const pageKey = computed(() => 'page-' + currentPage.value)

const currentResult = computed(() => pages.value[currentPage.value] || null)
const currentAnnotations = computed(() => annotations.value)
const currentSummaryBox = computed(() => summaryBox.value)

const handleFileChange = (e) => {
  files.value = Array.from(e.target.files || [])
}

const submit = async () => {
  pages.value = []; currentPage.value = 0; annotations.value = []; summaryBox.value = null
  if (files.value.length === 0) { alert('请选择至少一张图片'); return }
  if (!standardAnswer.value.trim()) { alert('请填写标准答案'); return }
  loading.value = true
  const formData = new FormData()
  files.value.forEach(f => formData.append('files', f))
  formData.append('standardAnswer', standardAnswer.value.trim())
  try {
    const res = await axios.post('http://localhost:8080/api/grading/batch-upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    if (res.data && res.data.pages) {
      pages.value = res.data.pages
      currentPage.value = 0
    }
  } catch (error) {
    console.error('请求失败:', error)
    alert('批阅失败：' + (error.response?.data?.message || error.message))
  } finally { loading.value = false }
}

const switchPage = (idx) => {
  annotations.value = []; summaryBox.value = null
  currentPage.value = idx
  // nextTick 后图片加载会触发 initAnnotations
}

const getOffsetX = () => {
  const wrapper = editorWrapper.value
  if (!wrapper) return 0
  return parseFloat(getComputedStyle(wrapper).paddingLeft) || 0
}

const getBoundaries = () => {
  const wrapper = editorWrapper.value
  if (!wrapper || !bgImage.value) return { leftMarginR: 0, rightMarginL: 0, topEdge: 0, bottomEdge: 0, wrapperW: 0 }
  const offsetX = getOffsetX()
  const imgH = bgImage.value.clientHeight
  const wrapperW = wrapper.clientWidth
  return { leftMarginR: offsetX - 5, rightMarginL: wrapperW - offsetX + 5, topEdge: 3, bottomEdge: imgH - 3, wrapperW }
}

const buildBoxHtml = (q) => {
  const exp = q.explanation || ''
  let combinedText = ''
  const parseIdx = exp.indexOf('【解析】')
  const errIdx = exp.indexOf('【错因】')
  const parseText = parseIdx !== -1 ? exp.substring(parseIdx + 4, errIdx !== -1 ? errIdx : exp.length).trim() : ''
  const errText = errIdx !== -1 ? exp.substring(errIdx + 4).trim() : ''
  if (parseText && errText) combinedText = parseText + '；' + errText
  else if (parseText) combinedText = parseText
  else if (errText) combinedText = errText
  else if (exp.trim()) combinedText = exp.trim()
  if (q.studentAnswer && !combinedText) combinedText = '学生答案: ' + q.studentAnswer
  return combinedText ? `<span class="c-parse-error">【解析/错因】${escapeHtml(combinedText)}</span>` : '双击编辑...'
}

function escapeHtml(str) {
  return String(str).replace(/[&]/g, '&' + 'amp;').replace(/</g, '&' + 'lt;').replace(/>/g, '&' + 'gt;')
}

const getGroupIndices = (idx) => {
  const ann = annotations.value[idx]
  if (!ann) return [idx]
  const qn = ann.questionNumber
  const result = []
  for (let k = 0; k < annotations.value.length; k++) {
    if (annotations.value[k].questionNumber === qn) result.push(k)
  }
  return result
}

const isGroupWrong = (idx) => {
  const indices = getGroupIndices(idx)
  return indices.some(i => !annotations.value[i].isCorrect)
}

const getGroupCenter = (idx) => {
  const indices = getGroupIndices(idx)
  let totalX = 0, totalY = 0, count = 0
  indices.forEach(i => { const a = annotations.value[i]; totalX += a.displayX + a.displayW / 2; totalY += a.displayY + a.displayH / 2; count++ })
  if (count === 0) return null
  return { cx: totalX / count, cy: totalY / count }
}

const findGroupBoxPosition = (idx) => {
  const ann = annotations.value[idx]
  if (!ann) return null
  const wrapper = editorWrapper.value
  if (!wrapper) return null
  const { leftMarginR, rightMarginL, topEdge, bottomEdge, wrapperW } = getBoundaries()
  const offsetX = getOffsetX()
  const imageAreaW = wrapper.clientWidth - 2 * offsetX
  const boxW = 200, boxH = 70
  const center = getGroupCenter(idx)
  const centerX = center ? center.cx : (offsetX + ann.displayX + ann.displayW / 2)
  const centerY = center ? center.cy : (ann.displayY + ann.displayH / 2)
  const isLeftSide = centerX < (offsetX + imageAreaW / 2)
  let boxX, boxY
  if (isLeftSide) boxX = Math.max(5, leftMarginR - boxW)
  else boxX = rightMarginL
  boxY = Math.max(topEdge, Math.min(centerY - boxH / 2, bottomEdge - boxH))
  boxX = Math.max(5, Math.min(boxX, wrapper.clientWidth - boxW - 5))
  const groupIndices = getGroupIndices(idx)
  const isGroupMember = (k) => groupIndices.includes(k)
  const checkOverlap = (x, y) => {
    for (let k = 0; k < annotations.value.length; k++) {
      if (isGroupMember(k) || annotations.value[k].isCorrect) continue
      const b = annotations.value[k]
      if (x < b.boxX + b.boxW && x + boxW > b.boxX && y < b.boxY + b.boxH && y + boxH > b.boxY) return true
    }
    return false
  }
  if (!checkOverlap(boxX, boxY)) return { boxX, boxY, boxW, boxH, isLeftSide }
  let switchedX
  if (isLeftSide) switchedX = rightMarginL
  else switchedX = Math.max(5, leftMarginR - boxW)
  if (!checkOverlap(switchedX, boxY)) return { boxX: switchedX, boxY, boxW, boxH, isLeftSide: !isLeftSide }
  for (let offset = 0; offset < bottomEdge; offset += boxH + 10) {
    for (const tryY of [boxY + offset, boxY - offset]) {
      if (tryY < topEdge || tryY + boxH > bottomEdge) continue
      if (!checkOverlap(boxX, tryY)) return { boxX, boxY: tryY, boxW, boxH, isLeftSide }
      if (!checkOverlap(switchedX, tryY)) return { boxX: switchedX, boxY: tryY, boxW, boxH, isLeftSide: !isLeftSide }
    }
  }
  return { boxX, boxY: Math.max(topEdge, Math.min(boxY, bottomEdge - boxH)), boxW, boxH, isLeftSide }
}

const toggleMark = (idx) => {
  const ann = annotations.value[idx]
  if (!ann) return
  const groupIndices = getGroupIndices(idx)
  const currentlyWrong = isGroupWrong(idx)
  if (!currentlyWrong) {
    const pos = findGroupBoxPosition(idx)
    if (pos) {
      groupIndices.forEach(i => { const a = annotations.value[i]; a.isCorrect = false; a.markText = '×'; a.boxX = pos.boxX; a.boxY = pos.boxY; a.boxW = pos.boxW; a.boxH = pos.boxH })
      const wrapper = editorWrapper.value
      if (wrapper) {
        nextTick(() => {
          const boxEl = wrapper.querySelector(`[data-box-id="box-${pageKey.value}-${groupIndices[0]}"]`)
          if (boxEl) { const contentEl = boxEl.querySelector('.box-content'); if (contentEl) contentEl.innerHTML = ann.boxText }
        })
      }
    }
  } else {
    groupIndices.forEach(i => { const a = annotations.value[i]; a.isCorrect = true; a.markText = '✓' })
  }
}

const initAnnotations = () => {
  const result = currentResult.value
  if (!result || !result.questions || !bgImage.value) return
  const img = bgImage.value, wrapper = editorWrapper.value
  if (!wrapper) return
  const naturalW = img.naturalWidth, naturalH = img.naturalHeight
  const displayW = img.clientWidth, displayH = img.clientHeight
  const scale = displayW / naturalW
  const offsetX = getOffsetX()
  imageScale.value = scale
  const imageAreaW = wrapper.clientWidth - 2 * offsetX
  const { leftMarginR, rightMarginL, topEdge, bottomEdge } = getBoundaries()
  const boxW = 200, boxH = 70
  const list = result.questions.map((q, idx) => {
    const box = q.bbox || { x: 0, y: 0, width: 100, height: 30 }
    let bx = box.x, by = box.y, bw = box.width, bh = box.height
    if (bx <= 1000 && by <= 1000 && bw <= 1000 && bh <= 1000) {
      bx = Math.round(bx * naturalW / 1000); by = Math.round(by * naturalH / 1000)
      bw = Math.round(bw * naturalW / 1000); bh = Math.round(bh * naturalH / 1000)
    }
    const displayX = Math.round(bx * scale), displayY = Math.round(by * scale)
    const displayWBox = Math.round(bw * scale), displayHBox = Math.round(bh * scale)
    const questionCenterX = offsetX + displayX + displayWBox / 2
    const isLeftSide = questionCenterX < (offsetX + imageAreaW / 2)
    let boxX, boxY
    if (isLeftSide) boxX = Math.max(5, leftMarginR - boxW)
    else boxX = rightMarginL
    boxY = Math.max(topEdge, Math.min(displayY + displayHBox / 2 - boxH / 2, bottomEdge - boxH))
    boxX = Math.max(5, Math.min(boxX, wrapper.clientWidth - boxW - 5))
    const boxHtml = buildBoxHtml(q)
    return { ...q, questionNumber: q.questionNumber || (idx + 1), boxText: boxHtml, markText: q.result === '正确' ? '✓' : '×', isCorrect: q.result === '正确', offsetX, displayX, displayY, displayW: displayWBox, displayH: displayHBox, boxX, boxY, boxW, boxH, dragging: false, dragStartX: 0, dragStartY: 0, resizeStartW: 0, resizeStartH: 0 }
  })
  for (let i = 0; i < list.length; i++) {
    if (list[i].isCorrect) continue
    for (let j = 0; j < i; j++) {
      if (list[j].isCorrect) continue
      const sameSide = (list[i].boxX < offsetX && list[j].boxX < offsetX) || (list[i].boxX >= offsetX + imageAreaW && list[j].boxX >= offsetX + imageAreaW)
      if (!sameSide) continue
      const a = list[i], b = list[j]
      if (a.boxX < b.boxX + b.boxW && a.boxX + a.boxW > b.boxX && a.boxY < b.boxY + b.boxH && a.boxY + a.boxH > b.boxY) {
        a.boxY = b.boxY + b.boxH + 8
        if (a.boxY + a.boxH > bottomEdge) a.boxY = bottomEdge - a.boxH
      }
    }
  }
  annotations.value = list
  const summaryY = bottomEdge + 3
  summaryBox.value = { x: 0, y: summaryY, w: wrapper.clientWidth, h: 100 }

  nextTick(() => {
    for (let i = 0; i < list.length; i++) {
      const boxEl = wrapper.querySelector(`[data-box-id="box-${pageKey.value}-${i}"]`)
      if (boxEl) { const contentEl = boxEl.querySelector('.box-content'); if (contentEl) contentEl.innerHTML = list[i].boxText }
    }
    if (summaryContentRef.value && result.overallComment) {
      summaryContentRef.value.textContent = result.overallComment
    }
  })
}

const getMarkStyle = (ann) => {
  const offsetX = getOffsetX()
  const left = offsetX + ann.displayX + (ann.displayW - MARK_SIZE) / 2
  const top = ann.displayY + (ann.displayH - MARK_SIZE) / 2
  return { left: left + 'px', top: top + 'px', width: MARK_SIZE + 'px', height: MARK_SIZE + 'px', fontSize: '18px', lineHeight: MARK_SIZE + 'px', cursor: 'pointer' }
}

const getBoxStyle = (ann) => ({ left: ann.boxX + 'px', top: ann.boxY + 'px', width: ann.boxW + 'px', height: ann.boxH + 'px' })

const getSummaryStyle = () => {
  if (!summaryBox.value) return {}
  return { left: summaryBox.value.x + 'px', top: summaryBox.value.y + 'px', width: summaryBox.value.w + 'px', height: summaryBox.value.h + 'px' }
}

const getClosestPoints = (rectA, rectB) => {
  const aL = rectA.x, aR = rectA.x + rectA.w, aT = rectA.y, aB = rectA.y + rectA.h
  const bL = rectB.x, bR = rectB.x + rectB.w, bT = rectB.y, bB = rectB.y + rectB.h
  const overlapX = !(aR < bL || bR < aL), overlapY = !(aB < bT || bB < aT)
  let cx, cy, px, py
  if (overlapX) { const ol = Math.max(aL, bL), or = Math.min(aR, bR); cx = (ol + or) / 2; px = cx }
  else if (aR < bL) { cx = aR; px = bL } else { cx = aL; px = bR }
  if (overlapY) { const ot = Math.max(aT, bT), ob = Math.min(aB, bB); cy = (ot + ob) / 2; py = cy }
  else if (aB < bT) { cy = aB; py = bT } else { cy = aT; py = bB }
  return { x1: cx, y1: cy, x2: px, y2: py }
}

const getLineStyle = (ann) => {
  const rectA = { x: ann.offsetX + ann.displayX, y: ann.displayY, w: ann.displayW, h: ann.displayH }
  const rectB = { x: ann.boxX, y: ann.boxY, w: ann.boxW, h: ann.boxH }
  const { x1, y1, x2, y2 } = getClosestPoints(rectA, rectB)
  const dx = x2 - x1, dy = y2 - y1
  const length = Math.sqrt(dx * dx + dy * dy)
  const angle = Math.atan2(dy, dx) * (180 / Math.PI)
  return { left: x1 + 'px', top: y1 + 'px', width: length + 'px', transform: `rotate(${angle}deg)`, transformOrigin: '0 0' }
}

const startDrag = (e, idx) => {
  const ann = annotations.value[idx]
  if (!ann) return
  ann.dragging = true
  ann.dragStartX = e.clientX - ann.boxX; ann.dragStartY = e.clientY - ann.boxY
  const onMove = (ev) => {
    if (!ann.dragging) return
    const wrapper = editorWrapper.value
    if (!wrapper) return
    const { leftMarginR, rightMarginL, topEdge, bottomEdge, wrapperW } = getBoundaries()
    let newX = ev.clientX - ann.dragStartX, newY = ev.clientY - ann.dragStartY
    const isOnLeft = ann.boxX + ann.boxW / 2 < wrapperW / 2
    if (isOnLeft) newX = Math.max(5, Math.min(leftMarginR - ann.boxW, newX))
    else newX = Math.max(rightMarginL, Math.min(wrapperW - ann.boxW - 5, newX))
    newY = Math.max(topEdge, Math.min(bottomEdge - ann.boxH, newY))
    ann.boxX = newX; ann.boxY = newY
  }
  const onUp = () => { ann.dragging = false; document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp) }
  document.addEventListener('mousemove', onMove); document.addEventListener('mouseup', onUp)
}

const startResize = (e, idx) => {
  e.stopPropagation()
  const ann = annotations.value[idx]
  if (!ann) return
  ann.resizeStartW = e.clientX; ann.resizeStartH = e.clientY
  const onMove = (ev) => {
    const dw = ev.clientX - ann.resizeStartW, dh = ev.clientY - ann.resizeStartH
    ann.boxW = Math.max(150, ann.boxW + dw); ann.boxH = Math.max(60, ann.boxH + dh)
    ann.resizeStartW = ev.clientX; ann.resizeStartH = ev.clientY
  }
  const onUp = () => { document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp) }
  document.addEventListener('mousemove', onMove); document.addEventListener('mouseup', onUp)
}

const onMarkEdit = (idx, event) => { if (annotations.value[idx]) annotations.value[idx].markText = event.target.innerText || '' }
const onMarkBlur = (idx, event) => {}
const onBoxEdit = (idx, event) => { if (annotations.value[idx]) annotations.value[idx].boxText = event.target.innerHTML || '' }
const onBoxBlur = (idx, event) => {}
const onSummaryEdit = (event) => { if (summaryBox.value) summaryBox.value.text = event.target.innerText || '' }
const resetAnnotations = () => { initAnnotations() }

const downloadCurrentPage = async () => {
  const result = currentResult.value
  if (!result || !bgImage.value) return
  const img = bgImage.value, wrapper = editorWrapper.value
  if (!wrapper) return
  const naturalW = img.naturalWidth, naturalH = img.naturalHeight
  const scale = imageScale.value, offsetX = getOffsetX()
  const extendW = Math.max(Math.round(offsetX / scale), 60)
  let summaryNaturalH = 0
  if (summaryBox.value) summaryNaturalH = Math.round(summaryBox.value.h / scale) + 30
  const canvasW = naturalW + 2 * extendW, canvasH = naturalH + 20 + summaryNaturalH
  const canvas = document.createElement('canvas')
  canvas.width = canvasW; canvas.height = canvasH
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#ffffff'; ctx.fillRect(0, 0, canvasW, canvasH)
  const imgOffsetX = extendW, imgOffsetY = 5
  ctx.drawImage(img, imgOffsetX, imgOffsetY, naturalW, naturalH)
  const paperLeft = imgOffsetX, paperRight = imgOffsetX + naturalW, paperBottom = imgOffsetY + naturalH

  annotations.value.forEach((ann, idx) => {
    const bx = Math.round(ann.displayX / scale) + imgOffsetX
    const by = Math.round(ann.displayY / scale) + imgOffsetY
    const bw = Math.round(ann.displayW / scale), bh = Math.round(ann.displayH / scale)
    ctx.font = 'bold 18px sans-serif'
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle'
    ctx.fillStyle = ann.isCorrect ? '#22c55e' : '#dc2626'
    ctx.fillText(ann.markText || (ann.isCorrect ? '✓' : '×'), bx + bw / 2, by + bh / 2)
    if (!ann.isCorrect) {
      ctx.strokeStyle = '#dc2626'; ctx.lineWidth = 2
      ctx.beginPath(); ctx.moveTo(bx, by + bh + 3); ctx.lineTo(bx + bw, by + bh + 3); ctx.stroke()
    }
    if (!ann.isCorrect) {
      let boxHtml = ann.boxText || ''
      if (!boxHtml) {
        const wrapperEl = editorWrapper.value
        if (wrapperEl) {
          const boxEl = wrapperEl.querySelector(`[data-box-id="box-${pageKey.value}-${idx}"]`)
          if (boxEl) { const contentEl = boxEl.querySelector('.box-content'); if (contentEl) boxHtml = contentEl.innerHTML || '' }
        }
      }
      const isOnLeft = ann.boxX + ann.boxW / 2 < wrapper.clientWidth / 2
      const gapLeft = 8, gapRight = 8, margin = 5
      let canvasBoxW
      if (isOnLeft) canvasBoxW = paperLeft - gapLeft - margin
      else canvasBoxW = canvasW - paperRight - gapRight - margin
      canvasBoxW = Math.max(100, canvasBoxW)
      const baseFontSize = Math.max(Math.round(19), 18)
      const lineH = baseFontSize * 1.5
      const paddingX = 6, paddingY = 5
      const maxLineW = canvasBoxW - paddingX * 2
      const tempDiv = document.createElement('div')
      tempDiv.innerHTML = boxHtml
      const allText = tempDiv.innerText || tempDiv.textContent || ''
      let mainText = ''
      if (allText.includes('【解析/错因】')) mainText = allText.replace('【解析/错因】', '').trim()
      else if (allText) mainText = allText.trim()
      const wrapText = (text) => {
        if (!text) return []
        const lines = []
        // 判断是否为中文为主（包含中文字符）
        const hasChinese = /[\u4e00-\u9fff]/.test(text)
        
        if (hasChinese) {
          // 中文为主：按字符数切分，每行填满固定字符数，保证各行右边界对齐
          let pos = 0
          while (pos < text.length) {
            // 逐字测量找到刚好不超过 maxLineW 的位置
            let end = pos + 1
            while (end <= text.length && ctx.measureText(text.substring(pos, end)).width <= maxLineW) {
              end++
            }
            lines.push(text.substring(pos, end - 1))
            pos = end - 1
          }
        } else {
          // 非中文：按空格分词 + 逐字测量
          const tokens = text.split(/(\s+)/); let cur = ''
          tokens.forEach(token => {
            const tokenW = ctx.measureText(token).width
            if (tokenW > maxLineW) { let cl = ''; for (const ch of token) { const tl = cur + cl + ch; if (ctx.measureText(tl).width > maxLineW && (cur + cl).trim() !== '') { if (cl) lines.push(cur + cl); else lines.push(cur); cur = ''; cl = ch } else { cl += ch } } cur += cl }
            else { const tl = cur + token; if (ctx.measureText(tl).width > maxLineW && cur !== '') { lines.push(cur); cur = token } else { cur = tl } }
          })
          if (cur !== '') lines.push(cur)
        }
        return lines
      }
      const contentLines = mainText ? wrapText(mainText) : []
      const drawLines = []
      if (contentLines.length > 0) { drawLines.push({ text: '【解析/错因】' + contentLines[0], color: '#dc2626' }); for (let k = 1; k < contentLines.length; k++) drawLines.push({ text: contentLines[k], color: '#dc2626' }) }
      else { drawLines.push({ text: '双击编辑...', color: '#4b5563' }) }
      const canvasBoxH = Math.max(26, Math.round(drawLines.length * lineH + paddingY * 2))
      let boxX, boxY
      if (isOnLeft) boxX = margin
      else boxX = paperRight + gapRight
      boxY = Math.round(ann.boxY / scale) + imgOffsetY
      boxY = Math.max(3, Math.min(boxY, paperBottom - canvasBoxH - 3))
      ctx.fillStyle = 'rgba(255, 255, 255, 0.92)'
      ctx.fillRect(boxX, boxY, canvasBoxW, canvasBoxH)
      ctx.strokeStyle = '#ef4444'; ctx.lineWidth = 1.5
      ctx.strokeRect(boxX, boxY, canvasBoxW, canvasBoxH)
      ctx.textAlign = 'left'; ctx.textBaseline = 'top'
      ctx.font = `${baseFontSize}px sans-serif`
      drawLines.forEach((dl, li) => { const yPos = boxY + paddingY + li * lineH; if (dl.text) { ctx.fillStyle = dl.color || '#4b5563'; ctx.fillText(dl.text, boxX + paddingX, yPos) } })
      const linePoints = getClosestPoints({ x: bx, y: by, w: bw, h: bh }, { x: boxX, y: boxY, w: canvasBoxW, h: canvasBoxH })
      ctx.strokeStyle = '#ef4444'; ctx.lineWidth = 1.5
      ctx.setLineDash([3, 3])
      ctx.beginPath(); ctx.moveTo(linePoints.x1, linePoints.y1); ctx.lineTo(linePoints.x2, linePoints.y2); ctx.stroke()
      ctx.setLineDash([])
    }
  })

  if (summaryBox.value) {
    let summaryText = summaryBox.value.text || ''
    if (!summaryText && summaryContentRef.value) summaryText = summaryContentRef.value.innerText || ''
    if (!summaryText && result.overallComment) summaryText = result.overallComment
    const sFontSize = Math.max(Math.round(22), 20)
    ctx.font = `${sFontSize}px sans-serif`
    const sLineH = sFontSize * 1.5
    const sPaddingX = 16, sPaddingY = 10
    const sTextMaxW = canvasW - sPaddingX * 2
    const sContentLines = []
    if (summaryText) {
      const paragraphs = summaryText.split('\n')
      paragraphs.forEach(para => {
        if (!para) { sContentLines.push(''); return }
        const tokens = para.split(/(\s+)/); let cur = ''
        tokens.forEach(token => {
          const tokenW = ctx.measureText(token).width
          if (tokenW > sTextMaxW) { let cl = ''; for (const ch of token) { const tl = cur + cl + ch; if (ctx.measureText(tl).width > sTextMaxW && (cur + cl).trim() !== '') { if (cl) sContentLines.push(cur + cl); else sContentLines.push(cur); cur = ''; cl = ch } else { cl += ch } } cur += cl }
          else { const tl = cur + token; if (ctx.measureText(tl).width > sTextMaxW && cur !== '') { sContentLines.push(cur); cur = token } else { cur = tl } }
        })
        if (cur !== '') sContentLines.push(cur)
      })
    }
    const titleH = sFontSize + 6
    const contentH = sContentLines.length * sLineH
    const sBoxH = Math.max(50, sPaddingY * 2 + titleH + contentH)
    const sBoxW = canvasW, sBoxX = 0, sBoxY = paperBottom + 3
    ctx.fillStyle = '#f8fafc'; ctx.fillRect(sBoxX, sBoxY, sBoxW, sBoxH)
    ctx.strokeStyle = '#94a3b8'; ctx.lineWidth = 1.5; ctx.strokeRect(sBoxX, sBoxY, sBoxW, sBoxH)
    ctx.font = `bold ${sFontSize}px sans-serif`
    ctx.fillStyle = '#374151'; ctx.textAlign = 'center'; ctx.textBaseline = 'top'
    ctx.fillText(' 整体评价', sBoxX + sBoxW / 2, sBoxY + sPaddingY)
    ctx.strokeStyle = '#e2e8f0'; ctx.lineWidth = 1
    ctx.beginPath(); ctx.moveTo(sBoxX + sPaddingX, sBoxY + sPaddingY + sFontSize + 6); ctx.lineTo(sBoxX + sBoxW - sPaddingX, sBoxY + sPaddingY + sFontSize + 6); ctx.stroke()
    if (sContentLines.length > 0) {
      ctx.font = `${sFontSize}px sans-serif`; ctx.fillStyle = '#4b5563'; ctx.textAlign = 'left'; ctx.textBaseline = 'top'
      const textStartY = sBoxY + sPaddingY + sFontSize + 12
      sContentLines.forEach((line, li) => { ctx.fillText(line, sBoxX + sPaddingX, textStartY + li * sLineH) })
    }
    ctx.strokeStyle = '#cbd5e1'; ctx.lineWidth = 1; ctx.setLineDash([5, 5])
    ctx.beginPath(); ctx.moveTo(paperLeft, paperBottom + 5); ctx.lineTo(paperRight, paperBottom + 5); ctx.stroke()
    ctx.setLineDash([])
    summaryBox.value.h = Math.round(sBoxH * scale)
  }

  const link = document.createElement('a')
  link.download = `批阅结果_第${currentPage.value + 1}页.png`
  link.href = canvas.toDataURL('image/png')
  document.body.appendChild(link); link.click(); document.body.removeChild(link)
}
</script>

<style scoped>
.paper-grading {
  width: 100%;
}

/* 页面标签导航 */
.page-tabs { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
.page-tab { padding: 8px 16px; border: 1px solid #d1d5db; border-radius: 8px; background: #fff; cursor: pointer; font-size: 13px; font-weight: 500; color: #4b5563; transition: all .2s; display: flex; flex-direction: column; align-items: center; }
.page-tab:hover { border-color: #2563eb; color: #2563eb; }
.page-tab.active { background: #2563eb; color: #fff; border-color: #2563eb; }
.page-filename { font-size: 10px; opacity: 0.7; margin-top: 2px; }

.input-section { background: #fff; border-radius: 12px; padding: 28px 32px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); border: 1px solid #e5e7eb; }
.form-group { margin-bottom: 20px; }
.form-label { display: block; font-size: 14px; font-weight: 600; color: #374151; margin-bottom: 8px; }
.file-upload-wrapper { position: relative; }
.file-input { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); border: 0; }
.file-upload-btn { display: inline-flex; align-items: center; padding: 10px 20px; border: 2px dashed #d1d5db; border-radius: 8px; background: #f9fafb; color: #4b5563; font-size: 14px; cursor: pointer; transition: all .2s; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-upload-btn:hover { border-color: #2563eb; background: #eff6ff; color: #2563eb; }
.file-list { margin-top: 8px; }
.file-item { font-size: 12px; color: #6b7280; padding: 2px 0; }
.form-textarea { width: 100%; padding: 10px 14px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px; line-height: 1.6; color: #1f2937; background: #fff; transition: border-color .2s; resize: vertical; font-family: inherit; }
.form-textarea:focus { outline: none; border-color: #2563eb; box-shadow: 0 0 0 3px rgba(37,99,235,0.1); }
.form-textarea::placeholder { color: #9ca3af; }

.btn-primary { display: inline-flex; align-items: center; justify-content: center; width: 100%; padding: 12px 24px; background: #2563eb; color: #fff; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; transition: background .2s; }
.btn-primary:hover:not(:disabled) { background: #1d4ed8; }
.btn-primary:disabled { opacity: .6; cursor: not-allowed; }

.spinner { display: inline-block; width: 18px; height: 18px; margin-right: 8px; border: 2px solid rgba(255,255,255,0.3); border-top-color: #fff; border-radius: 50%; animation: spin .6s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.result-section { margin-top: 0; background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); border: 1px solid #e5e7eb; overflow: hidden; }
.result-header { display: flex; align-items: center; padding: 20px 28px 0; gap: 10px; }
.result-header h2 { font-size: 18px; font-weight: 700; color: #111827; }
.badge-editable { display: inline-flex; font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 10px; background: #dbeafe; color: #2563eb; }
.result-body { padding: 20px 28px 28px; }

.editor-section { border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden; background: #f9fafb; }
.editor-canvas-wrapper { position: relative; overflow: auto; padding: 0 28%; background: #e8ecf1; min-height: 300px; }
.editor-bg-image { display: block; max-width: 100%; height: auto; }
.annotations-layer { position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; }

.mark-overlay { position: absolute; display: flex; align-items: center; justify-content: center; font-weight: bold; z-index: 5; text-shadow: 0 0 4px rgba(255,255,255,0.95), 0 0 2px #fff; pointer-events: auto; }
.mark-correct { color: #22c55e; }
.mark-wrong { color: #dc2626; }
.mark-text { outline: none; cursor: pointer; min-width: 8px; user-select: text; }

.annotation-box {
  position: absolute;
  background: rgba(255,255,255,0.92);
  border: 1.5px solid #ef4444;
  border-radius: 4px;
  box-shadow: 0 2px 6px rgba(239,68,68,0.08);
  pointer-events: auto;
  z-index: 10;
  display: flex;
  flex-direction: column;
  cursor: move;
  transition: box-shadow .2s;
}
.annotation-box:hover { box-shadow: 0 3px 10px rgba(239,68,68,0.12); }
.annotation-box.dragging { box-shadow: 0 4px 14px rgba(239,68,68,0.15); opacity: .95; }

.box-content { flex: 1; padding: 5px 8px; font-size: 13px; line-height: 1.4; color: #4b5563; overflow-y: auto; cursor: text; outline: none; white-space: pre-wrap; word-break: break-word; user-select: text; text-align: left; }
.box-content:focus { background: #f9fafb; border-radius: 3px; }
.box-content :deep(.c-parse) { color: #16a34a; }
.box-content :deep(.c-error) { color: #dc2626; }
.box-content :deep(.c-parse-error) { color: #dc2626; }

.resize-handle { position: absolute; right: 0; bottom: 0; width: 12px; height: 12px; cursor: nwse-resize; pointer-events: auto; z-index: 11; }
.resize-handle::after { content: ''; position: absolute; right: 1px; bottom: 1px; width: 7px; height: 7px; border-right: 1.5px solid #ef4444; border-bottom: 1.5px solid #ef4444; }

.connector-line { position: absolute; height: 2px; background: #ef4444; z-index: 6; pointer-events: none; opacity: 0.7; }

.summary-box { position: absolute; background: #f8fafc; border: 1.5px solid #94a3b8; border-radius: 6px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); pointer-events: auto; z-index: 20; display: flex; flex-direction: column; overflow: hidden; }
.summary-label { padding: 6px 12px; font-size: 16px; font-weight: 600; color: #374151; background: #f1f5f9; border-bottom: 1px solid #e2e8f0; user-select: none; text-align: center; }
.summary-content { flex: 1; padding: 8px 12px; font-size: 14px; line-height: 1.5; color: #4b5563; overflow-y: auto; cursor: text; outline: none; white-space: pre-wrap; word-break: break-word; user-select: text; text-align: left; }
.summary-content:focus { background: #fff; }

.editor-actions { display: flex; gap: 12px; justify-content: center; margin-top: 16px; }
.btn-download { display: inline-flex; align-items: center; padding: 10px 20px; background: #059669; color: #fff; border: none; border-radius: 8px; font-size: 14px; font-weight: 500; cursor: pointer; transition: background .2s; }
.btn-download:hover { background: #047857; }
.btn-reset { display: inline-flex; align-items: center; padding: 10px 20px; background: #f3f4f6; color: #4b5563; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px; font-weight: 500; cursor: pointer; transition: all .2s; }
.btn-reset:hover { background: #e5e7eb; border-color: #9ca3af; }
</style>