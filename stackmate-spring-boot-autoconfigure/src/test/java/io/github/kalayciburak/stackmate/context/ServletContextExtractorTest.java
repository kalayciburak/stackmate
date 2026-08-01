package io.github.kalayciburak.stackmate.context;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.incident.SpringRuntimeContext.TransactionState;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.core.SpringVersion;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

class ServletContextExtractorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void extractsBoundedSafeRequestAndSpringFacts() throws Exception {
        StackMateProperties properties = new StackMateProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.application.name", "orders\u001B[31m");
        environment.setActiveProfiles("dev", "test");
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        AutoConfigurationPackages.register(beanFactory, "com.example.boot");
        ServletContextExtractor extractor = extractor(environment, beanFactory, properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.setRequestURI("/orders?token=secret");
        Method method = ExampleHandler.class.getDeclaredMethod("submit");
        request.setAttribute(
                HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new HandlerMethod(new ExampleHandler(), method));
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(422);
        MDC.put("traceId", "trace-123");
        MDC.put("ignored", "must-not-be-read");

        ServletIncidentContext context = extractor.extract(request, response);

        assertThat(context.request().method()).isEqualTo("POST");
        assertThat(context.request().path()).isEqualTo("/orders");
        assertThat(context.request().handlerClass()).isEqualTo(ExampleHandler.class.getName());
        assertThat(context.request().handlerMethod()).isEqualTo("submit");
        assertThat(context.request().responseStatus()).isEqualTo(422);
        assertThat(context.request().selectedCorrelationIds())
                .hasSize(1)
                .containsEntry("traceId", "trace-123");
        assertThat(context.springRuntime().applicationName()).isEqualTo("orders");
        assertThat(context.springRuntime().activeProfiles()).containsExactly("dev", "test");
        assertThat(context.springRuntime().applicationPackage()).isEqualTo("com.example.boot");
        assertThat(context.springRuntime().bootVersion()).isEqualTo(SpringBootVersion.getVersion());
        assertThat(context.springRuntime().frameworkVersion())
                .isEqualTo(SpringVersion.getVersion());
        assertThat(context.springRuntime().transactionState()).isEqualTo(TransactionState.INACTIVE);
    }

    @Test
    void explicitApplicationPackageTakesPrecedenceOverBootPackage() {
        StackMateProperties properties = new StackMateProperties();
        properties.setApplicationPackages(List.of("com.explicit"));
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        AutoConfigurationPackages.register(beanFactory, "com.boot");
        ServletContextExtractor extractor =
                extractor(new MockEnvironment(), beanFactory, properties);

        ServletIncidentContext context =
                extractor.extract(
                        new MockHttpServletRequest("GET", "/"), new MockHttpServletResponse());

        assertThat(context.springRuntime().applicationPackage()).isEqualTo("com.explicit");
    }

    @Test
    void handlerPackageIsTheSafeFallbackWhenBootPackageIsUnavailable() throws Exception {
        ServletContextExtractor extractor =
                extractor(
                        new MockEnvironment(),
                        new DefaultListableBeanFactory(),
                        new StackMateProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fallback");
        request.setAttribute(
                HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new HandlerMethod(
                        new ExampleHandler(), ExampleHandler.class.getDeclaredMethod("submit")));

        ServletIncidentContext context = extractor.extract(request, new MockHttpServletResponse());

        assertThat(context.springRuntime().applicationPackage())
                .isEqualTo(ExampleHandler.class.getPackageName());
    }

    @Test
    void omitsUnavailableHandlerMdcAndInvalidStatusFacts() {
        StackMateProperties properties = new StackMateProperties();
        properties.getCapture().setSelectedMdcKeys(List.of("missing"));
        ServletContextExtractor extractor =
                extractor(new MockEnvironment(), new DefaultListableBeanFactory(), properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/plain");
        request.setAttribute(
                HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, "not-a-handler-method");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(700);

        ServletIncidentContext context = extractor.extract(request, response);

        assertThat(context.request().handlerClass()).isEmpty();
        assertThat(context.request().handlerMethod()).isEmpty();
        assertThat(context.request().responseStatus()).isZero();
        assertThat(context.request().selectedCorrelationIds()).isEmpty();
        assertThat(context.springRuntime().applicationPackage()).isEmpty();
    }

    @Test
    void extractsTheBoundedSelectedMdcEntriesAndSanitizesValues() {
        StackMateProperties properties = new StackMateProperties();
        List<String> keys =
                java.util.stream.IntStream.range(0, 16).mapToObj(index -> "key" + index).toList();
        properties.getCapture().setSelectedMdcKeys(keys);
        keys.forEach(key -> MDC.put(key, "value\u0000" + key));
        ServletContextExtractor extractor =
                extractor(new MockEnvironment(), new DefaultListableBeanFactory(), properties);

        ServletIncidentContext context =
                extractor.extract(
                        new MockHttpServletRequest("GET", "/"), new MockHttpServletResponse());

        assertThat(context.request().selectedCorrelationIds()).hasSize(16);
        assertThat(context.request().selectedCorrelationIds().values())
                .allMatch(value -> !value.contains("\u0000"));
    }

    private ServletContextExtractor extractor(
            MockEnvironment environment,
            DefaultListableBeanFactory beanFactory,
            StackMateProperties properties) {
        return new ServletContextExtractor(
                environment, beanFactory, properties, DefaultSanitizer.defaults());
    }

    private static final class ExampleHandler {

        @SuppressWarnings("unused")
        private void submit() {}
    }
}
