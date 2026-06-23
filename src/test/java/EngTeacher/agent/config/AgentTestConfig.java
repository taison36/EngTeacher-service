package EngTeacher.agent.config;

import EngTeacher.agent.utils.ToolCallRecorder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class AgentTestConfig {

    @Bean
    public ToolCallRecorder toolCallRecorder() {
        return new ToolCallRecorder();
    }

    @Bean
    public BeanPostProcessor toolCallbackWrappingPostProcessor(ToolCallRecorder recorder) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof ToolCallback tc) {
                    return recorder.wrap(tc);
                }
                return bean;
            }
        };
    }
}
