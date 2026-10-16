package com.areina.gatewaylab.nativeimpl.filters;

import com.areina.gatewaylab.nativeimpl.infrastructure.FilterRuntime;
import com.areina.gatewaylab.nativeimpl.infrastructure.Rules;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

@Component
public final class AuthorizeOperationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<AuthorizeOperationGatewayFilterFactory.Config> {
    public AuthorizeOperationGatewayFilterFactory() { super(Config.class); }

    @Override public GatewayFilter apply(Config config) {
        return (exchange, chain) -> FilterRuntime.run(exchange, "authorize", current -> {
            var request = current.getRequest();
            var headers = request.getHeaders();
            String action = Rules.authorize(request.getMethod().name(), headers.getFirst(Rules.WORKSPACE),
                    headers.getFirst(Rules.TOKEN_WORKSPACE), headers.getFirst(Rules.ROLE));
            var transformed = request.mutate().headers(updated -> {
                updated.set(Rules.ACTION, action);
                updated.remove("Authorization");
                updated.remove(Rules.ROLE);
                updated.remove(Rules.TOKEN_WORKSPACE);
            }).build();
            return chain.filter(current.mutate().request(transformed).build());
        });
    }

    public static final class Config {}
}
