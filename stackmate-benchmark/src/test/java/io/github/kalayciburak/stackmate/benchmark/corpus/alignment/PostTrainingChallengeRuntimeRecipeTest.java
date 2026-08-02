package io.github.kalayciburak.stackmate.benchmark.corpus.alignment;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.AccessControlException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class PostTrainingChallengeRuntimeRecipeTest {

    private static final AtomicLong DATABASE_SEQUENCE = new AtomicLong();

    static java.util.stream.Stream<PostTrainingChallengeCase> realCases() {
        return PostTrainingChallengeManifest.load().cases().stream()
                .filter(PostTrainingChallengeCase::realExecution);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("realCases")
    void reproducesEveryCaseLabeledAsRealExecution(PostTrainingChallengeCase benchmarkCase)
            throws Exception {
        Throwable failure = execute(benchmarkCase.recipe());
        String rootType = ThrowableSnapshotter.defaults().capture(failure).root().type();

        assertThat(rootType).isEqualTo(benchmarkCase.expectedRootType());
    }

    Throwable execute(String recipe) throws Exception {
        if (recipe.startsWith("challenge.java.")) {
            return javaFailure(recipe);
        }
        if (recipe.startsWith("challenge.spring.")) {
            return springFailure(recipe);
        }
        if (recipe.startsWith("challenge.mvc.")) {
            return mvcFailure(recipe);
        }
        if (recipe.startsWith("challenge.h2.")) {
            return h2Failure(recipe);
        }
        if (recipe.startsWith("challenge.security.")) {
            return securityFailure(recipe);
        }
        if (recipe.startsWith("challenge.network.")) {
            return networkFailure(recipe);
        }
        if (recipe.startsWith("challenge.concurrency.")) {
            return concurrencyFailure(recipe);
        }
        throw new IllegalArgumentException("unsupported challenge recipe: " + recipe);
    }

    private Throwable javaFailure(String recipe) throws Exception {
        return capture(
                () -> {
                    switch (recipe) {
                        case "challenge.java.completion-duration" ->
                                CompletableFuture.supplyAsync(() -> Integer.parseInt("19ms"))
                                        .join();
                        case "challenge.java.execution-port" -> {
                            try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                                executor.submit(() -> Integer.parseInt("port-81")).get();
                            }
                        }
                        case "challenge.java.numeric-retry" -> Integer.parseInt("retry-seven");
                        default -> throw new IllegalArgumentException("unknown Java challenge");
                    }
                });
    }

    private Throwable springFailure(String recipe) throws Exception {
        return capture(
                () -> {
                    DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
                    switch (recipe) {
                        case "challenge.spring.missing-clock" ->
                                factory.getBean(java.time.Clock.class);
                        case "challenge.spring.nonunique-codec" -> {
                            factory.registerSingleton("jsonCodec", new Codec("json"));
                            factory.registerSingleton("binaryCodec", new Codec("binary"));
                            factory.getBean(Codec.class);
                        }
                        case "challenge.spring.conversion-window" ->
                                DefaultConversionService.getSharedInstance()
                                        .convert("window-45", Integer.class);
                        default -> throw new IllegalArgumentException("unknown Spring challenge");
                    }
                });
    }

    private Throwable mvcFailure(String recipe) throws Exception {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(new ChallengeController())
                        .setValidator(validator)
                        .build();
        MvcResult result =
                switch (recipe) {
                    case "challenge.mvc.malformed-list" ->
                            mvc.perform(
                                            MockMvcRequestBuilders.post("/challenge/lines")
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .content("[{\"name\":\"box\"},"))
                                    .andReturn();
                    case "challenge.mvc.invalid-state" ->
                            mvc.perform(
                                            MockMvcRequestBuilders.get("/challenge/state")
                                                    .param("state", "PAUSED_FOREVER"))
                                    .andReturn();
                    case "challenge.mvc.validation-line" ->
                            mvc.perform(
                                            MockMvcRequestBuilders.post("/challenge/line")
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .content("{\"name\":\"\",\"quantity\":0}"))
                                    .andReturn();
                    case "challenge.mvc.missing-cursor" ->
                            mvc.perform(MockMvcRequestBuilders.get("/challenge/page")).andReturn();
                    default -> throw new IllegalArgumentException("unknown MVC challenge");
                };
        validator.close();
        return java.util.Objects.requireNonNull(result.getResolvedException(), recipe);
    }

    private Throwable h2Failure(String recipe) throws Exception {
        String database = "challenge_" + DATABASE_SEQUENCE.incrementAndGet();
        try (Connection connection =
                DriverManager.getConnection("jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1")) {
            return capture(() -> executeH2(connection, recipe));
        }
    }

    private void executeH2(Connection connection, String recipe) throws Exception {
        try (var statement = connection.createStatement()) {
            switch (recipe) {
                case "challenge.h2.unique-sku" -> {
                    statement.execute(
                            "create table challenge_product(id int primary key, sku varchar unique)");
                    statement.execute("insert into challenge_product values(1, 'SKU-X')");
                    statement.execute("insert into challenge_product values(2, 'SKU-X')");
                }
                case "challenge.h2.not-null-tenant" -> {
                    statement.execute(
                            "create table challenge_tenant(tenant_code varchar not null)");
                    statement.execute("insert into challenge_tenant values(null)");
                }
                case "challenge.h2.foreign-key-warehouse" -> {
                    statement.execute("create table challenge_warehouse(id int primary key)");
                    statement.execute(
                            "create table challenge_stock(warehouse_id int references challenge_warehouse(id))");
                    statement.execute("insert into challenge_stock values(404)");
                }
                case "challenge.h2.missing-column-route" -> {
                    statement.execute("create table challenge_route(id int)");
                    statement.executeQuery("select route_zone from challenge_route");
                }
                case "challenge.h2.syntax-alias" ->
                        statement.execute("select id as from challenge_route");
                default -> throw new IllegalArgumentException("unknown H2 challenge");
            }
        }
    }

    @SuppressWarnings("removal")
    private Throwable securityFailure(String recipe) throws Exception {
        return capture(
                () -> {
                    switch (recipe) {
                        case "challenge.security.permission-denied" ->
                                throw new AccessControlException("challenge permission was denied");
                        case "challenge.security.message-denied" ->
                                throw new SecurityException(
                                        "access is denied for the challenge operation");
                        default -> throw new IllegalArgumentException("unknown security challenge");
                    }
                });
    }

    private Throwable networkFailure(String recipe) throws Exception {
        if (!recipe.equals("challenge.network.socket-local")) {
            throw new IllegalArgumentException("unknown network challenge");
        }
        int port;
        try (ServerSocket server = new ServerSocket(0)) {
            port = server.getLocalPort();
        }
        int closedPort = port;
        return capture(
                () ->
                        new Socket()
                                .connect(
                                        new InetSocketAddress(
                                                InetAddress.getLoopbackAddress(), closedPort),
                                        500));
    }

    private Throwable concurrencyFailure(String recipe) throws Exception {
        return capture(
                () -> {
                    switch (recipe) {
                        case "challenge.concurrency.future-timeout" -> {
                            try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                                Future<?> future =
                                        executor.submit(
                                                () -> {
                                                    try {
                                                        Thread.sleep(500);
                                                    } catch (InterruptedException interrupted) {
                                                        Thread.currentThread().interrupt();
                                                    }
                                                });
                                future.get(1, TimeUnit.MILLISECONDS);
                            }
                        }
                        case "challenge.concurrency.executor-reject" -> {
                            ThreadPoolExecutor executor =
                                    (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
                            executor.shutdown();
                            executor.execute(() -> {});
                        }
                        default ->
                                throw new IllegalArgumentException("unknown concurrency challenge");
                    }
                });
    }

    private Throwable capture(ThrowingAction action) throws Exception {
        try {
            action.run();
        } catch (Throwable failure) {
            return failure;
        }
        throw new IllegalStateException("challenge recipe did not fail");
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private record Codec(String name) {}

    private enum State {
        READY,
        STOPPED
    }

    private record LineRequest(@NotBlank String name, @Min(1) int quantity) {}

    @RestController
    private static final class ChallengeController {

        @PostMapping("/challenge/lines")
        List<LineRequest> lines(@RequestBody List<LineRequest> lines) {
            return lines;
        }

        @GetMapping("/challenge/state")
        State state(@RequestParam State state) {
            return state;
        }

        @PostMapping("/challenge/line")
        LineRequest line(@Valid @RequestBody LineRequest line) {
            return line;
        }

        @GetMapping("/challenge/page")
        String page(@RequestParam String cursor) {
            return cursor;
        }
    }
}
