package com.example.zhihuiqingtu.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 通用基础字段抽象，所有实体的父类；包含：
 * 1.创建时间
 * 2.更新时间
 * 3.逻辑删除
 */
@Getter
@Setter
public abstract class BaseEntity {

    /**
     * 创建时间。
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 软删除标记，0正常，1删除
     */
    @TableLogic(value = "0", delval = "1")
    /*
        配置mybatis-plus的逻辑删除：非真实删除数据，就是给数据添加状态资源，进行状态的修改和查询时候添加状态条件
            这里采用局部的方法：
            1.找到对应的实体类，找到的删除逻辑字段对应的属性
            2.只需要添加一个@TableLogic注解
        但其实也可以采用全局配置--官网
            赋的值是0是1还是其他的数来代表正常/删除无所谓，但要和数据库对应的地方协调好。
     */
    @TableField(value = "delete_flag", fill = FieldFill.INSERT)
    private Integer deleteFlag;
    /*
    一定要注意的事项：一旦配置了逻辑删除字段，mybatis-plus提供的方法制动添加逻辑删除的处理
    一旦我们后期自己定义mapperxml下的sql语句，需要我们自己添加逻辑删除判断
     */
}
