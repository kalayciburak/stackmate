package io.github.kalayciburak.stackmate.benchmark.corpus.realworld;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Controlled real-world fixtures for the MVC and infrastructure corpus families. */
final class RealWorldMvcInfrastructureFixtures {

    private static final Set<String> SUPPORTED =
            Set.of(
                    "G53-R-MV-001",
                    "G53-R-MV-002",
                    "G53-R-MV-003",
                    "G53-R-MV-004",
                    "G53-R-MV-005",
                    "G53-R-MV-006",
                    "G53-R-MV-007",
                    "G53-R-MV-008",
                    "G53-R-MV-009",
                    "G53-R-MV-010",
                    "G53-R-MV-011",
                    "G53-R-MV-012",
                    "G53-R-MV-013",
                    "G53-R-MV-014",
                    "G53-R-MV-015",
                    "G53-R-MV-016",
                    "G53-R-IN-001",
                    "G53-R-IN-002",
                    "G53-R-IN-003",
                    "G53-R-IN-004",
                    "G53-R-IN-005",
                    "G53-R-IN-006",
                    "G53-R-IN-007",
                    "G53-R-IN-008",
                    "G53-R-IN-009",
                    "G53-R-IN-010",
                    "G53-R-IN-011",
                    "G53-R-IN-012",
                    "G53-R-IN-013",
                    "G53-R-IN-014");

    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(2);
    private static final long COORDINATION_TIMEOUT_MILLIS = 2_000;

    private RealWorldMvcInfrastructureFixtures() {}

    static boolean supports(String familyId) {
        return familyId != null && SUPPORTED.contains(familyId);
    }

    static Throwable execute(String familyId, int variant) {
        if (variant != 0 && variant != 1) {
            throw new IllegalArgumentException("variant must be 0 or 1: " + variant);
        }
        if (!supports(familyId)) {
            throw new IllegalArgumentException("Unsupported real-world fixture family: " + familyId);
        }

        try {
            Throwable failure = switch (familyId) {
                case "G53-R-MV-001" -> missingResource(variant);
                case "G53-R-MV-002" -> unacceptableResponse(variant);
                case "G53-R-MV-003" -> unreadableBody(variant, temporalMapper(), "{\"date\":\"2025-02-30\"}", TemporalBody.class);
                case "G53-R-MV-004" -> unreadableBody(variant, strictMapper(), "{\"name\":\"fixture\",\"unknown\":1}", NamedBody.class);
                case "G53-R-MV-005" -> unreadableBody(variant, defaultMapper(), "{\"kind\":\"missing-subtype\",\"value\":1}", PolymorphicBody.class);
                case "G53-R-MV-006" -> unreadableBody(variant, defaultMapper(), "{\"number\":2147483648}", IntegerBody.class);
                case "G53-R-MV-007" -> unreadableBody(variant, depthMapper(), "{\"value\":{\"value\":{\"value\":1}}}", NestedBody.class);
                case "G53-R-MV-008" -> unreadableBody(variant, duplicateMapper(), "{\"name\":\"first\",\"name\":\"second\"}", NamedBody.class);
                case "G53-R-MV-009" -> creatorFailure(variant);
                case "G53-R-MV-010" -> restClientNotFound(variant);
                case "G53-R-MV-011" -> restClientUnknownContentType(variant);
                case "G53-R-MV-012" -> responseStatusConflict(variant);
                case "G53-R-MV-013" -> asyncTimeout(variant);
                case "G53-R-MV-014" -> asyncWriteAfterCompletion(variant);
                case "G53-R-MV-015" -> missingRequestAttribute(variant);
                case "G53-R-MV-016" -> requiredSessionAttribute(variant);
                case "G53-R-IN-001" -> httpRequestTimeout(variant);
                case "G53-R-IN-002" -> socketWriteAfterClose(variant);
                case "G53-R-IN-003" -> tlsHostnameMismatch(variant);
                case "G53-R-IN-004" -> tlsProtocolMismatch(variant);
                case "G53-R-IN-005" -> truncatedHttpBody(variant);
                case "G53-R-IN-006" -> createExistingFile(variant);
                case "G53-R-IN-007" -> deleteNonemptyDirectory(variant);
                case "G53-R-IN-008" -> readClosedFileChannel(variant);
                case "G53-R-IN-009" -> overlappingFileLock(variant);
                case "G53-R-IN-010" -> brokenBarrier(variant);
                case "G53-R-IN-011" -> nonOwnerUnlock(variant);
                case "G53-R-IN-012" -> daoAccountFailure(variant, true);
                case "G53-R-IN-013" -> daoAccountFailure(variant, false);
                case "G53-R-IN-014" -> invalidGcmTag(variant);
                default -> throw new AssertionError("exhaustive family switch");
            };
            return verifyManifestTopology(familyId, failure);
        } catch (FixtureUnavailableException unavailable) {
            throw unavailable;
        } catch (Throwable setupFailure) {
            throw new IllegalStateException("Fixture " + familyId + " could not be executed", setupFailure);
        }
    }

