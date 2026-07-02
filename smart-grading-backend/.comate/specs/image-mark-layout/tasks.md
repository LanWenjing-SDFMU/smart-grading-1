# ImageMarkService 布局管理增强任务

## 任务清单

- [x] Task 1: 修改 `markImageWithUnifiedResult` 方法
  - 1.1: 添加 obstacles 和 usedAnnotationRects 初始化
  - 1.2: 在遍历前收集所有题目边界框到 obstacles
  - 1.3: 调用 `drawAnnotationWithPlacement` 替换原有调用
  - 1.4: 移除 `drawUnifiedAnnotations` 和 `drawUnifiedAnnotationsWithLine` 方法

- [x] Task 2: 新增 `convertBBox` 辅助方法
  - 2.1: 实现归一化坐标转换逻辑
  - 2.2: 实现边界裁剪逻辑
  - 2.3: 处理无效矩形返回 null

- [x] Task 3: 新增 `drawAnnotationWithPlacement` 核心方法
  - 3.1: 计算文本尺寸和最大宽度
  - 3.2: 生成四个方向候选位置（右、下、左、上）
  - 3.3: 实现碰撞检测找到可用位置
  - 3.4: 绘制半透明背景框
  - 3.5: 绘制文本和连线
  - 3.6: 返回放置矩形

- [x] Task 4: 新增 `isOverlapping` 碰撞检测方法
  - 4.1: 实现矩形列表遍历
  - 4.2: 使用 intersects 判断重叠

- [x] Task 5: 验证代码完整性
  - 5.1: 检查所有 import 语句完整
  - 5.2: 验证方法签名正确
  - 5.3: 确保无语法错误
