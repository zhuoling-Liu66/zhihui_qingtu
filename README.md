# 智慧青途

智慧青途是一个面向校园活动管理的前后端分离项目，支持活动发布、活动报名、签到反馈、第二课堂学分审核、兴趣标签管理和组队管理等功能。项目用于课程实践和小组协作开发，方便同学之间分工、提交代码和跟踪问题。

## 项目简介

本项目以校园活动服务为核心场景，后端负责用户、活动、报名、学分和组队等业务接口，前端提供活动浏览、搜索、详情、发布、个人中心和我的活动等页面。

适合的仓库描述：

```text
校园活动发布、报名、签到、学分审核与组队管理一体化平台，包含 Spring Boot 后端和 Vue 前端，适用于课程小组协作开发。
```

## 技术栈

- 后端：Spring Boot 4、Java 17、MyBatis-Plus、MySQL、Redis
- 前端：Vue 3、Vite、Element Plus、Axios、Vue Router
- 构建工具：Maven、npm

## 主要功能

- 用户注册、登录和资料维护
- 活动发布：学生/老师发布无学分活动；运营人员发布学分/志愿活动（需审核）；管理员发布全类型活动（免审核）
- 活动审核、查询、详情、状态与签到开关管理
- 活动报名、退出、自助签到/签退、评价
- 考勤与发放：发布者/管理员修正考勤；组合学分按档位名额发放；灵活志愿时长逐人录入并提交管理员审核
- 独立管理后台：活动审核、发布活动、参与人员与发放管理、账号管理、AI 智能导入
- 活动标签和用户兴趣标签管理
- 队伍创建、加入、审核和解散
- AI 助手：学生端对话式找活动/推荐；管理端 AI 批量导入发放（Excel/CSV 自动识别列）

## 目录结构

```text
.
├── docs/                         # 项目文档（含 SQL 迁移、架构规范）
├── frontend_v2/                  # 客户端：学生 / 老师使用（Vite 5173）
├── frontend_admin/               # 管理端：管理员 / 运营人员使用（Vite 5174）
├── src/main/java/                # Spring Boot 后端源码（按业务域分包，见下）
├── src/main/resources/           # 后端配置、静态资源和数据库脚本
├── pom.xml                       # Maven 后端依赖配置
└── README.md                     # 项目说明
```

### 后端代码结构（按业务域）

```text
src/main/java/com/example/zhihuiqingtu
├── common/ config/ security/ exception/ validation   # 公共基础设施
├── auth/        # 登录注册、JWT
├── user/        # 用户资料、兴趣标签、学分总览
├── activity/    # 活动发布/审核/查询 + 缓存/库存/热门榜 + 档位
├── enrollment/  # 报名、取消、签到/签退、评价
├── award/       # 参与人员、考勤修正、发放与批量审核、统计、发放记录工厂
├── team/ publisher/
├── admin/       # 管理端交付通道：auth/activity/award/dashboard/enrollment/publisher/team/user
└── ai/          # AI 对话/推荐/工具/待确认动作/表格列识别
```

- `ActivityService` 是稳定门面：查询已拆到 `ActivityQueryService`，VO 装配在 `ActivityAssembler`；
- `EnrollmentService` 是稳定门面：参与人员与发放拆到 `ParticipantAwardService`，发放记录生成在 `AwardRecordFactory`；
- 详细分层、依赖规则、命名规范与新增模块步骤见 **[后端架构与开发规范](docs/后端架构与开发规范.md)**。

## 本地运行

### 1. 准备环境

- JDK 17
- Maven 或项目自带的 Maven Wrapper
- Node.js 和 npm
- MySQL 8.0
- Redis

### 2. 初始化数据库

先创建数据库并导入表结构：

```bash
mysql -u root -p < src/main/resources/Mysql.sql
```

数据库默认名称为 `campus_activity_platform`。如需修改连接信息，请使用环境变量配置，不要把本机密码提交到仓库。

**已有数据库升级**：按需依次执行 `docs/sql/` 下的迁移（新库只执行 `Mysql.sql`）：

```bash
mysql -u root -p campus_activity_platform < docs/sql/activity_review_and_staff.sql
mysql -u root -p campus_activity_platform < docs/sql/publisher_permission.sql
mysql -u root -p campus_activity_platform < docs/sql/account_status.sql
mysql -u root -p campus_activity_platform < docs/sql/ai_assistant.sql
mysql -u root -p campus_activity_platform < docs/sql/attendance_and_award.sql
mysql -u root -p campus_activity_platform < docs/sql/credit_modes_and_tiers.sql
```

### 3. 启动后端

