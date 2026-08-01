package io.github.kalayciburak.stackmate.context;

import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.incident.RequestContext;
import io.github.kalayciburak.stackmate.incident.SpringRuntimeContext;
import io.github.kalayciburak.stackmate.incident.SpringRuntimeContext.TransactionState;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.core.SpringVersion;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/** Extracts a small immutable servlet and Spring context without inspecting application beans. */
public final class ServletContextExtractor {

    private static final int MAX_SHORT_VALUE = 256;
    private static final int MAX_PATH = 1_024;
    private static final int MAX_PROFILES = 16;
    private static final int MAX_MDC_KEYS = 16;
    private static final String TRANSACTION_SYNCHRONIZATION_MANAGER =
            "org.springframework.transaction.support.TransactionSynchronizationManager";

    private final Environment environment;
    private final BeanFactory beanFactory;
    private final StackMateProperties properties;
    private final Sanitizer sanitizer;

    public ServletContextExtractor(
            Environment environment,
            BeanFactory beanFactory,
            StackMateProperties properties,
            Sanitizer sanitizer) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    public ServletIncidentContext extract(
            HttpServletRequest request, HttpServletResponse response) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(response, "response");

        HandlerMetadata handler = handlerMetadata(request);
        RequestContext requestContext =
                new RequestContext(
                        safeServletValue(request::getMethod, "method", MAX_SHORT_VALUE),
                        pathWithoutQuery(request),
                        handler.className(),
                        handler.methodName(),
                        responseStatus(response),
                        selectedMdcValues());
        SpringRuntimeContext springContext =
                new SpringRuntimeContext(
                        applicationName(),
                        activeProfiles(),
                        applicationPackage(handler.packageName()),
                        safeVersion(SpringBootVersion::getVersion),
                        safeVersion(SpringVersion::getVersion),
                        TransactionStateDetector.current());
        return new ServletIncidentContext(requestContext, springContext);
    }

    private HandlerMetadata handlerMetadata(HttpServletRequest request) {
        Object attribute;
        try {
            attribute = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        } catch (RuntimeException failure) {
            return HandlerMetadata.none();
        }
        if (!(attribute instanceof HandlerMethod handlerMethod)) {
            return HandlerMetadata.none();
        }
        try {
            Class<?> beanType = ClassUtils.getUserClass(handlerMethod.getBeanType());
            String className = safeSanitize("className", beanType.getName(), MAX_SHORT_VALUE);
            String methodName =
                    safeSanitize(
                            "methodName", handlerMethod.getMethod().getName(), MAX_SHORT_VALUE);
            Package handlerPackage = beanType.getPackage();
            String packageName = handlerPackage == null ? "" : handlerPackage.getName();
            return new HandlerMetadata(className, methodName, packageName);
        } catch (RuntimeException | LinkageError failure) {
            return HandlerMetadata.none();
        }
    }

    private String pathWithoutQuery(HttpServletRequest request) {
        String path = safeServletValue(request::getRequestURI, "path", MAX_PATH);
        int queryStart = path.indexOf('?');
        return queryStart < 0 ? path : path.substring(0, queryStart);
    }

    private int responseStatus(HttpServletResponse response) {
        try {
            int status = response.getStatus();
            return status >= 100 && status <= 599 ? status : 0;
        } catch (RuntimeException | LinkageError failure) {
            return 0;
        }
    }

    private Map<String, String> selectedMdcValues() {
        Map<String, String> selected = new LinkedHashMap<>();
        List<String> keys = properties.getCapture().getSelectedMdcKeys();
        for (int index = 0; index < keys.size() && selected.size() < MAX_MDC_KEYS; index++) {
            String configuredKey = keys.get(index);
            String key = safeSanitize("mdcKey", configuredKey, MAX_SHORT_VALUE);
            if (key.isBlank() || selected.containsKey(key)) {
                continue;
            }
            try {
                String value = MDC.get(configuredKey);
                if (value != null && !value.isBlank()) {
                    selected.put(key, safeSanitize(configuredKey, value, MAX_SHORT_VALUE));
                }
            } catch (RuntimeException | LinkageError ignored) {
                // An absent or failing MDC provider must not affect application exception handling.
            }
        }
        return Map.copyOf(selected);
    }

    private String applicationName() {
        try {
            String name = environment.getProperty("spring.application.name", "");
            return safeSanitize("applicationName", name, MAX_SHORT_VALUE);
        } catch (RuntimeException | LinkageError failure) {
            return "";
        }
    }

    private List<String> activeProfiles() {
        Set<String> profiles = new LinkedHashSet<>();
        try {
            for (String profile : environment.getActiveProfiles()) {
                if (profiles.size() == MAX_PROFILES) {
                    break;
                }
                String safe = safeSanitize("profile", profile, MAX_SHORT_VALUE);
                if (!safe.isBlank()) {
                    profiles.add(safe);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
        return List.copyOf(profiles);
    }

    private String applicationPackage(String handlerPackage) {
        List<String> configured = properties.getApplicationPackages();
        if (!configured.isEmpty()) {
            return safeSanitize("packageName", configured.getFirst(), MAX_SHORT_VALUE);
        }
        try {
            if (AutoConfigurationPackages.has(beanFactory)) {
                List<String> bootPackages = AutoConfigurationPackages.get(beanFactory);
                if (!bootPackages.isEmpty()) {
                    return safeSanitize("packageName", bootPackages.getFirst(), MAX_SHORT_VALUE);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A request handler package remains a safe fallback.
        }
        return safeSanitize("packageName", handlerPackage, MAX_SHORT_VALUE);
    }

    private String safeVersion(ValueSupplier supplier) {
        try {
            String value = supplier.get();
            return safeSanitize("version", value == null ? "" : value, MAX_SHORT_VALUE);
        } catch (RuntimeException | LinkageError failure) {
            return "";
        }
    }

    private String safeServletValue(ValueSupplier supplier, String fieldName, int limit) {
        try {
            String value = supplier.get();
            return safeSanitize(fieldName, value == null ? "" : value, limit);
        } catch (RuntimeException | LinkageError failure) {
            return "";
        }
    }

    private String safeSanitize(String fieldName, String value, int limit) {
        String sanitized;
        try {
            sanitized = sanitizer.sanitize(fieldName, value == null ? "" : value);
        } catch (RuntimeException | LinkageError failure) {
            sanitized = "";
        }
        return truncate(sanitized == null ? "" : sanitized, limit);
    }

    private static String truncate(String value, int maximumCodePoints) {
        if (value.codePointCount(0, value.length()) <= maximumCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maximumCodePoints));
    }

    private record HandlerMetadata(String className, String methodName, String packageName) {

        private static HandlerMetadata none() {
            return new HandlerMetadata("", "", "");
        }
    }

    @FunctionalInterface
    private interface ValueSupplier {
        String get();
    }

    private static final class TransactionStateDetector {

        private TransactionStateDetector() {}

        private static TransactionState current() {
            ClassLoader classLoader = ServletContextExtractor.class.getClassLoader();
            if (!ClassUtils.isPresent(TRANSACTION_SYNCHRONIZATION_MANAGER, classLoader)) {
                return TransactionState.UNKNOWN;
            }
            try {
                return SpringTransactionApi.isTransactionActive()
                        ? TransactionState.ACTIVE
                        : TransactionState.INACTIVE;
            } catch (RuntimeException | LinkageError failure) {
                return TransactionState.UNKNOWN;
            }
        }
    }

    private static final class SpringTransactionApi {

        private SpringTransactionApi() {}

        private static boolean isTransactionActive() {
            return org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive();
        }
    }
}
