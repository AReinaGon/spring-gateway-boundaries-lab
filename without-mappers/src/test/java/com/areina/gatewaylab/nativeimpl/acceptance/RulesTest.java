package com.areina.gatewaylab.nativeimpl.acceptance;

import static org.junit.jupiter.api.Assertions.*;
import com.areina.gatewaylab.nativeimpl.infrastructure.Rules;
import java.util.List;
import org.junit.jupiter.api.Test;

class RulesTest {
    @Test void normalizesUsingAnExplicitAlphabet() {
        assertEquals("alpha", Rules.workspace(List.of(" AlPhA ")));
        assertEquals(400, assertThrows(Rules.Violation.class, () -> Rules.workspace(List.of("../alpha"))).status());
    }
    @Test void rejectsAmbiguousWorkspaceValues() {
        assertThrows(Rules.Violation.class, () -> Rules.workspace(List.of("alpha", "beta")));
    }
    @Test void directoryHasPurposeSpecificIdentityFacts() {
        assertEquals(new Rules.Identity("alice", "reader", "alpha"), Rules.authenticate(List.of("Bearer demo-alpha-reader")));
    }
    @Test void authenticationRejectsUnknownAndDuplicateTokens() {
        assertEquals(401, assertThrows(Rules.Violation.class, () -> Rules.authenticate(List.of("Bearer unknown"))).status());
        assertThrows(Rules.Violation.class, () -> Rules.authenticate(List.of("Bearer demo-alpha-reader", "Bearer demo-alpha-writer")));
    }
    @Test void authorizationRequiresBothWorkspaceAndOperation() {
        assertEquals("read", Rules.authorize("GET", "alpha", "alpha", "reader"));
        assertEquals("write", Rules.authorize("POST", "alpha", "alpha", "writer"));
        assertThrows(Rules.Violation.class, () -> Rules.authorize("POST", "alpha", "alpha", "reader"));
        assertThrows(Rules.Violation.class, () -> Rules.authorize("GET", "beta", "alpha", "writer"));
    }
    @Test void bodyLimitIsInclusive() {
        Rules.checkSize(1024);
        assertEquals(413, assertThrows(Rules.Violation.class, () -> Rules.checkSize(1025)).status());
    }
}
