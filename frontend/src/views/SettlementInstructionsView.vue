<template>
  <div class="page">
    <h2 class="page-title">结算指示</h2>
    <p class="page-desc">轧差完成的批次可生成结算指示预览（会员与金额），操作员复核后释放</p>

    <div class="toolbar">
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="card-panel" v-loading="loading">
      <el-table :data="runs" stripe>
        <el-table-column prop="run.runId" label="Run ID" min-width="200">
          <template #default="{ row }">
            <router-link class="mono" :to="`/settlement-instructions/${row.run.runId}`">{{ row.run.runId }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="run.settleDate" label="交割日" width="120" />
        <el-table-column prop="run.currency" label="币种" width="90" />
        <el-table-column label="批次状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.run.status === 'COMPLETED' ? 'success' : row.run.status === 'FAILED' ? 'danger' : 'info'">
              {{ row.run.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="指示状态" width="130">
          <template #default="{ row }">
            <el-tag :type="instructionTagType(row.instructionStatus)">
              {{ instructionStatusText(row.instructionStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="instructionCount" label="指示笔数" width="90" />
        <el-table-column prop="totalPay" label="应付合计" min-width="140" />
        <el-table-column prop="totalReceive" label="应收合计" min-width="140" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/settlement-instructions/${row.run.runId}`)">
              {{ row.run.status === 'COMPLETED' && row.instructionStatus === 'NONE' ? '生成预览' : '查看' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import api from '../api/client'
import { instructionStatusText, instructionTagType } from '../instruction-status'

const loading = ref(false)
const runs = ref([])

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/settlement-instructions/runs')
    runs.value = data
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
