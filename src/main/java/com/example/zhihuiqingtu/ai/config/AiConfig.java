package com.example.zhihuiqingtu.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import com.example.zhihuiqingtu.ai.client.SpringAiChatGateway;
import com.example.zhihuiqingtu.security.CurrentUser;

/**
 * AI 模块的基础设施配置。
 *
 * <p>这里只放"与具体模型无关"的东西：线程池。ChatClient 由 Spring AI 自动配置提供，
 * 在 {@code SpringAiChatGateway} 里按需取用（取不到就降级，而不是让应用启动失败）。
 */
@Slf4j
@Configuration
public class AiConfig {

    /**
     * 流式对话专用线程池。
     *
     * <p><b>为什么必须用 {@link DelegatingSecurityContextExecutor} 包装</b>：
     * {@code SecurityContextHolder} 默认是 ThreadLocal，不会跨线程传递。
     * 流式回复跑在独立线程里，若不包装，工具与 Service 里的
     * {@code CurrentUser.requireUserId()} 会读不到身份并抛 IllegalStateException。
     * 包装器在 {@code execute()} 提交任务的瞬间捕获上下文，子线程执行时再设回去。
     *
     * <p><b>为什么有界</b>：SSE 是长连接，每个连接占一个线程。无界队列会把内存吃光，
     * 队列满时走 {@code CallerRunsPolicy} 让提交线程自己跑，形成自然的背压。
     */
    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-chat-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：等待在途对话完成，避免用户看到"回复到一半消失"
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        log.info("AI 对话线程池已就绪：core=4 max=16 queue=100（已包装 SecurityContext 传递）");
        return new DelegatingSecurityContextExecutor(executor);
    }
}
