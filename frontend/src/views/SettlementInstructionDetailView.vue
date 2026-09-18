<template>
  <div class="page">
    <h2 class="page-title">结算指示详情</h2>
    <p class="page-desc">预览展示每个会员的结算方向与金额；操作员可释放，只读用户仅可预览/查看</p>

    <div class="toolbar">
      <el-button @click="$router.back()">返回</el-button>
      <el-button @click="load">刷新</el-button>
      <el-button
        v-if="canPreview"
        type="primary"
        :disabled="!runCompleted"
        :loading="previewing"
        @click="preview"
      >生成指示预览</el-button>
      <el-button
        v-if="canRelease"
        type="warning"
        :disabled="!auth.isOperator"
        :loading="releasing"
        @click="release"
      >释放结算指示</el-button>
      <el-tag v-if="detail" :type="instructionTagType(detail.instructionStatus)" size="large">
        {{ instructionStatusText(detail.instructionStatus) }}
      </el-tag>
      <el-tag v-if="detail && !runCompleted" type="info" size="large">批次未完成，不能生成</el-tag>
      <el-tag v-if="detail && runCompleted && !auth.isOperator && canRelease" type="info" size="large">
        只读用户不可释放
      </el-tag>
    </div>

    <div class="card-panel" v-loading="loading">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Run ID"><span class="mono">{{ detail.run.runId }}</span></el-descriptions-item>
          <el-descriptions-item label="批次状态">
            <el-tag :type="detail.run.status === 'COMPLETED' ? 'success' : 'info'">{{ detail.run.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="交割日">{{ detail.run.settleDate }}</el-descriptions-item>
          <el-descriptions-item label="币种">{{ detail.run.currency }}</el-descriptions-item>
          <el-descriptions-item label="应付合计">{{ detail.totalPay }}</el-descriptions-item>
          <el-descriptions-item label="应收合计">{{ detail.totalReceive }}</el-descriptions-item>
        </el-descriptions>

        <h3 style="margin:20px 0 10px">结算指示（会员 / 金额）</h3>
        <el-table :data="detail.instructions" stripe>
          <el-table-column prop="memberId" label="会员 ID" min-width="200">
            <template #default="{ row }">
              <span class="mono">{{ row.memberId }}</span>
              <div>{{ row.memberName }}</div>
            </template>
          </el-table-column>
          <el-table-column label="方向" width="90">
            <template #default="{ row }">
              <el-tag :type="directionTagType(row.direction)" size="small">{{ directionText(row.direction) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="currency" label="币种" width="90" />
          <el-table-column prop="amount" label="金额" min-width="160" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 'RELEASED' ? 'success' : 'warning'" size="small">
                {{ row.status === 'RELEASED' ? '已释放' : '已预览' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="预览时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.previewedAt) }}</template>
          </el-table-column>
          <el-table-column label="释放时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.releasedAt) }}</template>
          </el-table-column>
          <el-table-column prop="releasedBy" label="释放人" min-width="120" />
        </el-table>
        <el-empty
          v-if="detail.instructions.length === 0"
          description="尚未生成预览（仅轧差完成的批次可生成）"
        />
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import {
  instructionStatusText,
  instructionTagType,
  directionText,
  directionTagType
} from '../instruction-status'

const auth = useAuthStore()
const route = useRoute()
const loading = ref(false)
const previewing = ref(false)
const releasing = ref(false)
const detail = ref(null)

const runCompleted = computed(() => detail.value?.run?.status === 'COMPLETED')
const canPreview = computed(
  () => detail.value && runCompleted.value && detail.value.instructionStatus === 'NONE'
)
const canRelease = computed(
  () =>
    detail.value &&
    runCompleted.value &&
    (detail.value.instructionStatus === 'PREVIEWED' ||
      detail.value.instructionStatus === 'PARTIALLY_RELEASED')
)

function formatTime(v) {
  return v ? new Date(v).toLocaleString() : '-'
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get(`/settlement-instructions/runs/${route.params.id}`)
    detail.value = data
  } finally {
    loading.value = false
  }
}

async function preview() {
  previewing.value = true
  try {
    const { data } = await api.post(`/settlement-instructions/runs/${route.params.id}/preview`)
    detail.value = data
    ElMessage.success(`已生成 ${data.instructions.length} 笔结算指示预览`)
  } finally {
    previewing.value = false
  }
}

async function release() {
  try {
    await ElMessageBox.confirm('释放后结算指示将正式下发，确认释放？', '释放确认', {
      type: 'warning',
      confirmButtonText: '确认释放',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  releasing.value = true
  try {
    const { data } = await api.post(`/settlement-instructions/runs/${route.params.id}/release`)
    detail.value = data
    ElMessage.success('结算指示已释放')
  } finally {
    releasing.value = false
  }
}

onMounted(load)
</script>
