package com.example.zhihuiqingtu.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zhihuiqingtu.entity.ActivityTag;
import com.example.zhihuiqingtu.repository.ActivityTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时补齐默认活动标签，避免老库缺少基础标签数据。
 */
@Component
@RequiredArgsConstructor
public class ActivityTagDataInitializer implements CommandLineRunner {

    private final ActivityTagRepository activityTagRepository;

    @Override
    public void run(String... args) {
        Long count = activityTagRepository.selectCount(new LambdaQueryWrapper<ActivityTag>());
        if (count != null && count > 0) {
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
