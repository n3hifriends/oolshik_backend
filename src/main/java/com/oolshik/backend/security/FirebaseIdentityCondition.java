package com.oolshik.backend.security;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class FirebaseIdentityCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String value = context.getEnvironment().getProperty("app.security.identity-provider", "local");
        return "firebase".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }
}
