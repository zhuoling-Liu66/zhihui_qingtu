export const topQuickLinks = [
  { label: '第二课堂学分', slug: 'second-class' },
  { label: '志愿服务', slug: 'volunteer' },
  { label: '德育成长', slug: 'moral' },
  { label: '体育打卡', slug: 'physical' },
  { label: '组队邀请', slug: 'team' },
  { label: '创新创业', slug: 'innovation' },
  { label: '美育活动', slug: 'aesthetic' },
  { label: '劳育实践', slug: 'labor' },
]

export const homeTopicEntries = [
  { label: '猜你喜欢', icon: '❤', type: 'home' },
  { label: '第二课堂学分', icon: '学', type: 'section', slug: 'second-class' },
  { label: '志愿活动', icon: '愿', type: 'section', slug: 'volunteer' },
  { label: '组队邀请', icon: '队', type: 'section', slug: 'team' },
  { label: '德育', icon: '德', type: 'section', slug: 'moral' },
  { label: '体育', icon: '体', type: 'section', slug: 'physical' },
  { label: '创新创业', icon: '创', type: 'section', slug: 'innovation' },
  { label: '美育', icon: '美', type: 'section', slug: 'aesthetic' },
  { label: '劳育', icon: '劳', type: 'section', slug: 'labor' },
]

