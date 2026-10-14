package com.areina.gatewaylab.mapped.filters;

import com.areina.gatewaylab.mapped.infrastructure.FilterRuntime;
import com.areina.gatewaylab.mapped.MappedRules;
import com.areina.gatewaylab.mapped.RequestMapper;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

@Component
public final class AuthorizeOperationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<AuthorizeOperationGatewayFilterFactory.Config> {
    public AuthorizeOperationGatewayFilterFactory() { super(Config.class); }

    @Override public GatewayFilter apply(Config config) {
        return (exchange, chain) -> FilterRuntime.run(exchange, "authorize", current -> {
            var changes = MappedRules.authorize(RequestMapper.read(current));
            return chain.filter(RequestMapper.apply(current, changes));
        });
    }

    public static final class Config {}
}
