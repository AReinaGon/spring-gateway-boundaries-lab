package com.areina.gatewaylab.nativeimpl;

import com.areina.gatewaylab.nativeimpl.acceptance.AbstractCompositionTest;
import com.areina.gatewaylab.nativeimpl.filters.BodyLimitGatewayFilterFactory;
import com.areina.gatewaylab.nativeimpl.filters.NormalizeWorkspaceGatewayFilterFactory;
import com.areina.gatewaylab.nativeimpl.filters.TokenIdentityGatewayFilterFactory;
import com.areina.gatewaylab.nativeimpl.filters.AuthorizeOperationGatewayFilterFactory;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilter;

class NativeCompositionTest extends AbstractCompositionTest {
    @Override protected List<GatewayFilter> filters() {
        return List.of(new BodyLimitGatewayFilterFactory().apply(new BodyLimitGatewayFilterFactory.Config()),
                new NormalizeWorkspaceGatewayFilterFactory().apply(new NormalizeWorkspaceGatewayFilterFactory.Config()),
                new TokenIdentityGatewayFilterFactory().apply(new TokenIdentityGatewayFilterFactory.Config()),
                new AuthorizeOperationGatewayFilterFactory().apply(new AuthorizeOperationGatewayFilterFactory.Config()));
    }
    @Override protected boolean mapped() { return false; }
}
