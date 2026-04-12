# AI 推荐模块对接清单

本文档用于对接 Python AI 推荐模块。重点回答两个问题：


当前约定：

- Java 主后端：Spring Boot 4.0.3 + MyBatis-Plus，端口 `8082`
- Python AI 服务：FastAPI，默认端口 `8000`
- 数据库：MySQL 8.0
- 第一版 AI 服务：Python 直连 MySQL，只读查询推荐所需数据
- 第一版推荐日志：Java 继续写 `recommend_log`，Python 暂不使用日志训练或排序

---

## 1. 系统边界

### 1.1 Java 主后端负责

- 用户注册、登录、资料管理
- 活动发布、查询、详情、状态管理
- 活动报名、退出、签到
- 第二课堂学分生成和审核
- 推荐日志记录
- 调用 Python AI 服务获取推荐活动 ID

### 1.2 Python AI 服务负责

- 接收 Java 转发来的推荐请求
- 读取 MySQL 中的用户画像、活动、标签等数据
- 根据推荐规则/算法返回活动 ID 列表
- 返回每个推荐活动的分数和推荐理由标签

### 1.3 Python AI 服务不负责

- 不负责登录鉴权
- 不负责活动报名
- 不负责签到
- 不负责学分生成、审核、加分
- 不写入 `activity_main`、`activity_enroll`、`credit_record`
- 第一版暂不处理 `recommend_log` 反馈学习

---

## 2. Java 需要给 AI 同学的信息

### 2.1 Python 必须实现的接口

Java 会调用：

```text
POST http://localhost:8000/recommendations/query
```

如果 Python 部署地址不是 `localhost:8000`，需要修改 Java 环境变量：

```properties
AI_RECOMMENDATION_BASE_URL=http://实际Python服务地址
```

Java 配置默认值：

```properties
ai.recommendation.base-url=${AI_RECOMMENDATION_BASE_URL:http://localhost:8000}
```

### 2.2 Java 发给 Python 的请求格式

Python 接口请求体：

