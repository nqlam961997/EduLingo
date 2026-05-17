package com.edulingo.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeploymentSecurityCheck {

    private static final Logger log = LoggerFactory.getLogger(DeploymentSecurityCheck.class);

    private static final String DEMO_JWT_SECRET =
            "c2VjcmV0LWtleS1mb3ItZWR1bGluZ28tZGVtby1jaGFuZ2UtaW4tcHJvZHVjdGlvbg==";

    @Value("${deployment.domain:}")
    private String domain;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @PostConstruct
    void verify() {
        boolean publicDeploy = domain != null && !domain.isBlank();
        boolean usingDemoSecret = jwtSecret == null
                || jwtSecret.isBlank()
                || jwtSecret.equals(DEMO_JWT_SECRET);

        if (publicDeploy && usingDemoSecret) {
            String msg = "Refusing to start: DOMAIN is set ('" + domain
                    + "') but JWT_SECRET is unset or equal to the demo placeholder. "
                    + "Generate a strong secret (e.g. `openssl rand -base64 48`) "
                    + "and set JWT_SECRET in your .env before deploying publicly.";
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        if (usingDemoSecret) {
            log.warn("Starting with the demo JWT secret. This is only safe for local development.");
        }
    }
}
