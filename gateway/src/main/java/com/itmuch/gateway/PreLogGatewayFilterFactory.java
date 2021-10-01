package com.itmuch.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractNameValueGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
@Component
public class PreLogGatewayFilterFactory
    extends AbstractNameValueGatewayFilterFactory {
    @Override
    public GatewayFilter apply(NameValueConfig config) {
        return ((exchange, chain) -> {
            log.info("请求进来了...{},{}", config.getName(), config.getValue());
            //修改请求
            ServerHttpRequest modifiedRequest = exchange.getRequest()
                .mutate()
                .build();


            //修改exchange 包括其中请请求
            ServerWebExchange modifiedExchange = exchange.mutate()
                .request(modifiedRequest)
                .build();

            //修改响应
            exchange.getResponse();

            //传递给下一个过滤器
            return chain.filter(modifiedExchange);
        });
    }
}