export const railPanels = [
  {
    key: 'credit',
    label: '第二课堂 / 学分认定',
    slug: 'second-class',
    sections: [
      {
        title: '学分分类',
        links: [
          { label: '第二课堂总览', type: 'section', slug: 'second-class' },
          { label: '创新创业', type: 'section', slug: 'innovation' },
          { label: '智育', type: 'section', slug: 'intellectual' },
          { label: '德育', type: 'section', slug: 'moral' },
          { label: '体育', type: 'section', slug: 'physical' },
          { label: '美育', type: 'section', slug: 'aesthetic' },
          { label: '劳育', type: 'section', slug: 'labor' },
        ],
      },
      {
        title: '常见搜索',
        links: [
          { label: '学分活动', type: 'search', keyword: '学分活动' },
          { label: '思政讲座', type: 'search', keyword: '思政 讲座' },
          { label: '实践竞赛', type: 'search', keyword: '竞赛 实践' },
        ],
      },
    ],
  },
  {
    key: 'volunteer',
    label: '志愿服务 / 时长统计',
    slug: 'volunteer',
    sections: [
      {
        title: '服务类型',
        links: [
          { label: '志愿服务总览', type: 'section', slug: 'volunteer' },
          { label: '公益服务', type: 'search', keyword: '公益 志愿' },
          { label: '社区协助', type: 'search', keyword: '社区 服务' },
          { label: '图书馆服务', type: 'search', keyword: '图书馆 志愿' },
        ],
      },
      {
        title: '时长管理',
        links: [
          { label: '按时长筛选', type: 'search', keyword: '志愿 时长' },
          { label: '周末服务', type: 'search', keyword: '周末 志愿' },
          { label: '长期招募', type: 'search', keyword: '长期 招募' },
        ],
      },
    ],
  },
  {
    key: 'sports',
    label: '运动健身 / 校园打卡',
    slug: 'sports',
    sections: [
      {
        title: '热门运动',
        links: [
          { label: '运动健身总览', type: 'section', slug: 'sports' },
          { label: '跑步打卡', type: 'search', keyword: '跑步 打卡' },
          { label: '球类活动', type: 'search', keyword: '篮球 羽毛球 足球' },
          { label: '体测训练', type: 'search', keyword: '体测 训练' },
        ],
      },
      {
        title: '体育学分',
        links: [
          { label: '体育学分', type: 'section', slug: 'physical' },
          { label: '体育活动', type: 'search', keyword: '体育 活动' },
        ],
      },
    ],
  },
  {
    key: 'interest',
    label: '兴趣团建 / 社团同好',
    slug: 'interest',
    sections: [
      {
        title: '同好圈层',
        links: [
          { label: '兴趣团建总览', type: 'section', slug: 'interest' },
          { label: '社团活动', type: 'search', keyword: '社团 团建' },
          { label: '桌游电影', type: 'search', keyword: '桌游 电影' },
          { label: '音乐摄影', type: 'search', keyword: '音乐 摄影' },
        ],
      },
      {
        title: '搭子社交',
        links: [
          { label: '组队邀请', type: 'section', slug: 'team' },
          { label: '临时搭子', type: 'search', keyword: '搭子 临时' },
        ],
      },
    ],
  },
  {
    key: 'credit-growth',
    label: '德育 / 智育 / 体育',
    slug: 'moral',
    sections: [
      {
        title: '成长版块',
        links: [
          { label: '德育成长', type: 'section', slug: 'moral' },
          { label: '智育成长', type: 'section', slug: 'intellectual' },
          { label: '体育成长', type: 'section', slug: 'physical' },
        ],
      },
      {
        title: '关键词',
        links: [
          { label: '主题班会', type: 'search', keyword: '班会 主题教育' },
          { label: '学术讲座', type: 'search', keyword: '学术 讲座' },
          { label: '体育赛事', type: 'search', keyword: '体育 赛事' },
        ],
      },
    ],
  },
  {
    key: 'art-labor',
    label: '美育 / 劳育 / 实践',
    slug: 'aesthetic',
    sections: [
      {
        title: '实践入口',
        links: [
          { label: '美育活动', type: 'section', slug: 'aesthetic' },
          { label: '劳育实践', type: 'section', slug: 'labor' },
          { label: '社会实践', type: 'search', keyword: '社会 实践' },
        ],
      },
      {
        title: '推荐关键词',
        links: [
          { label: '艺术展演', type: 'search', keyword: '艺术 展演' },
          { label: '劳动体验', type: 'search', keyword: '劳动 体验' },
          { label: '公益实践', type: 'search', keyword: '公益 实践' },
        ],
      },
    ],
  },
  {
    key: 'innovation',
    label: '创新创业 / 路演竞赛',
    slug: 'innovation',
    sections: [
      {
        title: '创新专区',
        links: [
          { label: '创新创业', type: 'section', slug: 'innovation' },
          { label: '创业路演', type: 'search', keyword: '创业 路演' },
          { label: '训练营', type: 'search', keyword: '创新 训练营' },
          { label: '双创比赛', type: 'search', keyword: '双创 比赛' },
        ],
      },
    ],
  },
  {
    key: 'campus-help',
    label: '校园互助 / 生活服务',
    slug: 'volunteer',
    sections: [
      {
        title: '生活互助',
        links: [
          { label: '校园互助', type: 'search', keyword: '校园 互助' },
          { label: '失物招领', type: 'search', keyword: '失物 招领' },
          { label: '学习搭子', type: 'search', keyword: '学习 搭子' },
        ],
      },
    ],
  },
  {
    key: 'team',
    label: '组队邀请 / 临时搭子',
    slug: 'team',
    sections: [
      {
        title: '组队模式',
        links: [
          { label: '组队邀请总览', type: 'section', slug: 'team' },
          { label: '运动搭子', type: 'search', keyword: '运动 搭子' },
          { label: '团建拼队', type: 'search', keyword: '团建 拼队' },
          { label: '临时搭子', type: 'search', keyword: '临时 搭子' },
        ],
      },
    ],
  },
]

export const featureModules = [
  {
    title: '第二课堂',
    subtitle: '按学分缺口找最合适的活动',
    badge: '学分专区',
    accent: 'credit',
    stat: '6 类学分类型',
    handNote: '',
    paperNote: '',
    icon: 'reading',
    slug: 'second-class',
  },
  {
    title: '志愿服务',
    subtitle: '服务时长、公益活动一键筛选',
    badge: '时长专区',
    accent: 'volunteer',
    stat: '支持时长记录',
    handNote: '',
    paperNote: '',
    icon: 'clock',
    slug: 'volunteer',
  },
  {
    title: '组队邀请',
    subtitle: '球搭子、搭子社交、校园团建',
    badge: '搭子专区',
    accent: 'team',
    stat: '运动与团建并行',
    handNote: '',
    paperNote: '',
    icon: 'connection',
    slug: 'team',
  },
]

