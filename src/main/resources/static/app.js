const ACTIVITY_TYPES = {
  SECOND_CLASS: 1,
  VOLUNTEER: 2,
  SPORTS: 3,
  INTEREST: 4,
};

const ACTIVITY_TYPE_LABELS = {
  1: "第二课堂学分活动",
  2: "志愿服务活动",
  3: "运动健身活动",
  4: "兴趣团建活动",
};

const ACTIVITY_STATUS_LABELS = {
  0: "草稿",
  1: "已发布",
  2: "进行中",
  3: "已结束",
  4: "已取消",
};

const CREDIT_TYPE_LABELS = {
  1: "创新创业",
  2: "智育",
  3: "德育",
  4: "体育",
  5: "美育",
  6: "劳育",
};

const ENROLLMENT_STATUS_LABELS = {
  0: "待审核",
  1: "已通过",
  2: "已驳回",
  3: "已签到",
  4: "已完成",
};

const REVIEW_STATUS_LABELS = {
  0: "待审核",
  1: "已通过",
  2: "已驳回",
};

const state = {
  currentUser: loadCurrentUser(),
  activityTags: [],
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));

function loadCurrentUser() {
  const raw = localStorage.getItem("zhihuiqingtu-current-user");
  return raw ? JSON.parse(raw) : null;
}

function saveCurrentUser(user) {
  state.currentUser = user;
  if (user) {
    localStorage.setItem("zhihuiqingtu-current-user", JSON.stringify(user));
  } else {
    localStorage.removeItem("zhihuiqingtu-current-user");
  }
  renderCurrentUser();
}

function renderCurrentUser() {
  const name = $("#current-user-name");
  const meta = $("#current-user-meta");
  if (!state.currentUser) {
    name.textContent = "未登录";
    meta.textContent = "请先完成登录或注册";
    return;
  }
  name.textContent = `${state.currentUser.userName}（ID ${state.currentUser.userId}）`;
  meta.textContent = `角色 ${state.currentUser.userRole} · 专业 ${state.currentUser.major || "未填写"} · 年级 ${state.currentUser.grade || "未填写"}`;
}

function showMessage(message, type = "success") {
  const bar = $("#message-bar");
  bar.textContent = message;
  bar.className = `message-bar ${type}`;
  setTimeout(() => {
    bar.className = "message-bar hidden";
  }, 3200);
}

function parseIds(value) {
  return (value || "")
    .split(",")
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isFinite(item) && item > 0);
}