    private static Throwable missingResource(int variant) throws Exception {
        Path directory = Files.createTempDirectory("stackmate-mvc-resource-");
        try {
            ResourceHttpRequestHandler resources = new ResourceHttpRequestHandler();
            resources.setLocations(
                    List.of(new FileSystemResource(directory + java.io.File.separator)));
            resources.afterPropertiesSet();
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new ResourceController(resources)).build();
            return resolved(mvc.perform(get("/fixture/resource/missing-" + value(variant))).andReturn(), "missing resource");
        } finally {
            Files.deleteIfExists(directory);
        }
    }

    private static Throwable unacceptableResponse(int variant) throws Exception {
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(new MvcController())
                        .setMessageConverters(jsonConverter(defaultMapper()))
                        .build();
        MvcResult result =
                mvc.perform(get("/fixture/json").param("variant", value(variant)).accept(MediaType.APPLICATION_XML))
                        .andReturn();
        return resolved(result, "unacceptable response media type");
    }

    private static Throwable unreadableBody(int variant, JsonMapper mapper, String json, Class<?> bodyType)
            throws Exception {
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(new BodyController())
                        .setMessageConverters(jsonConverter(mapper))
                        .build();
        MvcResult result =
                mvc.perform(
                                post(bodyPath(bodyType))
                                        .queryParam("variant", value(variant))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                        .andReturn();
        return resolved(result, "unreadable request body for " + bodyType.getSimpleName());
    }

    private static Throwable creatorFailure(int variant) throws Exception {
        Class<? extends Throwable> domainType = DomainExceptionLoader.load();
        CreatorBody.setFailure(domainType.getConstructor(String.class).newInstance("reserved-" + value(variant)));
        try {
            return unreadableBody(variant, defaultMapper(), "{\"value\":\"reserved\"}", CreatorBody.class);
        } finally {
            CreatorBody.clearFailure();
        }
    }

    private static Throwable restClientNotFound(int variant) throws Exception {
        return withHttpServer(
                variant,
                exchange -> {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                },
                uri -> capture(() -> RestClient.create(uri).get().retrieve().toBodilessEntity()));
    }

    private static Throwable restClientUnknownContentType(int variant) throws Exception {
        byte[] body = ("fixture-" + value(variant)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return withHttpServer(
                variant,
                exchange -> {
                    exchange.getResponseHeaders().set("Content-Type", "application/x-stackmate-fixture");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                },
                uri -> capture(() -> RestClient.create(uri).get().retrieve().body(UnknownBody.class)));
    }

    private static Throwable responseStatusConflict(int variant) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MvcController()).build();
        return resolved(
                mvc.perform(get("/fixture/conflict").queryParam("variant", value(variant))).andReturn(),
                "response status conflict");
    }

    private static Throwable asyncTimeout(int variant) throws Exception {
        Thread.interrupted();
        try {
            AsyncTaskExecutor taskExecutor = Runnable::run;
            MockMvc mvc =
                    MockMvcBuilders.standaloneSetup(new AsyncController(taskExecutor, variant))
                            .setAsyncRequestTimeout(COORDINATION_TIMEOUT_MILLIS)
                            .build();
            MvcResult initial = mvc.perform(get("/fixture/async-timeout")).andReturn();
            initial.getAsyncResult(COORDINATION_TIMEOUT_MILLIS);
            return capture(() -> mvc.perform(asyncDispatch(initial)).andReturn());
        } finally {
            Thread.interrupted();
        }
    }

    private static Throwable asyncWriteAfterCompletion(int variant) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fixture/async-write-" + value(variant));
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        StandardServletAsyncWebRequest asyncRequest = new StandardServletAsyncWebRequest(request, response);
        asyncRequest.startAsync();
        HttpServletResponse lifecycleResponse = asyncRequest.getResponse();
        Objects.requireNonNull(lifecycleResponse, "async lifecycle response").getOutputStream();
        request.getAsyncContext().complete();
        return capture(() -> lifecycleResponse.getOutputStream().write(variant & 0xff));
    }

    private static Throwable missingRequestAttribute(int variant) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MvcController()).build();
        return resolved(
                mvc.perform(get("/fixture/request-attribute").queryParam("variant", value(variant))).andReturn(),
                "missing request attribute");
    }

    private static Throwable requiredSessionAttribute(int variant) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MvcController()).build();
        return resolved(
                mvc.perform(
                                get("/fixture/path-variable")
                                        .queryParam("variant", value(variant)))
                        .andReturn(),
                "missing path variable");
    }

    private static Throwable httpRequestTimeout(int variant) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try {
            return withHttpServer(
                    variant,
                    exchange -> {
                        try {
                            release.await(COORDINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        } finally {
                            exchange.close();
                        }
                    },
                    uri ->
                            capture(
                                    () ->
                                            HttpClient.newBuilder()
                                                    .connectTimeout(CLIENT_TIMEOUT)
                                                    .build()
                                                    .send(
                                                            HttpRequest.newBuilder(uri)
                                                                    .timeout(Duration.ofMillis(75))
                                                                    .GET()
                                                                    .build(),
                                                            HttpResponse.BodyHandlers.discarding())));
        } finally {
            release.countDown();
        }
    }

    private static Throwable socketWriteAfterClose(int variant) throws Exception {
        try (ServerSocket server = loopbackServerSocket();
                ExecutorService executor = Executors.newSingleThreadExecutor()) {
            var accepted = executor.submit(server::accept);
            try (Socket client = new Socket()) {
                client.connect(server.getLocalSocketAddress(), (int) COORDINATION_TIMEOUT_MILLIS);
                try (Socket peer = accepted.get(COORDINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    OutputStream output = client.getOutputStream();
                    client.close();
                    return capture(() -> output.write(variant & 0xff));
                }
            }
        }
    }

    private static Throwable tlsHostnameMismatch(int variant) throws Exception {
        TlsMaterial material = TlsMaterial.create(variant);
        try (SSLServerSocket server = material.serverSocket()) {
            server.setSoTimeout((int) COORDINATION_TIMEOUT_MILLIS);
            try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                var serverTask = executor.submit(() -> acceptTls(server));
                URI uri = URI.create("https://localhost:" + server.getLocalPort() + "/fixture/" + value(variant));
                Throwable failure =
                        capture(
                                () ->
                                        HttpClient.newBuilder()
                                                .sslContext(material.trustingContext())
                                                .connectTimeout(CLIENT_TIMEOUT)
                                                .build()
                                                .send(
                                                        HttpRequest.newBuilder(uri)
                                                                .timeout(CLIENT_TIMEOUT)
                                                                .GET()
                                                                .build(),
                                                        HttpResponse.BodyHandlers.discarding()));
                serverTask.get(COORDINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                return failure;
            }
        }
    }

    private static Throwable tlsProtocolMismatch(int variant) throws Exception {
        TlsMaterial material = TlsMaterial.create(variant);
        try (SSLServerSocket server = material.serverSocket()) {
            server.setEnabledProtocols(new String[] {"TLSv1.2"});
            server.setSoTimeout((int) COORDINATION_TIMEOUT_MILLIS);
            try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                var serverTask = executor.submit(() -> acceptTls(server));
                try (SSLSocket client =
                        (SSLSocket)
                                material.trustingContext()
                                        .getSocketFactory()
                                        .createSocket(InetAddress.getLoopbackAddress(), server.getLocalPort())) {
                    client.setSoTimeout((int) COORDINATION_TIMEOUT_MILLIS);
                    client.setEnabledProtocols(new String[] {"TLSv1.3"});
                    Throwable failure = capture(client::startHandshake);
                    serverTask.get(COORDINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                    return failure;
                }
            }
        }
    }

    private static Throwable truncatedHttpBody(int variant) throws Exception {
        try (ServerSocket server = loopbackServerSocket();
                ExecutorService executor = Executors.newSingleThreadExecutor()) {
            var serverTask =
                    executor.submit(
                            () -> {
                                try (Socket socket = server.accept()) {
                                    socket.setSoTimeout((int) COORDINATION_TIMEOUT_MILLIS);
                                    readRequestHeaders(socket);
                                    byte[] partial = ("part-" + value(variant)).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                                    OutputStream output = socket.getOutputStream();
                                    output.write(
                                            ("HTTP/1.1 200 OK\r\nContent-Length: "
                                                            + (partial.length + 20)
                                                            + "\r\nConnection: close\r\n\r\n")
                                                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                                    output.write(partial);
                                    output.flush();
                                }
                                return null;
                            });
            URI uri = URI.create("http://localhost:" + server.getLocalPort() + "/fixture/" + value(variant));
            Throwable failure =
                    capture(
                            () ->
                                    HttpClient.newBuilder()
                                            .connectTimeout(CLIENT_TIMEOUT)
                                            .build()
                                            .send(
                                                    HttpRequest.newBuilder(uri).timeout(CLIENT_TIMEOUT).GET().build(),
                                                    HttpResponse.BodyHandlers.ofByteArray()));
            serverTask.get(COORDINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            return failure;
        }
    }

    private static Throwable createExistingFile(int variant) throws Exception {
        Path directory = Files.createTempDirectory("stackmate-existing-");
        Path file = directory.resolve("fixture-" + value(variant));
        try {
            Files.createFile(file);
            return capture(() -> Files.newOutputStream(file, StandardOpenOption.CREATE_NEW).close());
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static Throwable deleteNonemptyDirectory(int variant) throws Exception {
        Path directory = Files.createTempDirectory("stackmate-nonempty-");
        Path child = directory.resolve("child-" + value(variant));
        try {
            Files.writeString(child, "controlled");
            return capture(() -> Files.delete(directory));
        } finally {
            Files.deleteIfExists(child);
            Files.deleteIfExists(directory);
        }
    }

    private static Throwable readClosedFileChannel(int variant) throws Exception {
        Path file = Files.createTempFile("stackmate-closed-" + value(variant), ".bin");
        try {
            FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
            channel.close();
            return capture(() -> channel.read(ByteBuffer.allocate(1)));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static Throwable overlappingFileLock(int variant) throws Exception {
        Path file = Files.createTempFile("stackmate-lock-" + value(variant), ".bin");
        try (FileChannel first = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
                FileChannel second = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
                FileLock ignored = first.lock(0, 1, false)) {
            return capture(() -> second.lock(0, 1, false));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static Throwable brokenBarrier(int variant) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> barrier.await(25 + Math.floorMod(variant, 10), TimeUnit.MILLISECONDS));
            capture(() -> first.get(COORDINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            return capture(barrier::await);
        }
    }

    private static Throwable nonOwnerUnlock(int variant) throws Exception {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            var owner =
                    executor.submit(
                            () -> {
                                lock.lock();
                                try {
                                    locked.countDown();
                                    release.await(COORDINATION_TIMEOUT_MILLIS + Math.floorMod(variant, 10), TimeUnit.MILLISECONDS);
                                } finally {
                                    lock.unlock();
                                }
                                return null;
                            });
            if (!locked.await(COORDINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("lock owner did not start");
            }
            Throwable failure = capture(lock::unlock);
            release.countDown();
            owner.get(COORDINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            return failure;
        } finally {
            release.countDown();
        }
    }

    private static Throwable daoAccountFailure(int variant, boolean locked) throws Exception {
        try {
            ClassLoader loader = RealWorldMvcInfrastructureFixtures.class.getClassLoader();
            Class<?> userClass = Class.forName("org.springframework.security.core.userdetails.User", true, loader);
            Class<?> userDetailsClass = Class.forName("org.springframework.security.core.userdetails.UserDetails", true, loader);
            Class<?> userDetailsServiceClass = Class.forName("org.springframework.security.core.userdetails.UserDetailsService", true, loader);
            Class<?> managerClass = Class.forName("org.springframework.security.provisioning.InMemoryUserDetailsManager", true, loader);
            Class<?> providerClass = Class.forName("org.springframework.security.authentication.dao.DaoAuthenticationProvider", true, loader);
            Class<?> encoderClass = Class.forName("org.springframework.security.crypto.password.NoOpPasswordEncoder", true, loader);
            Class<?> tokenClass = Class.forName("org.springframework.security.authentication.UsernamePasswordAuthenticationToken", true, loader);

            String username = (locked ? "locked-" : "disabled-") + value(variant);
            Object builder = userClass.getMethod("withUsername", String.class).invoke(null, username);
            builder = builder.getClass().getMethod("password", String.class).invoke(builder, "controlled-password");
            builder = builder.getClass().getMethod("roles", String[].class).invoke(builder, (Object) new String[] {"USER"});
            String accountMethod = locked ? "accountLocked" : "disabled";
            builder = builder.getClass().getMethod(accountMethod, boolean.class).invoke(builder, true);
            Object user = builder.getClass().getMethod("build").invoke(builder);

            Object users = Array.newInstance(userDetailsClass, 1);
            Array.set(users, 0, user);
            Object manager = managerClass.getConstructor(users.getClass()).newInstance(users);
            Object provider = providerClass.getConstructor(userDetailsServiceClass).newInstance(manager);
            Object encoder = encoderClass.getMethod("getInstance").invoke(null);
            Method setEncoder = Arrays.stream(providerClass.getMethods())
                    .filter(method -> method.getName().equals("setPasswordEncoder"))
                    .findFirst()
                    .orElseThrow();
            setEncoder.invoke(provider, encoder);
            Object token = tokenClass.getMethod("unauthenticated", Object.class, Object.class)
                    .invoke(null, username, "controlled-password");
            return captureReflective(() -> providerClass.getMethod("authenticate", Class.forName("org.springframework.security.core.Authentication", true, loader)).invoke(provider, token));
        } catch (ClassNotFoundException missingDependency) {
            throw new FixtureUnavailableException(
                    "G53-R-IN-012/013 require spring-security-core on the benchmark test classpath; the current dependencies do not provide it",
                    missingDependency);
        }
    }

    private static Throwable invalidGcmTag(int variant) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128, seededRandom(variant));
        SecretKey key = generator.generateKey();
        byte[] nonce = new byte[12];
        seededRandom(variant ^ 0x53_14).nextBytes(nonce);
        byte[] plain = ("controlled-" + value(variant)).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding");
        encrypt.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        byte[] cipherText = encrypt.doFinal(plain);
        cipherText[cipherText.length - 1] ^= 0x01;

        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
        decrypt.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        return capture(() -> decrypt.doFinal(cipherText));
    }

    private static JsonMapper defaultMapper() {
        return JsonMapper.builder().build();
    }

    private static JsonMapper temporalMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    private static JsonMapper strictMapper() {
        return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    private static JsonMapper depthMapper() {
        JsonFactory factory =
                JsonFactory.builder()
                        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(2).build())
                        .build();
        return JsonMapper.builder(factory).build();
    }

    private static JsonMapper duplicateMapper() {
        JsonFactory factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        return JsonMapper.builder(factory).build();
    }

    private static HttpMessageConverter<?> jsonConverter(JsonMapper mapper) {
        return new JacksonJsonHttpMessageConverter(mapper);
    }

    private static String bodyPath(Class<?> bodyType) {
        if (bodyType == TemporalBody.class) {
            return "/fixture/body/temporal";
        }
        if (bodyType == NamedBody.class) {
            return "/fixture/body/named";
        }
        if (bodyType == PolymorphicBody.class) {
            return "/fixture/body/polymorphic";
        }
        if (bodyType == IntegerBody.class) {
            return "/fixture/body/integer";
        }
        if (bodyType == NestedBody.class) {
            return "/fixture/body/nested";
        }
        if (bodyType == CreatorBody.class) {
            return "/fixture/body/creator";
        }
        throw new IllegalArgumentException("unsupported body type: " + bodyType.getName());
    }

    private static Throwable resolved(MvcResult result, String operation) {
        Throwable failure = result.getResolvedException();
        if (failure == null) {
            throw new IllegalStateException(operation + " completed without a resolved exception");
        }
        return failure;
    }

    private static Throwable verifyManifestTopology(String familyId, Throwable failure) {
        List<String> expected =
                switch (familyId) {
                    case "G53-R-MV-001" -> List.of("org.springframework.web.servlet.resource.NoResourceFoundException");
                    case "G53-R-MV-002" -> List.of("org.springframework.web.HttpMediaTypeNotAcceptableException");
                    case "G53-R-MV-003" -> List.of(
                            "org.springframework.http.converter.HttpMessageNotReadableException",
                            "tools.jackson.databind.exc.InvalidFormatException",
                            "java.time.format.DateTimeParseException",
                            "java.time.DateTimeException");
                    case "G53-R-MV-004" -> List.of(
                            "org.springframework.http.converter.HttpMessageNotReadableException",
                            "tools.jackson.databind.exc.UnrecognizedPropertyException");
                    case "G53-R-MV-005" -> List.of(
                            "org.springframework.http.converter.HttpMessageNotReadableException",
                            "tools.jackson.databind.exc.InvalidTypeIdException");
                    case "G53-R-MV-006" -> List.of(
                            "org.springframework.http.converter.HttpMessageNotReadableException",
                            "tools.jackson.core.exc.InputCoercionException");
                    case "G53-R-MV-007" -> List.of(
                            "org.springframework.http.converter.HttpMessageNotReadableException",
                            "tools.jackson.core.exc.StreamConstraintsException");
                    case "G53-R-MV-008" -> List.of(
                            "org.springframework.http.converter.HttpMessageNotReadableException",
                            "tools.jackson.core.exc.StreamReadException");
                    case "G53-R-MV-009" -> List.of(
                            "org.springframework.http.converter.HttpMessageNotReadableException",
                            "tools.jackson.databind.exc.ValueInstantiationException",
                            "com.example.gatec.DomainInputException");
                    case "G53-R-MV-010" -> List.of("org.springframework.web.client.HttpClientErrorException$NotFound");
                    case "G53-R-MV-011" -> List.of("org.springframework.web.client.UnknownContentTypeException");
                    case "G53-R-MV-012" -> List.of("org.springframework.web.server.ResponseStatusException");
                    case "G53-R-MV-013" -> List.of(
                            "jakarta.servlet.ServletException", "java.lang.InterruptedException");
                    case "G53-R-MV-014" -> List.of("org.springframework.web.context.request.async.AsyncRequestNotUsableException");
                    case "G53-R-MV-015" -> List.of("org.springframework.web.bind.MissingRequestAttributeException");
                    case "G53-R-IN-001" -> List.of("java.net.http.HttpTimeoutException");
                    case "G53-R-IN-002" -> List.of("java.net.SocketException");
                    case "G53-R-IN-003" -> List.of(
                            "javax.net.ssl.SSLHandshakeException",
                            "javax.net.ssl.SSLHandshakeException",
                            "java.security.cert.CertificateException");
                    case "G53-R-IN-004" -> List.of("javax.net.ssl.SSLHandshakeException");
                    case "G53-R-IN-005" -> List.of(
                            "java.io.IOException", "java.io.IOException", "java.io.EOFException");
                    case "G53-R-IN-006" -> List.of("java.nio.file.FileAlreadyExistsException");
                    case "G53-R-IN-007" -> List.of("java.nio.file.DirectoryNotEmptyException");
                    case "G53-R-IN-008" -> List.of("java.nio.channels.ClosedChannelException");
                    case "G53-R-IN-009" -> List.of("java.nio.channels.OverlappingFileLockException");
                    case "G53-R-IN-010" -> List.of("java.util.concurrent.BrokenBarrierException");
                    case "G53-R-IN-011" -> List.of("java.lang.IllegalMonitorStateException");
                    case "G53-R-IN-012" -> List.of("org.springframework.security.authentication.LockedException");
                    case "G53-R-IN-013" -> List.of("org.springframework.security.authentication.DisabledException");
                    case "G53-R-IN-014" -> List.of("javax.crypto.AEADBadTagException");
                    case "G53-R-MV-016" -> List.of(
                            "org.springframework.web.bind.MissingPathVariableException");
                    default -> throw new AssertionError("validated family was not mapped: " + familyId);
                };

        Throwable current = failure;
        for (String expectedType : expected) {
            if (current == null || !current.getClass().getName().equals(expectedType)) {
                String actualType = current == null ? "<missing cause>" : current.getClass().getName();
                throw new FixtureUnavailableException(
                        familyId
                                + " produced "
                                + actualType
                                + " where the manifest requires "
                                + expectedType,
                        failure);
            }
            current = current.getCause();
        }
        return failure;
    }

    private static Throwable capture(ThrowingAction action) throws Exception {
        try {
            action.run();
        } catch (Throwable failure) {
            return failure;
        }
        throw new IllegalStateException("controlled operation completed without an exception");
    }

    private static Throwable captureReflective(ThrowingAction action) throws Exception {
        Throwable failure = capture(action);
        return failure instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause()
                : failure;
    }

    private static Throwable withHttpServer(
            int variant, HttpHandlerAction handler, UriFailureAction operation) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            server.setExecutor(executor);
            server.createContext("/fixture/" + value(variant), exchange -> handler.handle(exchange));
            server.start();
            URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/fixture/" + value(variant));
            return operation.run(uri);
        } finally {
            server.stop(0);
        }
    }

    private static ServerSocket loopbackServerSocket() throws IOException {
        return new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    }

    private static void readRequestHeaders(Socket socket) throws IOException {
        int matched = 0;
        byte[] terminator = {'\r', '\n', '\r', '\n'};
        while (matched < terminator.length) {
            int next = socket.getInputStream().read();
            if (next == -1) {
                throw new IOException("client closed before sending request headers");
            }
            matched = next == terminator[matched] ? matched + 1 : (next == terminator[0] ? 1 : 0);
        }
    }

    private static Void acceptTls(SSLServerSocket server) {
        try (SSLSocket socket = (SSLSocket) server.accept()) {
            socket.setSoTimeout((int) COORDINATION_TIMEOUT_MILLIS);
            socket.startHandshake();
        } catch (IOException expectedPeerFailure) {
            // The client-side exception is the fixture result; the peer must still terminate.
        }
        return null;
    }

    private static SecureRandom seededRandom(int variant) throws Exception {
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        random.setSeed(ByteBuffer.allocate(8).putInt(0x53544143).putInt(variant).array());
        return random;
    }

    private static String value(int variant) {
        return Integer.toUnsignedString(variant);
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface UriFailureAction {
        Throwable run(URI uri) throws Exception;
    }

    @FunctionalInterface
    private interface HttpHandlerAction {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
    }

    private static final class FixtureUnavailableException extends IllegalStateException {
        private FixtureUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @RestController
    private static final class ResourceController {
        private final ResourceHttpRequestHandler resources;

        private ResourceController(ResourceHttpRequestHandler resources) {
            this.resources = resources;
        }

        @GetMapping("/fixture/resource/{*path}")
        void resource(HttpServletRequest request, HttpServletResponse response) throws Exception {
            String path = request.getRequestURI().substring("/fixture/resource/".length());
            request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, path);
            resources.handleRequest(request, response);
        }
    }

    @RestController
    private static final class MvcController {
        @GetMapping("/fixture/json")
        NamedBody json() {
            return new NamedBody("controlled");
        }

        @GetMapping("/fixture/conflict")
        void conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "controlled conflict");
        }

        @GetMapping("/fixture/request-attribute")
        String requestAttribute(@RequestAttribute("controlledAttribute") String attribute) {
            return attribute;
        }

        @GetMapping("/fixture/path-variable")
        String pathVariable(@PathVariable("id") String id) {
            return id;
        }
    }

    @RestController
    private static final class BodyController {
        @PostMapping("/fixture/body/temporal")
        String temporal(@RequestBody TemporalBody body) {
            return body.date().toString();
        }

        @PostMapping("/fixture/body/named")
        String named(@RequestBody NamedBody body) {
            return body.name();
        }

        @PostMapping("/fixture/body/polymorphic")
        String polymorphic(@RequestBody PolymorphicBody body) {
            return body.toString();
        }

        @PostMapping("/fixture/body/integer")
        String integer(@RequestBody IntegerBody body) {
            return Integer.toString(body.number());
        }

        @PostMapping("/fixture/body/nested")
        String nested(@RequestBody NestedBody body) {
            return body.toString();
        }

        @PostMapping("/fixture/body/creator")
        String creator(@RequestBody CreatorBody body) {
            return body.value;
        }
    }

    @RestController
    private static final class AsyncController {
        private final AsyncTaskExecutor executor;
        private final int variant;

        private AsyncController(AsyncTaskExecutor executor, int variant) {
            this.executor = executor;
            this.variant = variant;
        }

        @GetMapping("/fixture/async-timeout")
        WebAsyncTask<String> timeout() {
            return new WebAsyncTask<>(
                    25L,
                    executor,
                    () -> {
                        throw new InterruptedException(
                                "controlled async interruption " + value(variant));
                    });
        }
    }

    private record NamedBody(String name) {}

    private record UnknownBody(String value) {}

    private record IntegerBody(int number) {}

    private record TemporalBody(java.time.LocalDate date) {}

    private record NestedBody(Object value) {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes(@JsonSubTypes.Type(value = KnownPolymorphicBody.class, name = "known"))
    private interface PolymorphicBody {}

    private record KnownPolymorphicBody(int value) implements PolymorphicBody {}

    private static final class CreatorBody {
        private static final ThreadLocal<Throwable> FAILURE = new ThreadLocal<>();
        private final String value;

        @JsonCreator
        private CreatorBody(@JsonProperty("value") String value) {
            Throwable failure = FAILURE.get();
            if ("reserved".equals(value) && failure != null) {
                CreatorBody.<RuntimeException>throwUnchecked(failure);
            }
            this.value = value;
        }

        private static void setFailure(Throwable failure) {
            FAILURE.set(failure);
        }

        private static void clearFailure() {
            FAILURE.remove();
        }

        @SuppressWarnings("unchecked")
        private static <T extends Throwable> void throwUnchecked(Throwable failure) throws T {
            throw (T) failure;
        }
    }

    private static final class DomainExceptionLoader {
        private static volatile Class<? extends Throwable> loaded;

        private DomainExceptionLoader() {}

        private static Class<? extends Throwable> load() throws Exception {
            Class<? extends Throwable> cached = loaded;
            if (cached != null) {
                return cached;
            }
            synchronized (DomainExceptionLoader.class) {
                if (loaded == null) {
                    loaded = compile();
                }
                return loaded;
            }
        }

        @SuppressWarnings("unchecked")
        private static Class<? extends Throwable> compile() throws Exception {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new FixtureUnavailableException(
                        "G53-R-MV-009 requires a full JDK so its manifest domain exception can be created without adding another source file",
                        new ClassNotFoundException("javax.tools.JavaCompiler"));
            }
            Path directory = Files.createTempDirectory("stackmate-domain-exception-");
            Path packageDirectory = directory.resolve("com/example/gatec");
            Path source = packageDirectory.resolve("DomainInputException.java");
            try {
                Files.createDirectories(packageDirectory);
                Files.writeString(
                        source,
                        "package com.example.gatec; public final class DomainInputException extends RuntimeException {"
                                + " public DomainInputException(String message) { super(message); } }");
                try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
                    var units = files.getJavaFileObjects(source.toFile());
                    Boolean success = compiler.getTask(null, files, null, List.of("-d", directory.toString()), null, units).call();
                    if (!Boolean.TRUE.equals(success)) {
                        throw new FixtureUnavailableException(
                                "G53-R-MV-009 could not compile its controlled manifest domain exception",
                                new IllegalStateException("JDK compiler returned false"));
                    }
                }
                byte[] bytecode = Files.readAllBytes(packageDirectory.resolve("DomainInputException.class"));
                Class<?> type = new ByteArrayClassLoader().define("com.example.gatec.DomainInputException", bytecode);
                return (Class<? extends Throwable>) type.asSubclass(Throwable.class);
            } finally {
                deleteTree(directory);
            }
        }
    }

    private static final class ByteArrayClassLoader extends ClassLoader {
        private ByteArrayClassLoader() {
            super(RealWorldMvcInfrastructureFixtures.class.getClassLoader());
        }

        private Class<?> define(String name, byte[] bytecode) {
            return defineClass(name, bytecode, 0, bytecode.length);
        }
    }

    private record TlsMaterial(KeyPair keyPair, X509Certificate certificate, SSLContext serverContext, SSLContext trustContext) {
        private static TlsMaterial create(int variant) throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, seededRandom(variant ^ 0x53_03));
            KeyPair keyPair = generator.generateKeyPair();
            X509Certificate certificate = SelfSignedCertificate.create(keyPair, variant);

            char[] password = ("fixture-" + value(variant)).toCharArray();
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, password);
            keyStore.setKeyEntry("fixture", keyPair.getPrivate(), password, new java.security.cert.Certificate[] {certificate});
            KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keyStore, password);
            SSLContext serverContext = SSLContext.getInstance("TLS");
            serverContext.init(keyManagers.getKeyManagers(), null, seededRandom(variant ^ 0x53_04));

            X509TrustManager trustFixture = new FixtureTrustManager(certificate);
            SSLContext trustContext = SSLContext.getInstance("TLS");
            trustContext.init(null, new TrustManager[] {trustFixture}, seededRandom(variant ^ 0x53_05));
            return new TlsMaterial(keyPair, certificate, serverContext, trustContext);
        }

        private SSLServerSocket serverSocket() throws IOException {
            return (SSLServerSocket)
                    serverContext.getServerSocketFactory().createServerSocket(0, 1, InetAddress.getLoopbackAddress());
        }

        private SSLContext trustingContext() {
            return trustContext;
        }
    }

    private static final class FixtureTrustManager implements X509TrustManager {
        private final X509Certificate certificate;

        private FixtureTrustManager(X509Certificate certificate) {
            this.certificate = certificate;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            check(chain);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            check(chain);
        }

        private void check(X509Certificate[] chain) throws java.security.cert.CertificateException {
            if (chain == null || chain.length != 1 || !certificate.equals(chain[0])) {
                throw new java.security.cert.CertificateException("untrusted controlled fixture certificate");
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[] {certificate};
        }
    }

    /** Minimal DER writer for a generated, test-only RSA certificate with a non-localhost SAN. */
    private static final class SelfSignedCertificate {
        private SelfSignedCertificate() {}

        private static X509Certificate create(KeyPair pair, int variant) throws Exception {
            byte[] signatureAlgorithm = sequence(oid("1.2.840.113549.1.1.11"), der(0x05, new byte[0]));
            byte[] commonName = sequence(set(sequence(oid("2.5.4.3"), der(0x0c, "fixture.invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
            byte[] validity = sequence(der(0x18, ascii("20200101000000Z")), der(0x18, ascii("20400101000000Z")));
            byte[] sanValue = sequence(der(0x82, ascii("fixture.invalid")));
            byte[] extensions = der(0xa3, sequence(sequence(oid("2.5.29.17"), der(0x04, sanValue))));
            byte[] tbs =
                    sequence(
                            der(0xa0, integer(2)),
                            integer(Math.floorMod(variant, Integer.MAX_VALUE) + 1),
                            signatureAlgorithm,
                            commonName,
                            validity,
                            commonName,
                            pair.getPublic().getEncoded(),
                            extensions);
            java.security.Signature signer = java.security.Signature.getInstance("SHA256withRSA");
            signer.initSign(pair.getPrivate(), seededRandom(variant ^ 0x53_33));
            signer.update(tbs);
            byte[] encoded = sequence(tbs, signatureAlgorithm, bitString(signer.sign()));
            return (X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encoded));
        }

        private static byte[] sequence(byte[]... values) {
            return der(0x30, join(values));
        }

        private static byte[] set(byte[] value) {
            return der(0x31, value);
        }

        private static byte[] integer(int value) {
            byte[] bytes = ByteBuffer.allocate(4).putInt(value).array();
            int first = 0;
            while (first < bytes.length - 1 && bytes[first] == 0 && (bytes[first + 1] & 0x80) == 0) {
                first++;
            }
            return der(0x02, Arrays.copyOfRange(bytes, first, bytes.length));
        }

        private static byte[] oid(String dotted) {
            String[] parts = dotted.split("\\.");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]));
            for (int index = 2; index < parts.length; index++) {
                long component = Long.parseLong(parts[index]);
                byte[] encoded = new byte[10];
                int position = encoded.length;
                encoded[--position] = (byte) (component & 0x7f);
                while ((component >>>= 7) != 0) {
                    encoded[--position] = (byte) (0x80 | (component & 0x7f));
                }
                output.writeBytes(Arrays.copyOfRange(encoded, position, encoded.length));
            }
            return der(0x06, output.toByteArray());
        }

        private static byte[] bitString(byte[] value) {
            byte[] withUnusedBitCount = new byte[value.length + 1];
            System.arraycopy(value, 0, withUnusedBitCount, 1, value.length);
            return der(0x03, withUnusedBitCount);
        }

        private static byte[] der(int tag, byte[] value) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(tag);
            if (value.length < 128) {
                output.write(value.length);
            } else {
                int lengthBytes = 4 - Integer.numberOfLeadingZeros(value.length) / 8;
                output.write(0x80 | lengthBytes);
                for (int shift = (lengthBytes - 1) * 8; shift >= 0; shift -= 8) {
                    output.write(value.length >>> shift);
                }
            }
            output.writeBytes(value);
            return output.toByteArray();
        }

        private static byte[] join(byte[]... values) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (byte[] value : values) {
                output.writeBytes(value);
            }
            return output.toByteArray();
        }

        private static byte[] ascii(String value) {
            return value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
