package com.areina.gatewaylab.nativeimpl.filters;

import com.areina.gatewaylab.nativeimpl.infrastructure.FilterRuntime;
import com.areina.gatewaylab.nativeimpl.infrastructure.BodySupport;
import com.areina.gatewaylab.nativeimpl.infrastructure.Rules;
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
                    Rules.checkSize(cached.getAttributeOrDefault(BodySupport.SIZE, 0L));
                    return chain.filter(cached);
                }));
    }

    public static final class Config {}
}
