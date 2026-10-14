package com.areina.gatewaylab.mapped;

import com.areina.gatewaylab.mapped.acceptance.AbstractGatewayIT;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = MappedGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MappedGatewayIT extends AbstractGatewayIT {
    @Override protected boolean mapped() { return true; }
}
