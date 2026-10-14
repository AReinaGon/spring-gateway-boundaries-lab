package com.areina.gatewaylab.nativeimpl.filters;

import com.areina.gatewaylab.nativeimpl.infrastructure.FilterRuntime;
import com.areina.gatewaylab.nativeimpl.infrastructure.Rules;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

@Component
public final class TokenIdentityGatewayFilterFactory
        extends AbstractGatewayFilterFactory<TokenIdentityGatewayFilterFactory.Config> {
    public TokenIdentityGatewayFilterFactory() { super(Config.class); }

    @Override public GatewayFilter apply(Config config) {
        return (exchange, chain) -> FilterRuntime.run(exchange, "token", current -> {
            var identity = Rules.authenticate(current.getRequest().getHeaders().get("Authorization"));
            var request = current.getRequest().mutate().headers(headers -> {
                headers.set(Rules.SUBJECT, identity.subject());
                headers.set(Rules.ROLE, identity.role());
                headers.set(Rules.TOKEN_WORKSPACE, identity.workspace());
            }).build();
            return chain.filter(current.mutate().request(request).build());
        });
    }

    public static final class Config {}
}
