package io.github.kalayciburak.stackmate.benchmark.corpus.realworld;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.Lifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.web.context.request.RequestScope;

final class RealWorldJavaSpringFixtures {

    private static final long THREAD_TIMEOUT_MILLIS = 5_000;
    private static final long RECURSION_STACK_BYTES = 128 * 1024;

    private RealWorldJavaSpringFixtures() {}

    static boolean supports(String familyId) {
        if (familyId == null) {
            return false;
        }
        return switch (familyId) {
            case "G53-R-JV-001",
                            "G53-R-JV-002",
                            "G53-R-JV-003",
                            "G53-R-JV-004",
                            "G53-R-JV-005",
                            "G53-R-JV-006",
                            "G53-R-JV-007",
                            "G53-R-JV-008",
                            "G53-R-JV-009",
                            "G53-R-JV-010",
                            "G53-R-JV-011",
                            "G53-R-JV-012",
                            "G53-R-JV-013",
                            "G53-R-JV-014",
                            "G53-R-JV-015",
                            "G53-R-JV-016",
                            "G53-R-SP-001",
                            "G53-R-SP-002",
                            "G53-R-SP-003",
                            "G53-R-SP-004",
                            "G53-R-SP-005",
                            "G53-R-SP-006",
                            "G53-R-SP-007",
                            "G53-R-SP-008",
                            "G53-R-SP-009",
                            "G53-R-SP-010",
                            "G53-R-SP-011",
                            "G53-R-SP-012",
                            "G53-R-SP-013",
                            "G53-R-SP-014" -> true;
            default -> false;
        };
    }

    static Throwable execute(String familyId, int variant) {
        if (variant != 0 && variant != 1) {
            throw new IllegalArgumentException("variant must be 0 or 1: " + variant);
        }
        if (!supports(familyId)) {
            throw new IllegalArgumentException("unsupported real-world family: " + familyId);
        }

        return switch (familyId) {
            case "G53-R-JV-003" -> stackOverflowFailure(variant);
            case "G53-R-SP-005" -> inactiveRequestScopeFailure(variant);
            default -> capture(() -> executeCaptured(familyId, variant));
        };
    }

    private static void executeCaptured(String familyId, int variant) throws Exception {
        switch (familyId) {
            case "G53-R-JV-001" -> immutableListMutation(variant);
            case "G53-R-JV-002" -> absentClassLoading(variant);
            case "G53-R-JV-004" -> concurrentListModification(variant);
            case "G53-R-JV-005" -> integerDivisionByZero(variant);
            case "G53-R-JV-006" -> strictInvalidDate(variant);
            case "G53-R-JV-007" -> unclosedCharacterClass(variant);
            case "G53-R-JV-008" -> invalidSerializationHeader(variant);
            case "G53-R-JV-009" -> malformedUtf8(variant);
            case "G53-R-JV-010" -> closedZipFileSystem(variant);
            case "G53-R-JV-011" -> unsupportedFileSystemProvider(variant);
            case "G53-R-JV-012" -> byteBufferUnderflow(variant);
            case "G53-R-JV-013" -> readOnlyByteBufferWrite(variant);
            case "G53-R-JV-014" -> absentResourceBundle(variant);
            case "G53-R-JV-015" -> incompatibleFormatConversion(variant);
            case "G53-R-JV-016" -> absentServiceProvider(variant);
            case "G53-R-SP-001" -> duplicateBeanDefinition(variant);
            case "G53-R-SP-002" -> absentSpringConverter(variant);
            case "G53-R-SP-003" -> wrongRequiredBeanType(variant);
            case "G53-R-SP-004" -> dereferenceNonFactoryBean(variant);
            case "G53-R-SP-006" -> lookupDuringSingletonDestruction(variant);
            case "G53-R-SP-007" -> malformedSpringBeanXml(variant);
            case "G53-R-SP-008" -> failingLifecycleStart(variant);
            case "G53-R-SP-009" -> absentSpelProperty(variant);
            case "G53-R-SP-010" -> absentSpringMessage(variant);
            case "G53-R-SP-011" -> instantiateAbstractBean(variant);
            case "G53-R-SP-012" -> inconsistentAliasMetadata(variant);
            case "G53-R-SP-013" -> conflictingScannedComponents(variant);
            case "G53-R-SP-014" -> voidBeanMethod(variant);
            default -> throw new AssertionError("validated family was not dispatched: " + familyId);
        }
    }

    private static void immutableListMutation(int variant) {
        List<String> values =
                variant == 0 ? List.of("alpha", "beta") : List.of("gamma", "delta");
        values.add("controlled");
    }

    private static void absentClassLoading(int variant) throws ClassNotFoundException {
        Class.forName("com.example.gatec.AbsentFixture" + variant);
    }

