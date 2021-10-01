package ribbonconfiguration;

import com.itmuch.contentcenter.configuration.NacosSameClusterWeightedRule;
import com.itmuch.contentcenter.configuration.NacosWeightedRule;
import com.netflix.loadbalancer.IRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 不可以放在 spring-boot 上下文
 * spring-boot 和 ribbon 上下文重叠 导致 RibbonConfiguration 被所有ribbon客户端共享
 */
@Configuration
public class RibbonConfiguration {
    /**
     * 指定负载均衡规则
     * @return
     */
    @Bean
    public IRule ribbonRule() {
        return new NacosSameClusterWeightedRule();
    }
}
