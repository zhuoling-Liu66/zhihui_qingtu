<template>
  <div class="admin-page">
    <section class="admin-panel">
      <div class="admin-panel__head">
        <div>
          <h2>账号与角色</h2>
          <p>运营账号只能发布学分活动；用户角色调整后需重新登录生效</p>
        </div>
        <div class="admin-filter-actions">
          <el-button plain :loading="staffLoading || userLoading" @click="refreshCurrent">刷新</el-button>
          <el-button v-if="activeTab === 'staff'" type="warning" @click="openCreate">新建运营账号</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="运营账号" name="staff" />
        <el-tab-pane label="用户角色" name="users" />
      </el-tabs>

      <template v-if="activeTab === 'staff'">
        <div class="admin-filters">
          <el-form-item label="关键词">
            <el-input
              v-model="staffKeyword"
              clearable
              placeholder="工号 / 姓名 / 手机号 / 邮箱"
              @keyup.enter="handleStaffSearch"
            />
          </el-form-item>
          <div class="admin-filter-actions">
            <el-button type="warning" @click="handleStaffSearch">查询</el-button>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="admin-filters">
          <el-form-item label="关键词">
            <el-input
              v-model="userKeyword"
              clearable
              placeholder="学号 / 姓名 / 手机号 / 邮箱"
              @keyup.enter="handleUserSearch"
            />
          </el-form-item>
          <el-form-item label="角色">
            <el-select v-model="userRoleFilter" clearable placeholder="全部角色">
              <el-option :value="1" label="学生" />
              <el-option :value="2" label="管理员" />
              <el-option :value="3" label="老师" />
              <el-option :value="4" label="运营人员" />
            </el-select>
          </el-form-item>
          <div class="admin-filter-actions">
            <el-button type="warning" @click="handleUserSearch">查询</el-button>
          </div>
        </div>
      </template>
    </section>

    <section v-if="activeTab === 'staff'" class="admin-panel">
      <div class="admin-panel__head">
        <div>
          <h2>运营人员账号</h2>
          <p>共 {{ staffTotal }} 个账号 · 禁用/注销立即生效（含已登录会话）；运营人员不能审核活动，也不能管理账号</p>
        </div>
      </div>

      <el-table v-loading="staffLoading" :data="staffRows" empty-text="还没有运营人员账号">
        <el-table-column label="运营账号" min-width="170">
          <template #default="{ row }">
            <div class="admin-cell-title">{{ row.userName }}</div>
            <div class="admin-cell-sub"><span class="mono">{{ row.studentNo }}</span></div>
          </template>
        </el-table-column>

        <el-table-column label="联系方式" min-width="200">
          <template #default="{ row }">
            <div>{{ row.userPhone || '未填写手机号' }}</div>
            <div class="admin-cell-sub">{{ row.userEmail || '未填写邮箱' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="角色" width="120">
          <template #default>
            <el-tag type="warning" size="small">运营人员</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="Number(row.accountStatus) === 1 ? 'danger' : 'success'" size="small">
              {{ Number(row.accountStatus) === 1 ? '已禁用' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="160">
          <template #default="{ row }"><span class="mono">{{ formatDateTime(row.createTime) }}</span></template>
        </el-table-column>

        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openReset(row)">重置密码</el-button>
            <el-button
              text
              :type="Number(row.accountStatus) === 1 ? 'success' : 'warning'"
              :loading="statusSavingId === row.studentNo"
              @click="toggleStaffStatus(row)"
            >
              {{ Number(row.accountStatus) === 1 ? '启用' : '禁用' }}
            </el-button>
            <el-button
              text
              type="danger"
              :loading="deletingId === row.studentNo"
              @click="removeStaff(row)"
            >
              注销
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="staffTotal > pageSize"
        class="admin-pagination"
        layout="total, prev, pager, next"
        :current-page="staffPageNo + 1"
        :page-size="pageSize"
        :total="staffTotal"
        @current-change="handleStaffPageChange"
      />
    </section>

    <section v-else class="admin-panel">
      <div class="admin-panel__head">
        <div>
          <h2>用户列表</h2>
          <p>共 {{ userTotal }} 位用户 · 学生与老师权限相同，都可以发布无学分活动</p>
        </div>
      </div>

      <el-table v-loading="userLoading" :data="userRows" empty-text="没有符合条件的用户">
        <el-table-column label="用户" min-width="160">
          <template #default="{ row }">
            <div class="admin-cell-title">{{ row.userName }}</div>
            <div class="admin-cell-sub"><span class="mono">{{ row.studentNo }}</span></div>
          </template>
        </el-table-column>

        <el-table-column label="联系方式" min-width="190">
          <template #default="{ row }">
            <div>{{ row.userPhone || '未填写手机号' }}</div>
            <div class="admin-cell-sub">{{ row.userEmail || '未填写邮箱' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="专业 / 年级" min-width="150">
          <template #default="{ row }">
            <div>{{ row.major || '未填写专业' }}</div>
            <div class="admin-cell-sub">{{ row.grade || '未填写年级' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.userRole)" size="small">
              {{ formatRole(row.userRole) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="注册时间" width="160">
          <template #default="{ row }"><span class="mono">{{ formatDateTime(row.createTime) }}</span></template>
        </el-table-column>

        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              text
              type="primary"
              :disabled="row.studentNo === currentUserId"
              @click="openRoleDialog(row)"
            >
              调整角色
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="userTotal > pageSize"
        class="admin-pagination"
        layout="total, prev, pager, next"
        :current-page="userPageNo + 1"
        :page-size="pageSize"
        :total="userTotal"
        @current-change="handleUserPageChange"
      />
    </section>

    <el-dialog v-model="createVisible" title="新建运营账号" width="520px">
      <p class="admin-dialog-hint">
        该账号只能登录管理端，用于发布学分活动并提交管理员审核。
      </p>
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-position="top"
      >
        <div class="admin-form-grid">
          <el-form-item label="工号" prop="studentNo">
            <el-input v-model="createForm.studentNo" maxlength="20" placeholder="管理端登录工号" />
          </el-form-item>
          <el-form-item label="姓名" prop="userName">
            <el-input v-model="createForm.userName" maxlength="50" />
          </el-form-item>
          <el-form-item label="手机号" prop="userPhone">
            <el-input v-model="createForm.userPhone" maxlength="20" />
          </el-form-item>
          <el-form-item label="邮箱" prop="userEmail">
            <el-input v-model="createForm.userEmail" maxlength="100" />
          </el-form-item>
        </div>
        <el-form-item label="初始密码" prop="password">
          <el-input
            v-model="createForm.password"
            type="password"
            show-password
            maxlength="50"
            placeholder="至少 6 位"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="warning" :loading="creating" @click="submitCreate">创建账号</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="重置密码" width="440px">
      <p class="admin-dialog-hint">
        正在重置 <strong>{{ resetTarget?.userName }}</strong>（{{ resetTarget?.studentNo }}）的密码。
      </p>
      <el-input
        v-model="resetPassword"
        type="password"
        show-password
        maxlength="50"
        placeholder="请输入至少 6 位的新密码"
      />
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="warning" :loading="resetting" @click="submitReset">确认重置</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="调整用户角色" width="480px">
      <p class="admin-dialog-hint">
        正在调整 <strong>{{ roleTarget?.userName }}</strong>（{{ roleTarget?.studentNo }}）的角色。
      </p>
      <el-radio-group v-model="roleValue">
        <el-radio-button :value="1">学生</el-radio-button>
        <el-radio-button :value="2">管理员</el-radio-button>
        <el-radio-button :value="3">老师</el-radio-button>
        <el-radio-button :value="4">运营人员</el-radio-button>
      </el-radio-group>
      <p class="admin-dialog-hint">角色会写入登录令牌，该用户需要重新登录后才会生效。</p>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="warning" :loading="roleSaving" @click="submitRole">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import { useAdminSession } from '@/composables/useAdminSession'
import { formatDateTime, formatRole } from '@/utils/format'

const { user } = useAdminSession()
const currentUserId = computed(() => user.value?.studentNo || user.value?.userId)

const activeTab = ref('staff')
const pageSize = 10

/* ── 运营账号 ────────────────────────────────── */
const staffRows = ref([])
const staffKeyword = ref('')
const staffTotal = ref(0)
const staffPageNo = ref(0)
const staffLoading = ref(false)
let staffLoadToken = 0

/* ── 全部用户 ────────────────────────────────── */
const userRows = ref([])
const userKeyword = ref('')
const userRoleFilter = ref(null)
const userTotal = ref(0)
const userPageNo = ref(0)
const userLoading = ref(false)
let userLoadToken = 0

/* ── 弹窗 ───────────────────────────────────── */
const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref(null)
const createForm = reactive({
  studentNo: '',
  userName: '',
  userPhone: '',
  userEmail: '',
  password: '',
})

const createRules = {
  studentNo: [{ required: true, message: '请填写工号', trigger: 'blur' }],
  userName: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  password: [
    { required: true, message: '请填写初始密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码长度需在6到50之间', trigger: 'blur' },
  ],
  userEmail: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

const resetVisible = ref(false)
const resetting = ref(false)
const resetTarget = ref(null)
const resetPassword = ref('')

/* ── 禁用/启用/注销 ─────────────────────────── */
const statusSavingId = ref(null)
const deletingId = ref(null)

const toggleStaffStatus = async (row) => {
  const disabling = Number(row.accountStatus) !== 1
  if (disabling) {
    const confirmed = await ElMessageBox.confirm(
      `禁用后「${row.userName}」（${row.studentNo}）将立即无法登录，已登录的会话也会失效。可随时重新启用。`,
      '禁用账号',
      { confirmButtonText: '禁用', cancelButtonText: '取消', type: 'warning' },
    ).catch(() => false)
    if (!confirmed) return
  }
  statusSavingId.value = row.studentNo
  try {
    await adminApi.updateAccountStatus(row.studentNo, { accountStatus: disabling ? 1 : 0 })
    ElMessage.success(disabling ? '账号已禁用，立即生效' : '账号已启用，该用户需重新登录')
    await loadStaff()
  } finally {
    statusSavingId.value = null
  }
}

const removeStaff = async (row) => {
  const confirmed = await ElMessageBox.confirm(
    `确定注销「${row.userName}」（${row.studentNo}）吗？注销后该账号无法登录，已登录会话立即失效，历史数据保留。此操作不可恢复。`,
    '注销账号',
    { confirmButtonText: '注销', cancelButtonText: '取消', type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  deletingId.value = row.studentNo
  try {
    await adminApi.deleteStaffAccount(row.studentNo)
    ElMessage.success('账号已注销')
    await loadStaff()
  } finally {
    deletingId.value = null
  }
}

const roleTagType = (role) => {
  if (Number(role) === 2) return 'danger'
  if (Number(role) === 3) return 'warning'
  if (Number(role) === 4) return 'warning'
  return 'info'
}

const roleVisible = ref(false)
const roleSaving = ref(false)
const roleTarget = ref(null)
const roleValue = ref(1)

const loadStaff = async () => {
  const requestId = ++staffLoadToken
  staffLoading.value = true
  try {
    const params = { pageNo: staffPageNo.value, pageSize }
    if (staffKeyword.value.trim()) params.keyword = staffKeyword.value.trim()
    const page = await adminApi.staffAccounts(params)
    if (requestId !== staffLoadToken) return
    staffRows.value = page.records || []
    staffTotal.value = page.totalElements || 0
    if (!staffRows.value.length && staffPageNo.value > 0) {
      staffPageNo.value -= 1
      await loadStaff()
    }
  } finally {
    if (requestId === staffLoadToken) staffLoading.value = false
  }
}

const loadUsers = async () => {
  const requestId = ++userLoadToken
  userLoading.value = true
  try {
    const params = { pageNo: userPageNo.value, pageSize }
    if (userKeyword.value.trim()) params.keyword = userKeyword.value.trim()
    if (userRoleFilter.value !== null && userRoleFilter.value !== '') params.userRole = userRoleFilter.value
    const page = await adminApi.users(params)
    if (requestId !== userLoadToken) return
    userRows.value = page.records || []
    userTotal.value = page.totalElements || 0
    if (!userRows.value.length && userPageNo.value > 0) {
      userPageNo.value -= 1
      await loadUsers()
    }
  } finally {
    if (requestId === userLoadToken) userLoading.value = false
  }
}

const refreshCurrent = () => (activeTab.value === 'staff' ? loadStaff() : loadUsers())

const handleTabChange = (name) => {
  if (name === 'users' && !userRows.value.length) {
    loadUsers()
  }
}

const handleStaffSearch = () => {
  staffPageNo.value = 0
  loadStaff()
}

const handleStaffPageChange = (current) => {
  staffPageNo.value = current - 1
  loadStaff()
}

const handleUserSearch = () => {
  userPageNo.value = 0
  loadUsers()
}

const handleUserPageChange = (current) => {
  userPageNo.value = current - 1
  loadUsers()
}

const openCreate = () => {
  Object.assign(createForm, {
    studentNo: '',
    userName: '',
    userPhone: '',
    userEmail: '',
    password: '',
  })
  createVisible.value = true
  createFormRef.value?.clearValidate()
}

const submitCreate = async () => {
  if (creating.value) return
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    await adminApi.createStaff({
      studentNo: createForm.studentNo.trim(),
      userName: createForm.userName.trim(),
      userPhone: createForm.userPhone.trim() || null,
      userEmail: createForm.userEmail.trim() || null,
      password: createForm.password,
    })
    ElMessage.success('运营人员账号已创建')
    createVisible.value = false
    staffPageNo.value = 0
    await loadStaff()
  } finally {
    creating.value = false
  }
}

const openReset = (row) => {
  resetTarget.value = row
  resetPassword.value = ''
  resetVisible.value = true
}

const submitReset = async () => {
  if (!resetTarget.value || resetting.value) return
  if (resetPassword.value.trim().length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  resetting.value = true
  try {
    await adminApi.resetStaffPassword(resetTarget.value.studentNo, {
      password: resetPassword.value,
    })
    ElMessage.success('密码已重置')
    resetVisible.value = false
  } finally {
    resetting.value = false
  }
}

const openRoleDialog = (row) => {
  roleTarget.value = row
  roleValue.value = row.userRole
  roleVisible.value = true
}

const submitRole = async () => {
  if (!roleTarget.value || roleSaving.value) return
  roleSaving.value = true
  try {
    await adminApi.updateUserRole(roleTarget.value.studentNo, {
      userRole: roleValue.value,
    })
    ElMessage.success('角色已更新，该用户重新登录后生效')
    roleVisible.value = false
    await loadUsers()
  } finally {
    roleSaving.value = false
  }
}

onMounted(loadStaff)
</script>