function parseTimeSlots(value) {
  return (value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function parseDecimal(value) {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function toDateTime(value) {
  return value ? `${value}:00` : null;
}

function formatText(value, fallback = "无") {
  return value === null || value === undefined || value === "" ? fallback : value;
}

function formatCreditType(code) {
  return CREDIT_TYPE_LABELS[code] || "未设置";
}

function formatActivityType(code) {
  return ACTIVITY_TYPE_LABELS[code] || `类型 ${code}`;
}

function formatActivityStatus(code) {
  return ACTIVITY_STATUS_LABELS[code] || `状态 ${code}`;
}

function formatEnrollmentStatus(code) {
  return ENROLLMENT_STATUS_LABELS[code] || `状态 ${code}`;
}

function formatReviewStatus(code) {
  return REVIEW_STATUS_LABELS[code] || `状态 ${code}`;
}

function buildActivityMetricLine(item) {
  if (item.activityType === ACTIVITY_TYPES.SECOND_CLASS) {
    return `学分 ${formatText(item.activityCredit, 0)} · 学分类型 ${formatCreditType(item.creditType)}`;
  }
  if (item.activityType === ACTIVITY_TYPES.VOLUNTEER) {
    return `志愿服务时长 ${formatText(item.volunteerHours, 0)} 小时`;
  }
  return "无学分与服务时长要求";
}

async function api(path, options = {}) {
  const config = {
    headers: { "Content-Type": "application/json" },
    ...options,
  };
  const response = await fetch(path, config);
  const payload = await response.json();
  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || "请求失败");
  }
  return payload.data;
}

function ensureCurrentUser() {
  if (!state.currentUser) {
    throw new Error("请先登录后再执行该操作");
  }
  return state.currentUser;
}

function renderKeyValueBoard(target, data) {
  const entries = Object.entries(data || {});
  if (!entries.length) {
    target.classList.add("empty-state");
    target.textContent = "暂无数据";
    return;
  }
  target.classList.remove("empty-state");
  target.innerHTML = entries
    .map(([key, value]) => {
      let displayValue = value;
      if (Array.isArray(value)) {
        displayValue = value.length ? value.join("、") : "无";
      } else if (value && typeof value === "object") {
        displayValue = JSON.stringify(value, null, 2);
      } else {
        displayValue = formatText(value);
      }
      return `<div class="list-item"><h4>${key}</h4><p>${displayValue}</p></div>`;
    })
    .join("");
}

function renderList(target, items, renderItem) {
  if (!items || !items.length) {
    target.classList.add("empty-state");
    target.textContent = "暂无数据";
    return;
  }
  target.classList.remove("empty-state");
  target.innerHTML = items.map(renderItem).join("");
}

function renderTagBoards() {
  const content = state.activityTags.length
    ? state.activityTags
        .map((tag) => `<span class="tag-chip">#${tag.tagId} ${tag.tagName}</span>`)
        .join("")
    : "";

  ["#activity-tag-board", "#interest-tag-board", "#recommend-tag-board"].forEach((selector) => {
    const board = $(selector);
    if (!board) {
      return;
    }
    if (!content) {
      board.classList.add("empty-state");
      board.textContent = "暂无可用标签";
      return;
    }
    board.classList.remove("empty-state");
    board.innerHTML = content;
  });
}

async function loadActivityTags() {
  state.activityTags = await api("/api/activities/tags");
  renderTagBoards();
}

function bindNav() {
  $$(".nav-item").forEach((button) => {
    button.addEventListener("click", () => {
      $$(".nav-item").forEach((item) => item.classList.remove("active"));
      $$(".panel").forEach((panel) => panel.classList.remove("active"));
      button.classList.add("active");
      $(`#${button.dataset.target}`).classList.add("active");
    });
  });
}

function bindAuth() {
  $("#register-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    const payload = Object.fromEntries(form.entries());
    payload.userRole = Number(payload.userRole);
    try {
      await api("/api/auth/register", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      showMessage("注册成功，请继续登录");
      event.target.reset();
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const payload = Object.fromEntries(new FormData(event.target).entries());
    try {
      const data = await api("/api/auth/login", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      saveCurrentUser(data);
      showMessage("登录成功");
      await refreshProfile();
      await loadActivities();
      await loadEnrollments();
      await loadTeams();
      await loadCredits();
      await loadCreditStatistics();
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#logout-btn").addEventListener("click", () => {
    saveCurrentUser(null);
    showMessage("已退出当前账号");
  });
}

async function refreshProfile() {
  const user = ensureCurrentUser();
  const data = await api(`/api/users/${encodeURIComponent(user.userId)}`);
  renderKeyValueBoard($("#profile-view"), {
    "学号/工号": data.studentNo,
    姓名: data.userName,
    手机号: data.userPhone || "未填写",
    邮箱: data.userEmail || "未填写",
    角色: data.userRole,
    发布权限: data.publisherStatus,
    专业: data.major || "未填写",
    年级: data.grade || "未填写",
    兴趣标签: (data.interestTags || []).map((item) => item.tagName),
  });
}

function bindProfile() {
  $("#refresh-profile-btn").addEventListener("click", async () => {
    try {
      await refreshProfile();
      showMessage("资料已刷新");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#profile-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const user = ensureCurrentUser();
      const payload = Object.fromEntries(new FormData(event.target).entries());
      await api(`/api/users/${encodeURIComponent(user.userId)}`, {
        method: "PUT",
        body: JSON.stringify(payload),
      });
      await refreshProfile();
      showMessage("个人资料已更新");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#interest-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const user = ensureCurrentUser();
      const value = new FormData(event.target).get("tagIds");
      await api(`/api/users/${encodeURIComponent(user.userId)}/interest-tags`, {
        method: "PUT",
        body: JSON.stringify({ tagIds: parseIds(value) }),
      });
      await refreshProfile();
      showMessage("兴趣标签已更新");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#load-history-btn").addEventListener("click", async () => {
    try {
      const user = ensureCurrentUser();
      const data = await api(`/api/users/${encodeURIComponent(user.userId)}/histories`);
      renderList($("#profile-extra"), data, (item) => `
        <div class="list-item">
          <h4>${formatText(item.activityTitle, "未知活动")} · 报名ID ${item.enrollId}</h4>
          <p>活动ID ${item.activityId} · 类型 ${formatActivityType(item.activityType)} · 状态 ${formatEnrollmentStatus(item.enrollStatus)}</p>
          <p>${formatText(item.startTime, "-")} 至 ${formatText(item.endTime, "-")}</p>
        </div>
      `);
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#load-overview-btn").addEventListener("click", async () => {
    try {
      const user = ensureCurrentUser();
      const data = await api(`/api/users/${encodeURIComponent(user.userId)}/credit-overview`);
      renderKeyValueBoard($("#profile-extra"), {
        总学分: data.totalCredit,
        创新创业: data.innovationEntrepreneurshipCredit,
        智育: data.intellectualEducationCredit,
        德育: data.moralEducationCredit,
        体育: data.physicalEducationCredit,
        美育: data.aestheticEducationCredit,
        劳育: data.laborEducationCredit,
      });
    } catch (error) {
      showMessage(error.message, "error");
    }
  });
}

function updateActivityCreateFields() {
  const activityType = Number($("#activity-type-select").value);
  const creditWrapper = $("#activity-credit-wrapper");
  const creditTypeWrapper = $("#activity-credit-type-wrapper");
  const volunteerHoursWrapper = $("#activity-volunteer-hours-wrapper");
  const creditInput = $("#activity-create-form [name='activityCredit']");
  const creditTypeInput = $("#activity-create-form [name='creditType']");
  const volunteerHoursInput = $("#activity-create-form [name='volunteerHours']");
  const tip = $("#activity-mode-tip");

  creditWrapper.classList.toggle("hidden", activityType !== ACTIVITY_TYPES.SECOND_CLASS);
  creditTypeWrapper.classList.toggle("hidden", activityType !== ACTIVITY_TYPES.SECOND_CLASS);
  volunteerHoursWrapper.classList.toggle("hidden", activityType !== ACTIVITY_TYPES.VOLUNTEER);

  if (activityType === ACTIVITY_TYPES.SECOND_CLASS) {
    creditInput.value = creditInput.value || "";
    tip.textContent = "第二课堂学分活动需要填写学分和学分类型。";
  } else {
    creditInput.value = "";
    creditTypeInput.value = "";
  }

  if (activityType === ACTIVITY_TYPES.VOLUNTEER) {
    tip.textContent = "志愿服务活动需要填写志愿服务时长，不需要填写学分。";
  } else if (activityType !== ACTIVITY_TYPES.SECOND_CLASS) {
    volunteerHoursInput.value = "";
    tip.textContent = "当前活动类型不需要填写学分，也不需要填写学分类型。";
  }

  if (activityType !== ACTIVITY_TYPES.VOLUNTEER) {
    volunteerHoursInput.value = "";
  }
}

async function loadActivities() {
  const form = new FormData($("#activity-query-form"));
  const params = new URLSearchParams();
  form.forEach((value, key) => value && params.append(key, value));
  const data = await api(`/api/activities?${params.toString()}`);
  renderList($("#activity-list"), data.records || [], (item) => `
    <div class="list-item">
      <h4>${item.activityTitle} <small>#${item.activityId}</small></h4>
      <p>发布者 ${item.publisherUserId} · 类型 ${formatActivityType(item.activityType)} · 状态 ${formatActivityStatus(item.activityStatus)}</p>
      <p>${buildActivityMetricLine(item)} · 人数 ${item.currentParticipant}/${item.maxParticipant} · 地点 ${formatText(item.activityLocation, "未设置")}</p>
      <p>标签 ${(item.tags || []).map((tag) => `${tag.tagName}(${tag.tagId})`).join("、") || "无"}</p>
    </div>
  `);
}

function buildCreateActivityPayload(form) {
  const activityType = Number(form.get("activityType"));
  const payload = {
    activityTitle: form.get("activityTitle"),
    activityDesc: form.get("activityDesc"),
    activityType,
    startTime: toDateTime(form.get("startTime")),
    endTime: toDateTime(form.get("endTime")),
    activityLocation: form.get("activityLocation"),
    maxParticipant: Number(form.get("maxParticipant") || 0),
    tagIds: parseIds(form.get("tagIds")),
    activityCredit: null,
    creditType: null,
    volunteerHours: null,
  };

  if (activityType === ACTIVITY_TYPES.SECOND_CLASS) {
    payload.activityCredit = parseDecimal(form.get("activityCredit"));
    payload.creditType = form.get("creditType") ? Number(form.get("creditType")) : null;
  }

  if (activityType === ACTIVITY_TYPES.VOLUNTEER) {
    payload.volunteerHours = parseDecimal(form.get("volunteerHours"));
  }

  return payload;
}

function bindActivities() {
  $("#activity-type-select").addEventListener("change", updateActivityCreateFields);
  updateActivityCreateFields();

  $("#activity-create-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const user = ensureCurrentUser();
      const form = new FormData(event.target);
      const created = await api("/api/activities", {
        method: "POST",
        body: JSON.stringify({
          publisherUserId: user.userId,
          ...buildCreateActivityPayload(form),
        }),
      });
      event.target.reset();
      updateActivityCreateFields();
      await loadActivities();
      showMessage(`活动创建成功，活动ID 为 ${created.activityId}`);
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#activity-status-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const user = ensureCurrentUser();
      const form = new FormData(event.target);
      await api(`/api/activities/${form.get("activityId")}/status`, {
        method: "PATCH",
        body: JSON.stringify({
          operatorUserId: user.userId,
          activityStatus: Number(form.get("activityStatus")),
        }),
      });
      await loadActivities();
      showMessage("活动状态已更新");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#activity-delete-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const user = ensureCurrentUser();
      const activityId = new FormData(event.target).get("activityId");
      await api(`/api/activities/${activityId}?operatorUserId=${encodeURIComponent(user.userId)}`, {
        method: "DELETE",
      });
      event.target.reset();
      await loadActivities();
      showMessage(`活动 ${activityId} 已删除`);
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#activity-query-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await loadActivities();
    } catch (error) {
      showMessage(error.message, "error");
    }
  });
}

async function loadEnrollments() {
  if (!state.currentUser) return;
  const user = ensureCurrentUser();
  const form = new FormData($("#enrollment-query-form"));
  const params = new URLSearchParams();
  params.set("userId", user.userId);
  form.forEach((value, key) => value && params.append(key, value));
  const data = await api(`/api/enrollments?${params.toString()}`);
  renderList($("#enrollment-list"), data.records || [], (item) => `
    <div class="list-item">
      <h4>${formatText(item.activityTitle, "未知活动")} · 报名ID ${item.enrollId}</h4>
      <p>用户 ${item.userId} · 活动ID ${item.activityId} · 状态 ${formatEnrollmentStatus(item.enrollStatus)}</p>
      <p>评分 ${formatText(item.feedbackScore, "未评价")} · ${formatText(item.feedbackContent, "暂无评价")}</p>
    </div>
  `);
}

function bindEnrollments() {
  $("#enroll-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const user = ensureCurrentUser();
      const activityId = Number(new FormData(event.target).get("activityId"));
      await api("/api/enrollments", {
        method: "POST",
        body: JSON.stringify({ userId: user.userId, activityId }),
      });
      await loadEnrollments();
      showMessage("报名成功");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#enroll-review-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const user = ensureCurrentUser();
      const form = new FormData(event.target);
      await api(`/api/enrollments/${form.get("enrollId")}/review`, {
        method: "PATCH",
        body: JSON.stringify({
          reviewUserId: user.userId,
          enrollStatus: Number(form.get("enrollStatus")),
        }),
      });
      await loadEnrollments();
      showMessage("报名审核完成");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#enroll-feedback-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const form = new FormData(event.target);
      await api(`/api/enrollments/${form.get("enrollId")}/feedback`, {
        method: "PATCH",
        body: JSON.stringify({
          feedbackScore: Number(form.get("feedbackScore")),
          feedbackContent: form.get("feedbackContent"),
        }),
      });
      await loadEnrollments();
      await loadCredits();
      await loadCreditStatistics();
      showMessage("评价提交成功");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#enrollment-query-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await loadEnrollments();
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#sign-in-btn").addEventListener("click", async () => {
    const enrollId = prompt("请输入需要签到的报名ID");
    if (!enrollId) return;
    try {
      await api(`/api/enrollments/${enrollId}/sign-in`, { method: "PATCH" });
      await loadEnrollments();
      showMessage("签到成功");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#cancel-enroll-btn").addEventListener("click", async () => {
    const enrollId = prompt("请输入需要取消的报名ID");
    if (!enrollId) return;
    try {
      await api(`/api/enrollments/${enrollId}`, { method: "DELETE" });
      await loadEnrollments();
      showMessage("报名已取消");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });
}

async function loadTeams() {
  const form = new FormData($("#team-query-form"));
  const params = new URLSearchParams();
  form.forEach((value, key) => value && params.append(key, value));
  const data = await api(`/api/teams?${params.toString()}`);
  renderList($("#team-list"), data.records || [], (item) => `
    <div class="list-item">
      <h4>${item.teamName} · 队伍ID ${item.teamId}</h4>
      <p>活动ID ${formatText(item.activityId)} · 队长 ${item.leaderUserId} · 状态 ${formatText(item.teamStatus)}</p>
      <p>${formatText(item.teamDesc, "暂无简介")}</p>
      <p>人数 ${item.currentSize}/${item.maxSize}</p>
    </div>
  `);
}

function bindTeams() {
  $("#team-create-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const user = ensureCurrentUser();
      const form = new FormData(event.target);
      await api("/api/teams", {
        method: "POST",
        body: JSON.stringify({
          activityId: form.get("activityId") ? Number(form.get("activityId")) : null,
          leaderUserId: user.userId,
          teamName: form.get("teamName"),
          teamDesc: form.get("teamDesc"),
          maxSize: Number(form.get("maxSize")),
        }),
      });
      await loadTeams();
      showMessage("队伍创建成功");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#team-review-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const user = ensureCurrentUser();
      const form = new FormData(event.target);
      await api(`/api/teams/${form.get("teamId")}/members/${form.get("memberId")}/review`, {
        method: "PATCH",
        body: JSON.stringify({
          leaderUserId: user.userId,
          memberStatus: Number(form.get("memberStatus")),
        }),
      });
      await loadTeams();
      showMessage("队员审核完成");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#team-query-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await loadTeams();
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#join-team-btn").addEventListener("click", async () => {
    const teamId = prompt("请输入要加入的队伍ID");
    if (!teamId) return;
    try {
      const user = ensureCurrentUser();
      await api(`/api/teams/${teamId}/join`, {
        method: "POST",
        body: JSON.stringify({ userId: user.userId }),
      });
      await loadTeams();
      showMessage("入队申请已提交");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#exit-team-btn").addEventListener("click", async () => {
    const teamId = prompt("请输入队伍ID");
    const memberId = prompt("请输入成员记录ID");
    if (!teamId || !memberId) return;
    try {
      const user = ensureCurrentUser();
      await api(`/api/teams/${teamId}/members/${memberId}/exit`, {
        method: "PATCH",
        body: JSON.stringify({ operatorUserId: user.userId }),
      });
      await loadTeams();
      showMessage("已退出队伍");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#dismiss-team-btn").addEventListener("click", async () => {
    const teamId = prompt("请输入要解散的队伍ID");
    if (!teamId) return;
    try {
      const user = ensureCurrentUser();
      await api(`/api/teams/${teamId}/dismiss`, {
        method: "PATCH",
        body: JSON.stringify({ operatorUserId: user.userId }),
      });
      await loadTeams();
      showMessage("队伍已解散");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });
}

async function loadCredits() {
  if (!state.currentUser) return;
  const user = ensureCurrentUser();
  const form = new FormData($("#credit-query-form"));
  const params = new URLSearchParams();
  params.set("userId", user.userId);
  form.forEach((value, key) => value && params.append(key, value));
  const data = await api(`/api/credits?${params.toString()}`);
  renderList($("#credit-list"), data.records || [], (item) => `
    <div class="list-item">
      <h4>学分记录 ${item.recordId}</h4>
      <p>用户 ${item.userId} · 活动ID ${item.activityId} · 学分 ${formatText(item.credit, 0)}</p>
      <p>类型 ${formatCreditType(item.creditType)} · 审核状态 ${formatReviewStatus(item.verifyStatus)} · 驳回原因 ${formatText(item.rejectReason)}</p>
    </div>
  `);
}

async function loadCreditStatistics() {
  if (!state.currentUser) return;
  const user = ensureCurrentUser();
  const data = await api(`/api/credits/statistics?userId=${encodeURIComponent(user.userId)}`);
  renderKeyValueBoard($("#credit-statistics"), {
    总学分: data.totalCredit,
    创新创业: data.innovationEntrepreneurshipCredit,
    智育: data.intellectualEducationCredit,
    德育: data.moralEducationCredit,
    体育: data.physicalEducationCredit,
    美育: data.aestheticEducationCredit,
    劳育: data.laborEducationCredit,
  });
}

function bindCredits() {
  $("#credit-query-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await loadCredits();
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#credit-review-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const user = ensureCurrentUser();
      const form = new FormData(event.target);
      await api(`/api/credits/${form.get("recordId")}/review`, {
        method: "PATCH",
        body: JSON.stringify({
          verifyUserId: user.userId,
          verifyStatus: Number(form.get("verifyStatus")),
          rejectReason: form.get("rejectReason"),
        }),
      });
      await loadCredits();
      await loadCreditStatistics();
      showMessage("学分审核完成");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#refresh-credit-stats-btn").addEventListener("click", async () => {
    try {
      await loadCreditStatistics();
      showMessage("学分统计已刷新");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });
}

function bindRecommendation() {
  $("#recommend-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const user = ensureCurrentUser();
      const form = new FormData(event.target);
      const data = await api("/api/recommendations/query", {
        method: "POST",
        body: JSON.stringify({
          userId: user.userId,
          grade: form.get("grade"),
          major: form.get("major"),
          interestTagIds: parseIds(form.get("interestTagIds")),
          creditGap: Number(form.get("creditGap") || 0),
          availableTimeSlots: parseTimeSlots(form.get("availableTimeSlots")),
          limit: Number(form.get("limit") || 10),
        }),
      });
      renderKeyValueBoard($("#recommend-result"), {
        "推荐活动 ID": data.recommendedActivityIds || [],
        分数字典: data.scores || {},
        推荐理由: data.reasonTags || {},
      });
      showMessage("推荐请求已完成");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });

  $("#recommend-log-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const user = ensureCurrentUser();
      const form = new FormData(event.target);
      await api("/api/recommendations/logs", {
        method: "POST",
        body: JSON.stringify({
          userId: user.userId,
          activityId: Number(form.get("activityId")),
          isClick: Number(form.get("isClick")),
          isEnroll: Number(form.get("isEnroll")),
        }),
      });
      showMessage("推荐日志记录成功");
    } catch (error) {
      showMessage(error.message, "error");
    }
  });
}

async function bootstrap() {
  bindNav();
  bindAuth();
  bindProfile();
  bindActivities();
  bindEnrollments();
  bindTeams();
  bindCredits();
  bindRecommendation();
  renderCurrentUser();

  try {
    await loadActivityTags();
    await loadActivities();
    if (state.currentUser) {
      await refreshProfile();
      await loadEnrollments();
      await loadTeams();
      await loadCredits();
      await loadCreditStatistics();
    }
  } catch (error) {
    showMessage(error.message, "error");
  }
}

bootstrap();