Windows PowerShell 示例：

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/campus_activity_platform?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_unicode_ci&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的数据库密码"
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
.\mvnw.cmd spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

### 4. 启动客户端

```bash
cd frontend_v2
npm install
npm run dev
```

客户端默认地址：`http://localhost:5173`。学生和老师权限一致，都可以发布**无学分**活动；活动提交后需管理员审核。

### 5. 启动管理端（独立应用）

```bash
cd frontend_admin
npm install
npm run dev
```

管理端默认地址：`http://localhost:5174`，与客户端分开部署、分别登录：

- **管理员**：活动审核、发布活动（全类型，免审核）、参与人员与发放管理、账号管理、AI 智能导入；
- **运营人员**（管理端普通账号）：发布/编辑学分或志愿活动（提交管理员审核），管理自己活动的参与人员与发放。
- 运营账号由管理员在「账号管理」中创建；学生/老师不能登录管理端，运营账号也不能登录客户端。

## AI 助手（可选功能）

在原有活动/组队业务之上提供对话式助手：自然语言找活动、按条件筛选、个性化推荐活动与队伍，
并在用户**明确确认后**代为报名。核心设计是「AI 只读 + 两阶段确认」：
模型没有任何直接写库的工具，报名由用户点确认后走既有 `EnrollmentService`。

- 后端：Spring AI 2.0 + DeepSeek（OpenAI 兼容），工具调用 + SSE 流式
- 前端：`frontend_v2` 右下角悬浮球 → 聊天抽屉，活动/队伍卡片可跳转，报名有确认卡片
- **管理端 AI 智能导入**：活动「参与人员」中上传 Excel/CSV，AI 自动识别表头、学号/姓名列、时长或学分列，
  学号优先匹配参与者，预览后一键保存为待审核；模型不可用时自动降级为表头规则识别
- **不配 API Key 也能启动**：启动时打一条 WARN，聊天返回规则降级结果（`degraded=true`），
  推荐位与导入向导的规则识别完全可用

### 配置

```powershell
$env:AI_API_KEY="sk-..."          # 不配则走降级
$env:AI_MODEL="deepseek-flash"     # 默认值，需与 /models 返回的 id 一致
```

可选项见 `.env.example`：`AI_ENABLED`、`AI_BASE_URL`、`AI_DAILY_TOKENS`、`AI_RATE_PER_MINUTE`。

### 数据库

```bash
mysql -u root -p campus_activity_platform < docs/sql/ai_assistant.sql
```

只新增 5 张 `ai_` 前缀表，不改动任何既有表，可独立回滚。

### 看到「AI 助手暂时不可用」怎么办

这是**降级兜底**，不是报错：后端没拿到可用的 Key、或上游调用失败时，会改用规则引擎推荐，
并在回复里注明。按顺序检查：

1. 运行环境里是否有 `AI_API_KEY`；或在 `application-local.properties`（已 gitignore）里
   配 `spring.ai.deepseek.api-key=sk-...`；
2. 是否以 `local` profile 启动（`-Dspring.profiles.active=local`），否则上面那个文件不会被加载；
3. 模型名是否与 `GET https://api.deepseek.com/models` 返回的 id 一致（当前默认 `deepseek-flash`）；
4. 改完配置必须**重启应用**，Spring 不会热加载数据源配置。

> 判断依据：响应里的 `degraded=true`，且 `usage.totalTokens=0`。

### 说明

- 模型调用失败、超时、未配置 Key 时返回 **200 + `degraded=true`**（附规则推荐），
  不会把错误码甩给用户；只有限流/超配额才返回 429。
- 启动端口以 `application.properties` 的 `server.port` 为准（默认 8080）。
  若本机 8080 被其他服务占用，请先停掉它或改用 `--server.port=xxxx`，
  并同步修改 `frontend_v2/vite.config.js` 里的代理目标。

## 团队协作建议

- `main` 分支保持稳定，可以运行后再合并代码。
- 每位同学从 `main` 新建自己的功能分支，例如 `feature/login-page`、`feature/activity-api`。
- 修改完成后先提交到自己的分支，再在 GitHub 上发起 Pull Request。
- 不要提交 `node_modules/`、`dist/`、`target/`、`uploads/`、`.env` 等本地生成文件。
- 数据库密码、Token、个人账号等敏感信息只放在本机环境变量中。

## 常用命令

```bash
# 查看当前改动
git status

# 拉取远程最新代码
git pull

# 新建并切换分支
git checkout -b feature/your-feature

# 提交代码
git add .
git commit -m "feat: add your feature"

# 推送分支
git push -u origin feature/your-feature
```

## 许可证

本项目当前主要用于课程学习和小组协作。若后续需要公开复用，请再补充明确的开源许可证。
