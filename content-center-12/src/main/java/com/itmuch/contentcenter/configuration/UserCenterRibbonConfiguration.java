package com.itmuch.contentcenter.configuration;

import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.RibbonClients;
import org.springframework.context.annotation.Configuration;
import ribbonconfiguration.RibbonConfiguration;

@Configuration
/**
 * 指定ribbon 全局默认配置
 * @RibbonClients不同于@RibbonClient，它 可以为所有的Ribbon客户端提供默认配置
 */
@RibbonClients(defaultConfiguration = RibbonConfiguration.class)
//指定负载目标服务 和 负载配置
//@RibbonClient(name = "user-center",configuration = RibbonConfiguration.class)
public class UserCenterRibbonConfiguration {
}
