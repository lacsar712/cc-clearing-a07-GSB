<template>
  <div class="page">
    <h2 class="page-title">结算指示</h2>
    <p class="page-desc">轧差完成后按批次生成结算指示预览（会员与金额），操作员复核后释放；只读用户仅可预览</p>

    <div class="card-panel">
      <div class="toolbar">
        <el-select
          v-model="selectedRunId"
          placeholder="选择轧差批次"
          style="width: 420px"
          :loading="loadingRuns"
          @change="onSelectRun"
        >
          <el-option
            v-for="r in runs"
            :key="r.runId"
            :value="r.runId"
            :label="`${r.settleDate} · ${r.currency} · ${r.status} · ${r.runId.slice(0, 8)}`"
          >
            <span>{{ r.settleDate }} {{ r.currency }}</span>
            <el-tag
              size="small"
              :type="r.status === 'COMPLETED' ? 'success' : r.status === 'FAILED' ? 'danger' : 'info'"
              style="margin: 0 8px"
            >{{ r.status }}</el-tag>
            <span class="mono" style="color: var(--muted)">{{ r.runId.slice(0, 8) }}</span>
          </el-option>
        </el-select>
        <el-button
          type="primary"
          :disabled="!selectedRun || selectedRun.status !== 'COMPLETED' || busy"
          :loading="busy"
          @click="preview"
        >生成指示预览</el-button>
        <el-button
          type="warning"
          :disabled="!canRelease || busy"
          :loading="busy"
          @click="release"
        >释放指示</el-button>
        <el-button :disabled="!selectedRunId || busy" @click="loadInstructions">刷新</el-button>
        <el-tag v-if="!auth.isOperator" type="info">只读用户：仅可预览，不能释放</el-tag>
      </div>
      <div v-if="selectedRun && selectedRun.status !== 'COMPLETED'" class="hint">
        该批次状态为 {{ selectedRun.status }}，仅 COMPLETED 批次可生成结算指示。
      </div>
    </div>

    <div v-if="selectedRunId" class="card-panel" style="margin-top:16px" v-loading="loading">
      <div class="toolbar" style="justify-content: space-between; margin-bottom: 12px">
        <div>
          <strong>批次 {{ selectedRunId.slice(0, 8) }}</strong>
          <el-tag style="margin-left: 8px" :type="setStateTag.type">{{ setStateTag.text }}</el-tag>
        </div>
        <div v-if="set" style="color: var(--muted); font-size: 13px">
          交割日 {{ set.run.settleDate }} · 币种 {{ set.run.currency }} · 共 {{ set.instructions.length }} 条
        </div>
      </div>

      <el-empty v-if="set && set.instructions.length === 0" description="尚未生成指示预览" />

      <el-table v-if="set && set.instructions.length" :data="set.instructions" stripe :row-class-name="rowClass">
        <el-table-column label="会员" min-width="220">
          <template #default="{ row }">
            <div>{{ row.memberName || '—' }}</div>
            <div class="mono" style="color: var(--muted)">{{ row.memberId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="100">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'RECEIVE' ? 'success' : 'danger'" size="small">
              {{ row.direction === 'RECEIVE' ? '应收' : '应付' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column prop="amount" label="金额" min-width="160" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'RELEASED' ? 'warning' : 'info'" size="small">
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
        <el-table-column prop="releasedBy" label="释放人" min-width="120">
          <template #default="{ row }">{{ row.releasedBy || '—' }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()

const runs = ref([])
const selectedRunId = ref(route.query.runId || '')
const set = ref(null)
const loadingRuns = ref(false)
const loading = ref(false)
const busy = ref(false)

const selectedRun = computed(() => runs.value.find((r) => r.runId === selectedRunId.value) || null)
const canRelease = computed(
  () =>
    auth.isOperator &&
    set.value &&
    set.value.instructions.length > 0 &&
    set.value.instructions.some((i) => i.status === 'PREVIEWED')
)
const setStateTag = computed(() => {
  if (!set.value || set.value.instructions.length === 0) {
    return { type: 'info', text: '未预览' }
  }
  if (set.value.released) {
    return { type: 'warning', text: '已释放' }
  }
  return { type: 'success', text: '已预览 · 待释放' }
})

function rowClass({ row }) {
  return row.status === 'RELEASED' ? 'row-released' : ''
}

function formatTime(v) {
  return v ? new Date(v).toLocaleString() : '—'
}

async function loadRuns() {
  loadingRuns.value = true
  try {
    const { data } = await api.get('/netting-runs')
    runs.value = data
    if (!selectedRunId.value && data.length) {
      const firstCompleted = data.find((r) => r.status === 'COMPLETED')
      if (firstCompleted) {
        selectedRunId.value = firstCompleted.runId
        await loadInstructions()
      }
    }
  } finally {
    loadingRuns.value = false
  }
}

async function onSelectRun() {
  set.value = null
  await loadInstructions()
}

async function loadInstructions() {
  if (!selectedRunId.value) return
  loading.value = true
  try {
    const { data } = await api.get(`/netting-runs/${selectedRunId.value}/settlement-instructions`)
    set.value = data
  } finally {
    loading.value = false
  }
}

async function preview() {
  if (!selectedRunId.value) return
  busy.value = true
  try {
    const { data } = await api.post(`/netting-runs/${selectedRunId.value}/settlement-instructions/preview`)
    set.value = data
    ElMessage.success(`已生成 ${data.instructions.length} 条结算指示预览`)
  } finally {
    busy.value = false
  }
}

async function release() {
  if (!selectedRunId.value) return
  busy.value = true
  try {
    const { data } = await api.post(`/netting-runs/${selectedRunId.value}/settlement-instructions/release`)
    set.value = data
    ElMessage.success('结算指示已释放')
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  await loadRuns()
  if (selectedRunId.value) {
    await loadInstructions()
  }
})
</script>

<style scoped>
.hint {
  color: #b45309;
  font-size: 13px;
}
:deep(.row-released) {
  background: #fdf6ec;
}
:deep(.row-released td) {
  background: #fdf6ec;
}
</style>
