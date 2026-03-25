package com.zeta.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();

        return chain.filter(exchange)
                .doOnError(error -> log.error("Gateway request failed method={} path={} message={}", method, path, error.getMessage()))
                .doFinally(signalType -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                    int status = statusCode != null ? statusCode.value() : 500;
                    log.info("Gateway request completed method={} path={} status={} durationMs={}", method, path, status, durationMs);
                });
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
