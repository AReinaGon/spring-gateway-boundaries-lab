package com.areina.gatewaylab.mapped.filters;

import com.areina.gatewaylab.mapped.infrastructure.FilterRuntime;
import com.areina.gatewaylab.mapped.infrastructure.BodySupport;
import com.areina.gatewaylab.mapped.MappedRules;
import com.areina.gatewaylab.mapped.RequestMapper;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

@Component
public final class BodyLimitGatewayFilterFactory
        extends AbstractGatewayFilterFactory<BodyLimitGatewayFilterFactory.Config> {
    public BodyLimitGatewayFilterFactory() { super(Config.class); }

    @Override public GatewayFilter apply(Config config) {
        return (exchange, chain) -> FilterRuntime.run(exchange, "size", current ->
                BodySupport.cacheBounded(current).flatMap(cached -> {
                    var changes = MappedRules.size(RequestMapper.read(cached));
                    return chain.filter(RequestMapper.apply(cached, changes));
                }));
    }

    public static final class Config {}
}
