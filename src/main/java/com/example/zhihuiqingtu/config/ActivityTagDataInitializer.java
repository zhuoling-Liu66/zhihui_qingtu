package com.example.zhihuiqingtu.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zhihuiqingtu.entity.ActivityTag;
import com.example.zhihuiqingtu.repository.ActivityTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 活动标签初始化器。
 * <p>
 * 应用启动完成后会自动执行一次，用于给新数据库补充一批基础活动标签。
 * 这样前端在发布活动或设置兴趣标签时，至少有一套默认标签可选。
 */
@Component
// 被 Spring 扫描后，这个类会在项目启动时自动参与执行。
@RequiredArgsConstructor
// Lombok 自动为 final 字段生成构造器，便于注入 repository。
public class ActivityTagDataInitializer implements CommandLineRunner {

    /**
     * 标签表的数据访问接口。
     */
    private final ActivityTagRepository activityTagRepository;

    /**
     * Spring Boot 启动完成后自动回调的方法。
     *
     * @param args 启动参数
     */
    @Override
    public void run(String... args) {
        Long count = activityTagRepository.selectCount(new LambdaQueryWrapper<ActivityTag>());
        if (count != null && count > 0) {
            // 只在“标签表为空”时插入默认标签，避免重复初始化。
            return;
        }

        List<ActivityTag> defaultTags = List.of(
                ActivityTag.builder().tagName("第二课堂").tagDesc("适用于第二课堂学分活动").build(),
                ActivityTag.builder().tagName("志愿服务").tagDesc("适用于志愿类公益服务").build(),
                ActivityTag.builder().tagName("运动健身").tagDesc("适用于跑步、球类、健身打卡").build(),
                ActivityTag.builder().tagName("兴趣团建").tagDesc("适用于兴趣社团和团建活动").build(),
                ActivityTag.builder().tagName("创新创业").tagDesc("适用于竞赛、讲座、创业训练").build(),
                ActivityTag.builder().tagName("社会实践").tagDesc("适用于实践调研和社会服务").build()
        );

        for (ActivityTag tag : defaultTags) {
            activityTagRepository.insert(tag);
        }
    }
}
