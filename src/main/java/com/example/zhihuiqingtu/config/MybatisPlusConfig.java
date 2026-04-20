package com.example.zhihuiqingtu.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置类。
 * <p>
 * 主要负责两件事：
 * <ol>
 *     <li>注册分页插件和安全插件。</li>
 *     <li>自动填充 createTime、updateTime、deleteFlag 等公共字段。</li>
 * </ol>
 */
@Configuration
public class MybatisPlusConfig implements MetaObjectHandler {

    /**
     * 注册 MyBatis-Plus 总拦截器。
     * <p>
     * PaginationInnerInterceptor：自动处理分页 SQL。
     * BlockAttackInnerInterceptor：阻止危险的全表更新、全表删除。
     *
     * @return 配置好的 MyBatis-Plus 拦截器对象
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }

    /**
     * 插入数据时自动填充公共字段。
     *
     * @param metaObject MyBatis 提供的元对象，可理解为“当前要入库的对象包装器”
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        strictInsertFill(metaObject, "deleteFlag", Integer.class, 0);
    }

    /**
     * 更新数据时自动刷新更新时间。
     *
     * @param metaObject MyBatis 提供的元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
