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
- 校园活动发布、查询、详情和状态管理
- 活动报名、退出、签到和反馈
- 第二课堂学分记录与审核
- 活动标签和用户兴趣标签管理
- 队伍创建、加入、审核和解散

## 目录结构

```text
.
├── docs/                         # 项目文档
├── frontend/                     # Vue 3 + Vite 前端项目
├── src/main/java/                # Spring Boot 后端源码
├── src/main/resources/           # 后端配置、静态资源和数据库脚本
├── pom.xml                       # Maven 后端依赖配置
└── README.md                     # 项目说明
```

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
http://localhost:8082
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认由 Vite 输出本地访问地址。

## AI 助手（可选功能）

在原有活动/组队业务之上提供对话式助手：自然语言找活动、按条件筛选、个性化推荐活动与队伍，
并在用户**明确确认后**代为报名。核心设计是「AI 只读 + 两阶段确认」：
模型没有任何直接写库的工具，报名由用户点确认后走既有 `EnrollmentService`。

- 后端：Spring AI 2.0 + DeepSeek（OpenAI 兼容），工具调用 + SSE 流式
- 前端：`frontend_v2` 右下角悬浮球 → 聊天抽屉，活动/队伍卡片可跳转，报名有确认卡片
- **不配 API Key 也能启动**：启动时打一条 WARN，聊天返回规则降级结果（`degraded=true`），
  推荐位完全可用

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
