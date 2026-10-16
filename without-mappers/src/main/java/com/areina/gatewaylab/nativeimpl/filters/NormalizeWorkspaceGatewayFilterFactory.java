package com.areina.gatewaylab.nativeimpl.filters;

import com.areina.gatewaylab.nativeimpl.infrastructure.FilterRuntime;
import com.areina.gatewaylab.nativeimpl.infrastructure.Rules;
import java.util.Locale;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

@Component
public final class NormalizeWorkspaceGatewayFilterFactory
        extends AbstractGatewayFilterFactory<NormalizeWorkspaceGatewayFilterFactory.Config> {
    public NormalizeWorkspaceGatewayFilterFactory() { super(Config.class); }

    @Override public GatewayFilter apply(Config config) {
        return (exchange, chain) -> FilterRuntime.run(exchange, "normalize", current -> {
            String workspace = Rules.workspace(current.getRequest().getHeaders().get(Rules.WORKSPACE));
            var request = current.getRequest().mutate().headers(headers -> {
                headers.keySet().removeIf(key -> key.toLowerCase(Locale.ROOT).startsWith("x-lab-"));
                headers.set(Rules.WORKSPACE, workspace);
            }).build();
            return chain.filter(current.mutate().request(request).build());
        });
    }

    public static final class Config {}
}
