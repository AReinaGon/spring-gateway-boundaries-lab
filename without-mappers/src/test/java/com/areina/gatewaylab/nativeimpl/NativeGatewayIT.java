package com.areina.gatewaylab.nativeimpl;

import com.areina.gatewaylab.nativeimpl.acceptance.AbstractGatewayIT;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = NativeGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NativeGatewayIT extends AbstractGatewayIT {
    @Override protected boolean mapped() { return false; }
}