```json
{
  "userId": "2405020222",
  "grade": "2024级",
  "major": "计算机科学与技术",
  "interestTagIds": [1, 3, 5],
  "creditGap": 2.0,
  "availableTimeSlots": ["周三晚上", "周末白天"],
  "limit": 10
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | string | 是 | 用户学号/工号，对应 `user_info.student_no` |
| `grade` | string | 否 | 年级，例如 `2024级` |
| `major` | string | 否 | 专业 |
| `interestTagIds` | number[] | 否 | 用户兴趣标签 ID 列表 |
| `creditGap` | number | 否 | 学分缺口，单位为学分 |
| `availableTimeSlots` | string[] | 否 | 空闲时间文本，例如 `周三晚上` |
| `limit` | number | 否 | 返回数量，默认建议 10 |

### 2.3 Python 返回给 Java 的响应格式

Python 返回体**不要**包 `code/message/data`。

正确返回：

```json
{
  "recommendedActivityIds": [9, 3, 1],
  "scores": {
    "9": 0.95,
    "3": 0.86,
    "1": 0.8
  },
  "reasonTags": {
    "9": ["智育", "第二课堂", "时间匹配"],
    "3": ["创新创业", "兴趣命中"],
    "1": ["体育", "热门活动"]
  }
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `recommendedActivityIds` | number[] | 是 | 推荐活动 ID 列表，按推荐优先级排序 |
| `scores` | object | 是 | 活动 ID 到推荐分数的映射，分数建议 0 到 1 |
| `reasonTags` | object | 是 | 活动 ID 到推荐理由标签列表的映射 |

空推荐时返回：

```json
{
  "recommendedActivityIds": [],
  "scores": {},
  "reasonTags": {}
}
```

### 2.4 Java 对外给前端的推荐接口

前端不会直接调用 Python，而是调用 Java：

```text
POST http://localhost:8082/api/recommendations/query
```

Java 会把请求转发给 Python：

```text
POST http://localhost:8000/recommendations/query
```

前端记录推荐点击/报名时调用：

```text
POST http://localhost:8082/api/recommendations/logs
```

请求体：

```json
{
  "userId": "2405020222",
  "activityId": 9,
  "isClick": 1,
  "isEnroll": 0
}
```

说明：

- `isClick=1` 表示用户点击了推荐活动
- `isEnroll=1` 表示用户从推荐链路报名
- 第一版 Python 暂不使用该日志

---

## 3. AI 同学需要从 MySQL 读取的信息

第一版约定：Python 使用只读 MySQL 账号读取数据。

推荐读取这些表：

| 表名 | 用途 |
| --- | --- |
| `user_info` | 用户专业、年级、角色等基础画像 |
| `user_interest_rel` | 用户兴趣标签 |
| `activity_main` | 活动主信息和可推荐候选集 |
| `activity_tag` | 标签名称 |
| `activity_tag_rel` | 活动和标签关系 |
| `activity_enroll` | 用户历史报名、签到、完成情况 |
| `credit_record` | 用户已获得或待审核学分记录 |

第一版不需要读取：

| 表名 | 原因 |
| --- | --- |
| `recommend_log` | 第一版暂不使用点击/报名反馈做排序 |
| `team_main` / `team_member` | 除非第一版要推荐组队，否则可以先不接入 |

---

## 4. 关键数据库字段

### 4.1 用户表 `user_info`

| 字段 | 说明 |
| --- | --- |
| `student_no` | 用户主键，学号/工号 |
| `user_name` | 姓名 |
| `user_role` | 1-学生，2-管理员，3-老师 |
| `publisher_status` | 是否有发布权限 |
| `major` | 专业 |
| `grade` | 年级 |
| `delete_flag` | 软删除标记，0-正常，1-删除 |

注意：

- 用户 ID 不是自增数字 ID
- 推荐请求里的 `userId` 对应这里的 `student_no`

### 4.2 用户兴趣标签表 `user_interest_rel`

| 字段 | 说明 |
| --- | --- |
| `user_id` | 用户学号/工号 |
| `tag_id` | 兴趣标签 ID |
| `delete_flag` | 软删除标记 |

### 4.3 活动主表 `activity_main`

| 字段 | 说明 |
| --- | --- |
| `activity_id` | 活动 ID，自增 |
| `publisher_user_id` | 发布者学号/工号 |
| `activity_title` | 活动标题 |
| `activity_desc` | 活动描述 |
| `activity_type` | 活动类型 |
| `activity_credit` | 学分，仅第二课堂活动使用 |
| `credit_type` | 学分类型，仅第二课堂活动使用 |
| `volunteer_hours` | 志愿服务时长，仅志愿服务活动使用 |
| `cover_url` | 活动封面 |
| `start_time` | 开始时间 |
| `end_time` | 结束时间 |
| `activity_location` | 活动地点 |
| `max_participant` | 最大人数，0 表示不限 |
| `current_participant` | 当前报名人数 |
| `activity_status` | 1-已发布，4-已取消 |
| `sign_in_enabled` | 0-未开放签到，1-已开放签到 |
| `delete_flag` | 软删除标记 |

### 4.4 活动标签关系表

`activity_tag`：

| 字段 | 说明 |
| --- | --- |
| `tag_id` | 标签 ID |
| `tag_name` | 标签名称 |

`activity_tag_rel`：

| 字段 | 说明 |
| --- | --- |
| `activity_id` | 活动 ID |
| `tag_id` | 标签 ID |

### 4.5 报名表 `activity_enroll`

| 字段 | 说明 |
| --- | --- |
| `enroll_id` | 报名 ID |
| `user_id` | 用户学号/工号 |
| `activity_id` | 活动 ID |
| `enroll_status` | 1-已报名，3-已签到，4-已完成 |
| `sign_in_time` | 签到时间 |
| `feedback_score` | 活动评分 |
| `feedback_content` | 活动评价 |
| `delete_flag` | 软删除标记 |

### 4.6 学分表 `credit_record`

| 字段 | 说明 |
| --- | --- |
| `user_id` | 用户学号/工号 |
| `activity_id` | 活动 ID |
| `credit` | 获得学分 |
| `credit_type` | 学分类型 |
| `verify_status` | 0-待审核，1-已通过，2-已驳回 |

---

## 5. 枚举规则

### 5.1 活动类型 `activity_type`

| 值 | 说明 |
| --- | --- |
| `1` | 第二课堂学分活动 |
| `2` | 志愿服务活动 |
| `3` | 运动健身活动 |
| `4` | 兴趣团建活动 |

### 5.2 学分类型 `credit_type`

| 值 | 说明 |
| --- | --- |
| `1` | 创新创业 |
| `2` | 智育 |
| `3` | 德育 |
| `4` | 体育 |
| `5` | 美育 |
| `6` | 劳育 |

### 5.3 活动状态 `activity_status`

| 值 | 说明 |
| --- | --- |
| `1` | 已发布 |
| `4` | 已取消 |

补充：

- “已结束”不是数据库状态
- 推荐时用 `end_time <= 当前时间` 判断活动是否已经结束

### 5.4 报名状态 `enroll_status`

| 值 | 说明 |
| --- | --- |
| `1` | 已报名 |
| `3` | 已签到 |
| `4` | 已完成 |

---

## 6. 推荐候选活动过滤规则

Python 侧推荐候选集必须先过滤：

```sql
SELECT *
FROM activity_main
WHERE delete_flag = 0
  AND activity_status = 1
  AND end_time > NOW()
  AND (
        max_participant = 0
        OR current_participant < max_participant
      );
```

必须排除：

- 软删除活动
- 已取消活动
- 已结束活动
- 人数已满活动

建议排除：

- 用户已经报名过的活动
- 活动时间与用户空闲时间明显冲突的活动

---

## 7. 第一版推荐算法建议

第一版先做规则推荐，不强制上复杂模型。

建议排序因素：

| 因素 | 说明 |
| --- | --- |
| 兴趣标签匹配 | 用户 `interestTagIds` 和活动标签重合越多，分数越高 |
| 学分缺口匹配 | 有 `creditGap` 时优先推荐第二课堂学分活动 |
| 学分类型匹配 | 可优先推荐智育、德育、体育等缺口相关类型 |
| 活动类型偏好 | 根据用户历史报名活动类型加权 |
| 时间匹配 | 活动时间落在 `availableTimeSlots` 中加分 |
| 热门度 | 当前报名人数较高但未满的活动可适当加权 |

推荐分数建议范围：

```text
0.00 <= score <= 1.00
```

推荐理由标签建议从以下类型里生成：

- `兴趣命中`
- `第二课堂`
- `志愿服务`
- `时间匹配`
- `学分缺口`
- `热门活动`
- `同专业偏好`
- `历史偏好`

---

## 8. AI 同学需要给你的信息

AI 同学交付时至少需要给你：

### 8.1 Python 服务信息

- 启动命令
- 依赖文件，例如 `requirements.txt`
- 实际服务地址，例如 `http://localhost:8000`
- 是否使用 `.env` 配置 MySQL

### 8.2 接口确认

必须确认已实现：

```text
POST /recommendations/query
```

并确认：

- 请求字段名和 Java 一致
- 返回字段名和 Java 一致
- 返回体不包 `code/message/data`
- 空结果时返回空数组和空对象，不抛异常

### 8.3 数据库读取说明

需要说明：

- 读取了哪些表
- 用了哪些字段
- 是否需要新增字段
- 是否需要 Java 新增接口

### 8.4 推荐算法说明

至少说明：

- 候选活动如何过滤
- 如何计算分数
- 每个推荐理由如何生成
- 没有画像数据时如何兜底
- 没有可推荐活动时如何返回

### 8.5 联调样例

需要提供：

- 一组请求 JSON
- 一组预期响应 JSON
- 一组可验证的活动 ID

---

## 9. Python FastAPI 最小示例

```python
from typing import Dict, List, Optional

from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="Campus Activity AI Recommendation Service")


class RecommendationQueryRequest(BaseModel):
    userId: str
    grade: Optional[str] = None
    major: Optional[str] = None
    interestTagIds: List[int] = []
    creditGap: Optional[float] = None
    availableTimeSlots: List[str] = []
    limit: int = 10


class RecommendationQueryResponse(BaseModel):
    recommendedActivityIds: List[int]
    scores: Dict[int, float]
    reasonTags: Dict[int, List[str]]


@app.post("/recommendations/query", response_model=RecommendationQueryResponse)
def query_recommendations(payload: RecommendationQueryRequest):
    # TODO: 这里后续替换为真实推荐逻辑。
    return RecommendationQueryResponse(
        recommendedActivityIds=[],
        scores={},
        reasonTags={},
    )
```

启动示例：

```bash
uvicorn app:app --host 0.0.0.0 --port 8000
```

---

## 10. 联调检查清单

### 10.1 直调 Python

```http
POST http://localhost:8000/recommendations/query
Content-Type: application/json
```

```json
{
  "userId": "2405020222",
  "grade": "2024级",
  "major": "计算机科学与技术",
  "interestTagIds": [1, 3, 5],
  "creditGap": 2.0,
  "availableTimeSlots": ["周三晚上", "周末白天"],
  "limit": 5
}
```

### 10.2 调 Java 转发

```http
POST http://localhost:8082/api/recommendations/query
Content-Type: application/json
```

请求体同上。

### 10.3 验证推荐活动 ID 是否有效

对 Python 返回的每个活动 ID，调用 Java：

```http
GET http://localhost:8082/api/activities/{activityId}
```

必须能查到活动详情。

### 10.4 验证推荐日志

```http
POST http://localhost:8082/api/recommendations/logs
Content-Type: application/json
```

```json
{
  "userId": "2405020222",
  "activityId": 9,
  "isClick": 1,
  "isEnroll": 0
}
```

第一版只验证 Java 能写入日志，不要求 Python 使用日志。

---

## 11. 只读数据库账号建议

不要把 root 账号给 AI 服务。

建议创建只读账号，例如：

- 用户名：`ai_reader`
- 权限：只允许 `SELECT`
- 授权范围：推荐相关表

仓库中提供了 SQL 模板：

```text
docs/sql/create_ai_readonly_user.sql
```

执行前请先修改里面的密码。

---

## 12. 当前双方最终约定

- Java 给 Python：用户画像参数
- Python 自己查 MySQL：活动、标签、报名历史、学分记录
- Python 给 Java：活动 ID、分数、推荐理由
- Java 给前端：统一包装后的推荐结果
- Java 写日志：`recommend_log`
- Python 第一版不使用日志
- Python 不写业务表