    private static Throwable stackOverflowFailure(int variant) {
        return failureInThread(
                "stackmate-real-world-recursion-" + variant,
                RECURSION_STACK_BYTES,
                () -> recurseUntilOverflow(variant));
    }

    private static int recurseUntilOverflow(int value) {
        return value + recurseUntilOverflow(value + 1);
    }

    private static void concurrentListModification(int variant) {
        List<String> values = new ArrayList<>(List.of("alpha", "beta"));
        Iterator<String> iterator = values.iterator();
        values.add(variant == 0 ? "gamma" : "delta");
        iterator.next();
    }

    private static void integerDivisionByZero(int variant) {
        int dividend = variant == 0 ? 17 : 29;
        int divisor = variant - variant;
        int ignored = dividend / divisor;
    }

    private static void strictInvalidDate(int variant) {
        String value = variant == 0 ? "2026-02-30" : "2025-02-29";
        DateTimeFormatter formatter =
                DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT);
        LocalDate.parse(value, formatter);
    }

    private static void unclosedCharacterClass(int variant) {
        Pattern.compile(variant == 0 ? "[alpha" : "[beta");
    }

    private static void invalidSerializationHeader(int variant) throws IOException {
        byte[] bytes =
                variant == 0
                        ? new byte[] {0x00, 0x01, 0x02, 0x03}
                        : new byte[] {0x10, 0x11, 0x12, 0x13};
        try (ObjectInputStream ignored =
                new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            // Construction validates the stream header.
        }
    }

    private static void malformedUtf8(int variant) throws IOException {
        byte[] bytes =
                variant == 0
                        ? new byte[] {(byte) 0xC3, 0x28}
                        : new byte[] {(byte) 0xE2, 0x28, (byte) 0xA1};
        StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
    }

    private static void closedZipFileSystem(int variant) throws IOException {
        try (TemporaryDirectory temporary = TemporaryDirectory.create("stackmate-zipfs-")) {
            Path archive = temporary.path().resolve("fixture-" + variant + ".zip");
            URI archiveUri = URI.create("jar:" + archive.toUri());
            Path retained;
            try (FileSystem fileSystem =
                    FileSystems.newFileSystem(archiveUri, Map.of("create", "true"))) {
                retained = fileSystem.getPath("/controlled-" + variant + ".txt");
                Files.writeString(retained, "controlled", StandardCharsets.UTF_8);
            }
            Files.readAttributes(retained, BasicFileAttributes.class);
        }
    }

    private static void unsupportedFileSystemProvider(int variant) throws IOException {
        URI uri = URI.create("nosuchfs" + variant + ":///controlled");
        try (FileSystem ignored = FileSystems.newFileSystem(uri, Map.of())) {
            // Provider resolution fails before a file system is returned.
        }
    }

    private static void byteBufferUnderflow(int variant) {
        ByteBuffer.allocate(1).put((byte) variant).flip().getLong();
    }

    private static void readOnlyByteBufferWrite(int variant) {
        ByteBuffer.allocate(1).asReadOnlyBuffer().put((byte) variant);
    }

    private static void absentResourceBundle(int variant) throws IOException {
        try (URLClassLoader loader =
                new URLClassLoader(
                        new URL[0], RealWorldJavaSpringFixtures.class.getClassLoader())) {
            try {
                ResourceBundle.getBundle(
                        "com.example.gatec.AbsentBundle" + variant,
                        Locale.ROOT,
                        loader,
                        ResourceBundle.Control.getControl(
                                ResourceBundle.Control.FORMAT_PROPERTIES));
            } finally {
                ResourceBundle.clearCache(loader);
            }
        }
    }

    private static void incompatibleFormatConversion(int variant) {
        String.format(Locale.ROOT, "%d", variant == 0 ? "alpha" : "beta");
    }

    private static void absentServiceProvider(int variant) throws Exception {
        try (TemporaryDirectory temporary = TemporaryDirectory.create("stackmate-service-")) {
            Path descriptor =
                    temporary
                            .path()
                            .resolve("META-INF/services")
                            .resolve(ControlledService.class.getName());
            Files.createDirectories(descriptor.getParent());
            Files.writeString(
                    descriptor,
                    "com.example.gatec.AbsentProvider" + variant + System.lineSeparator(),
                    StandardCharsets.UTF_8);

            URL[] urls = {temporary.path().toUri().toURL()};
            try (URLClassLoader loader =
                    new URLClassLoader(urls, RealWorldJavaSpringFixtures.class.getClassLoader())) {
                ServiceLoader.load(ControlledService.class, loader).iterator().next();
            }
        }
    }

    private static void duplicateBeanDefinition(int variant) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.setAllowBeanDefinitionOverriding(false);
        String beanName = "controlledBean" + variant;
        factory.registerBeanDefinition(beanName, new RootBeanDefinition(String.class));
        factory.registerBeanDefinition(beanName, new RootBeanDefinition(Integer.class));
    }

    private static void absentSpringConverter(int variant) {
        DefaultConversionService conversionService = new DefaultConversionService();
        conversionService.convert(new SourceValue("value-" + variant), TargetValue.class);
    }

    private static void wrongRequiredBeanType(int variant) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        String beanName = "stringBean" + variant;
        factory.registerSingleton(beanName, "controlled");
        factory.getBean(beanName, Integer.class);
    }

    private static void dereferenceNonFactoryBean(int variant) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        String beanName = "plainBean" + variant;
        factory.registerSingleton(beanName, "controlled");
        factory.getBean('&' + beanName);
    }

    private static Throwable inactiveRequestScopeFailure(int variant) {
        return failureInThread(
                "stackmate-real-world-request-scope-" + variant,
                0,
                () -> {
                    DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
                    factory.registerScope("request", new RequestScope());
                    RootBeanDefinition definition = new RootBeanDefinition(String.class);
                    definition.setScope("request");
                    factory.registerBeanDefinition("requestBean" + variant, definition);
                    factory.getBean("requestBean" + variant);
                });
    }

    private static void lookupDuringSingletonDestruction(int variant) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        String targetName = "destructionTarget" + variant;
        String callbackName = "destructionCallback" + variant;
        AtomicReference<Throwable> failure = new AtomicReference<>();

        factory.registerBeanDefinition(targetName, new RootBeanDefinition(String.class));
        factory.registerBeanDefinition(
                callbackName,
                new RootBeanDefinition(
                        DestructionLookup.class,
                        () -> new DestructionLookup(factory, targetName, failure)));
        factory.getBean(callbackName);
        factory.destroySingletons();

        Throwable captured = failure.get();
        if (captured == null) {
            throw new IllegalStateException("singleton destruction did not reject bean creation");
        }
        throwUnchecked(captured);
    }

    private static void malformedSpringBeanXml(int variant) {
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <beans xmlns="http://www.springframework.org/schema/beans">
                  <bean id="controlled%d" class="java.lang.String">
                </beans>
                """
                        .formatted(variant);
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
        reader.loadBeanDefinitions(
                new ByteArrayResource(
                        xml.getBytes(StandardCharsets.UTF_8), "controlled-malformed-" + variant));
    }

    private static void failingLifecycleStart(int variant) {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.getBeanFactory()
                    .registerSingleton("failingLifecycle" + variant, new FailingLifecycle(variant));
            context.refresh();
            context.start();
        }
    }

    private static void absentSpelProperty(int variant) {
        String property = variant == 0 ? "missingCode" : "missingStatus";
        new SpelExpressionParser()
                .parseExpression(property)
                .getValue(new SpelSource("controlled"));
    }

    private static void absentSpringMessage(int variant) {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.getMessage("controlled.message." + variant, null, Locale.ROOT);
    }

    private static void instantiateAbstractBean(int variant) {
        Class<?> fixture = variant == 0 ? AbstractFixtureZero.class : AbstractFixtureOne.class;
        BeanUtils.instantiateClass(fixture);
    }

    private static void inconsistentAliasMetadata(int variant) {
        Class<?> fixture = variant == 0 ? AliasFixtureZero.class : AliasFixtureOne.class;
        AnnotatedElementUtils.getMergedAnnotationAttributes(fixture, InconsistentAliases.class);
    }

    private static void conflictingScannedComponents(int variant) throws Exception {
        try (TemporaryDirectory temporary = TemporaryDirectory.create("stackmate-scan-")) {
            String suffix = variant == 0 ? "alpha" : "beta";
            String firstPackage = "com.example.gatec." + suffix + ".first";
            String secondPackage = "com.example.gatec." + suffix + ".second";
            Path firstSource = writeComponentSource(temporary.path(), firstPackage);
            Path secondSource = writeComponentSource(temporary.path(), secondPackage);
            compileComponents(temporary.path(), firstSource, secondSource);

            URL[] urls = {temporary.path().toUri().toURL()};
            try (URLClassLoader loader =
                            new URLClassLoader(
                                    urls, RealWorldJavaSpringFixtures.class.getClassLoader());
                    AnnotationConfigApplicationContext context =
                            new AnnotationConfigApplicationContext()) {
                context.setClassLoader(loader);
                context.scan(firstPackage, secondPackage);
            }
        }
    }

    private static Path writeComponentSource(Path root, String packageName) throws IOException {
        Path source =
                root.resolve(packageName.replace('.', '/')).resolve("DuplicateComponent.java");
        Files.createDirectories(source.getParent());
        Files.writeString(
                source,
                """
                package %s;

                import org.springframework.stereotype.Component;

                @Component
                class DuplicateComponent {}
                """
                        .formatted(packageName),
                StandardCharsets.UTF_8);
        return source;
    }

    private static void compileComponents(Path output, Path... sources) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("a JDK compiler is required for the scan fixture");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units =
                    fileManager.getJavaFileObjectsFromPaths(List.of(sources));
            List<String> options =
                    List.of(
                            "-classpath",
                            System.getProperty("java.class.path"),
                            "-d",
                            output.toString(),
                            "-proc:none");
            boolean compiled =
                    Boolean.TRUE.equals(
                            compiler.getTask(null, fileManager, diagnostics, options, null, units)
                                    .call());
            if (!compiled) {
                throw new IllegalStateException(
                        "controlled component compilation failed: " + diagnostics.getDiagnostics());
            }
        }
    }

    private static void voidBeanMethod(int variant) {
        Class<?> configuration =
                variant == 0 ? VoidBeanConfigurationZero.class : VoidBeanConfigurationOne.class;
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            context.register(configuration);
            context.refresh();
        }
    }

    private static Throwable capture(ThrowingAction action) {
        try {
            action.run();
        } catch (Throwable failure) {
            return failure;
        }
        throw new IllegalStateException("controlled fixture completed without a failure");
    }

    private static Throwable failureInThread(
            String name, long stackSize, ThrowingAction action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread =
                new Thread(
                        null,
                        () -> {
                            try {
                                action.run();
                            } catch (Throwable caught) {
                                failure.set(caught);
                            }
                        },
                        name,
                        stackSize,
                        false);
        thread.setDaemon(true);
        thread.start();
        try {
            thread.join(THREAD_TIMEOUT_MILLIS);
            if (thread.isAlive()) {
                thread.interrupt();
                thread.join(THREAD_TIMEOUT_MILLIS);
            }
        } catch (InterruptedException interrupted) {
            thread.interrupt();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for " + name, interrupted);
        }
        if (thread.isAlive()) {
            throw new IllegalStateException("controlled fixture thread did not terminate: " + name);
        }
        Throwable caught = failure.get();
        if (caught == null) {
            throw new IllegalStateException("controlled fixture thread did not fail: " + name);
        }
        return caught;
    }

    private static void throwUnchecked(Throwable failure) {
        RealWorldJavaSpringFixtures.<RuntimeException>throwAny(failure);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwAny(Throwable failure) throws T {
        throw (T) failure;
    }

    private interface ThrowingAction {
        void run() throws Exception;
    }

    private interface ControlledService {}

    private record SourceValue(String value) {}

    private record TargetValue(String value) {}

    private record SpelSource(String presentCode) {}

    private static final class DestructionLookup implements DisposableBean {

        private final DefaultListableBeanFactory factory;
        private final String targetName;
        private final AtomicReference<Throwable> failure;

        private DestructionLookup(
                DefaultListableBeanFactory factory,
                String targetName,
                AtomicReference<Throwable> failure) {
            this.factory = factory;
            this.targetName = targetName;
            this.failure = failure;
        }

        @Override
        public void destroy() {
            try {
                factory.getBean(targetName);
            } catch (Throwable caught) {
                failure.set(caught);
            }
        }
    }

    private static final class FailingLifecycle implements Lifecycle {

        private final int variant;

        private FailingLifecycle(int variant) {
            this.variant = variant;
        }

        @Override
        public void start() {
            throw new IllegalStateException("controlled lifecycle start failure " + variant);
        }

        @Override
        public void stop() {}

        @Override
        public boolean isRunning() {
            return false;
        }
    }

    private abstract static class AbstractFixtureZero {}

    private abstract static class AbstractFixtureOne {}

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface InconsistentAliases {

        @AliasFor("second")
        String first() default "first-default";

        @AliasFor("first")
        String second() default "second-default";
    }

    @InconsistentAliases
    private static final class AliasFixtureZero {}

    @InconsistentAliases(first = "controlled")
    private static final class AliasFixtureOne {}

    @Configuration
    private static class VoidBeanConfigurationZero {

        @Bean
        void invalidBean() {}
    }

    @Configuration
    private static class VoidBeanConfigurationOne {

        @Bean
        void anotherInvalidBean() {}
    }

    private record TemporaryDirectory(Path path) implements AutoCloseable {

        private static TemporaryDirectory create(String prefix) throws IOException {
            return new TemporaryDirectory(Files.createTempDirectory(prefix));
        }

        @Override
        public void close() throws IOException {
            try (Stream<Path> paths = Files.walk(path)) {
                for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(entry);
                }
            }
        }
    }
}