export const creditTypeMap = {
  1: '创新创业',
  2: '智育',
  3: '德育',
  4: '体育',
  5: '美育',
  6: '劳育',
}

export const activityTypeMap = {
  1: '第二课堂学分活动',
  2: '志愿服务活动',
  3: '运动健身活动',
  4: '兴趣团建活动',
}

export const activityStatusMap = {
  0: '草稿',
  1: '已发布',
  2: '进行中',
  3: '已结束',
  4: '已取消',
}

export const sectionConfigs = {
  'second-class': {
    title: '第二课堂学分',
    subtitle: '只展示有学分的第二课堂活动',
    description: '聚合所有带学分认定的第二课堂活动，支持再按六类学分继续细分。',
    theme: 'gold',
    match(activity) {
      return activity.activityType === 1 && Number(activity.activityCredit || 0) > 0
    },
  },
  volunteer: {
    title: '志愿服务',
    subtitle: '只展示带志愿服务时长的活动',
    description: '适合按公益服务、时长要求、时间窗口来筛选志愿项目。',
    theme: 'cyan',
    match(activity) {
      return activity.activityType === 2
    },
  },
  innovation: {
    title: '创新创业',
    subtitle: '第二课堂中的创新创业类学分活动',
    description: '双创竞赛、创业讲座、训练营和项目路演都汇总到这一页。',
    theme: 'orange',
    match(activity) {
      return activity.activityType === 1 && activity.creditType === 1
    },
  },
  intellectual: {
    title: '智育成长',
    subtitle: '第二课堂中的智育学分活动',
    description: '学术讲座、知识竞赛、科研分享等智育导向活动。',
    theme: 'blue',
    match(activity) {
      return activity.activityType === 1 && activity.creditType === 2
    },
  },
  moral: {
    title: '德育成长',
    subtitle: '第二课堂中的德育学分活动',
    description: '主题教育、思政学习、志德成长相关活动集中展示。',
    theme: 'rose',
    match(activity) {
      return activity.activityType === 1 && activity.creditType === 3
    },
  },
  physical: {
    title: '体育打卡',
    subtitle: '体育学分与运动成长活动',
    description: '既包含体育学分类活动，也聚合运动打卡与校园赛事。',
    theme: 'mint',
    match(activity) {
      return (activity.activityType === 1 && activity.creditType === 4) || activity.activityType === 3
    },
  },
  aesthetic: {
    title: '美育活动',
    subtitle: '第二课堂中的美育活动',
    description: '艺术展演、观演体验、设计创作与美育课程入口。',
    theme: 'purple',
    match(activity) {
      return activity.activityType === 1 && activity.creditType === 5
    },
  },
  labor: {
    title: '劳育实践',
    subtitle: '第二课堂中的劳育与实践活动',
    description: '劳动教育、服务实践与校内实践任务的集合页。',
    theme: 'green',
    match(activity) {
      return activity.activityType === 1 && activity.creditType === 6
    },
  },
  sports: {
    title: '运动健身',
    subtitle: '校园运动与日常健身活动',
    description: '跑步打卡、球类拼局、校园赛事、健身训练都在这里。',
    theme: 'teal',
    match(activity) {
      return activity.activityType === 3
    },
  },
  interest: {
    title: '兴趣团建',
    subtitle: '社团同好与兴趣活动',
    description: '兴趣社群、电影局、桌游局、社团联谊等内容聚合。',
    theme: 'pink',
    match(activity) {
      return activity.activityType === 4
    },
  },
  team: {
    title: '组队邀请',
    subtitle: '搭子社交与校园拼队',
    description: '球搭子、比赛拼队、团建临时搭子等信息在这个界面集中展示。',
    theme: 'lime',
    match(activity) {
      return activity.activityType === 3 || activity.activityType === 4
    },
    showTeams: true,
  },
}
