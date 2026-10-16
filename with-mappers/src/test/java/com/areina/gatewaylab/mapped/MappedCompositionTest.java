package com.areina.gatewaylab.mapped;

import com.areina.gatewaylab.mapped.acceptance.AbstractCompositionTest;
import com.areina.gatewaylab.mapped.filters.BodyLimitGatewayFilterFactory;
import com.areina.gatewaylab.mapped.filters.NormalizeWorkspaceGatewayFilterFactory;
import com.areina.gatewaylab.mapped.filters.TokenIdentityGatewayFilterFactory;
import com.areina.gatewaylab.mapped.filters.AuthorizeOperationGatewayFilterFactory;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilter;

class MappedCompositionTest extends AbstractCompositionTest {
    @Override protected List<GatewayFilter> filters() {
        return List.of(new BodyLimitGatewayFilterFactory().apply(new BodyLimitGatewayFilterFactory.Config()),
                new NormalizeWorkspaceGatewayFilterFactory().apply(new NormalizeWorkspaceGatewayFilterFactory.Config()),
                new TokenIdentityGatewayFilterFactory().apply(new TokenIdentityGatewayFilterFactory.Config()),
                new AuthorizeOperationGatewayFilterFactory().apply(new AuthorizeOperationGatewayFilterFactory.Config()));
    }
    @Override protected boolean mapped() { return true; }
}
