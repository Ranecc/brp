package dev.brp.common.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tiny SpEL helper for annotation keys (compiled expressions cached per method). */
public final class SpelKeys {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final Map<String, Expression> COMPILED = new ConcurrentHashMap<>();

    private SpelKeys() {}

    public static String eval(String expr, ProceedingJoinPoint pjp) {
        if (expr == null || expr.isEmpty()) {
            return "";
        }
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Expression compiled = COMPILED.computeIfAbsent(
                method.getDeclaringClass().getName() + '#' + method.getName() + '|' + expr,
                ignored -> PARSER.parseExpression(expr));
        var ctx = new MethodBasedEvaluationContext(null, method, pjp.getArgs(), DISCOVERER);
        return String.valueOf(compiled.getValue(ctx));
    }

    public static Method method(ProceedingJoinPoint pjp) {
        return ((MethodSignature) pjp.getSignature()).getMethod();
    }
}
