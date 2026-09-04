package io.github.kalayciburak.stackmate.benchmark.corpus.freshfinal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.pattern.PathPatternParser;

final class FreshFinalV2RuntimeFixtures {

    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(3);
    private static final long COORDINATION_TIMEOUT_MILLIS = 5_000;
    private static final Set<String> SUPPORTED =
            Set.of(
                    "F543-R-JV-001",
                    "F543-R-JV-002",
                    "F543-R-JV-003",
                    "F543-R-JV-004",
                    "F543-R-JV-005",
                    "F543-R-JV-006",
                    "F543-R-JV-007",
                    "F543-R-JV-008",
                    "F543-R-SP-001",
                    "F543-R-SP-002",
                    "F543-R-SP-003",
                    "F543-R-MV-001",
                    "F543-R-MV-002",
                    "F543-R-MV-003",
                    "F543-R-JS-001",
                    "F543-R-JS-002",
                    "F543-R-NET-001",
                    "F543-R-NET-002");

    private FreshFinalV2RuntimeFixtures() {}

    static boolean supports(String familyId) {
        return familyId != null && SUPPORTED.contains(familyId);
    }

    static Throwable execute(String familyId, int variant) {
        if (variant != 0 && variant != 1) {
            throw new IllegalArgumentException("variant must be 0 or 1: " + variant);
        }
        if (!supports(familyId)) {
            throw new IllegalArgumentException("unsupported fresh-final fixture: " + familyId);
        }

        return switch (familyId) {
            case "F543-R-MV-001" -> missingRequiredHeader(variant);
            case "F543-R-MV-002" -> missingRequiredCookie(variant);
            case "F543-R-MV-003" -> missingRequiredMultipartPart(variant);
            case "F543-R-NET-002" -> malformedHttpStatusLine(variant);
            default -> capture(() -> executeCaptured(familyId, variant));
        };
    }

    private static void executeCaptured(String familyId, int variant) throws Exception {
        switch (familyId) {
            case "F543-R-JV-001" -> byteBufferOverflow(variant);
            case "F543-R-JV-002" -> recursiveConcurrentMapUpdate(variant);
            case "F543-R-JV-003" -> incompatibleMethodHandleAdaptation(variant);
            case "F543-R-JV-004" -> serializeNonSerializableObject(variant);
            case "F543-R-JV-005" -> encodeUnmappableAscii(variant);
            case "F543-R-JV-006" -> readInvalidGzipHeader(variant);
            case "F543-R-JV-007" -> unlockInvalidWriteStamp(variant);
            case "F543-R-JV-008" -> addToFullQueue(variant);
            case "F543-R-SP-001" -> parseMalformedSpel(variant);
            case "F543-R-SP-002" -> writeReadOnlyProperty(variant);
            case "F543-R-SP-003" -> parseCaptureRestBeforeEnd(variant);
            case "F543-R-JS-001" -> serializeEmptyBean(variant);
            case "F543-R-JS-002" -> serializeDirectSelfReference(variant);
            case "F543-R-NET-001" -> bindOccupiedLoopbackPort(variant);
            default -> throw new AssertionError("validated family was not dispatched: " + familyId);
        }
    }

    private static void byteBufferOverflow(int variant) {
        ByteBuffer buffer = ByteBuffer.allocate(variant + 1);
        buffer.put(new byte[variant + 2]);
    }

    private static void recursiveConcurrentMapUpdate(int variant) {
        ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();
        String key = "controlled-" + variant;
        values.computeIfAbsent(key, ignored -> values.computeIfAbsent(key, nested -> "value"));
    }

    private static void incompatibleMethodHandleAdaptation(int variant)
            throws NoSuchMethodException, IllegalAccessException {
        MethodHandle length =
                MethodHandles.lookup()
                        .findVirtual(String.class, "length", MethodType.methodType(int.class));
        Class<?> incompatibleParameter = variant == 0 ? int.class : long.class;
        length.asType(MethodType.methodType(int.class, incompatibleParameter));
    }

    private static void serializeNonSerializableObject(int variant) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(new NonSerializableValue("value-" + variant));
        }
    }

    private static void encodeUnmappableAscii(int variant) throws IOException {
        String value = variant == 0 ? "caf\u00e9" : "price-\u20ac";
        StandardCharsets.US_ASCII
                .newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(java.nio.CharBuffer.wrap(value));
    }

    private static void readInvalidGzipHeader(int variant) throws IOException {
        byte[] bytes =
                variant == 0
                        ? new byte[] {0x00, 0x01, 0x02, 0x03}
                        : new byte[] {0x1f, (byte) 0x8a, 0x08, 0x00};
        try (GZIPInputStream ignored = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            // Construction validates the controlled header.
        }
    }

    private static void unlockInvalidWriteStamp(int variant) {
        StampedLock lock = new StampedLock();
        long stamp = lock.writeLock();
        lock.unlockWrite(stamp);
        lock.unlockWrite(variant == 0 ? stamp : stamp + 1);
    }

    private static void addToFullQueue(int variant) {
        ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(variant + 1);
        for (int index = 0; index < variant + 1; index++) {
            queue.add("seed-" + index);
        }
        queue.add("overflow-" + variant);
    }

    private static void parseMalformedSpel(int variant) {
        new SpelExpressionParser().parseExpression(variant == 0 ? "1 +" : "payload[");
    }

    private static void writeReadOnlyProperty(int variant) {
        new BeanWrapperImpl(new ReadOnlyValue("value-" + variant))
                .setPropertyValue("value", "replacement-" + variant);
    }

    private static void parseCaptureRestBeforeEnd(int variant) {
        String pattern = variant == 0 ? "/{*path" : "/api/{*rest";
        new PathPatternParser().parse(pattern);
    }

    private static Throwable missingRequiredHeader(int variant) {
        return resolvedMvcFailure(
                get("/fixture/header").queryParam("variant", Integer.toString(variant)),
                "missing required header");
    }

    private static Throwable missingRequiredCookie(int variant) {
        return resolvedMvcFailure(
                get("/fixture/cookie").queryParam("variant", Integer.toString(variant)),
                "missing required cookie");
    }

    private static Throwable missingRequiredMultipartPart(int variant) {
        return resolvedMvcFailure(
                multipart("/fixture/multipart").param("variant", Integer.toString(variant)),
                "missing required multipart part");
    }

    private static Throwable resolvedMvcFailure(RequestBuilder request, String description) {
        try {
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new RequiredInputController()).build();
            MvcResult result = mvc.perform(request).andReturn();
            Throwable failure = result.getResolvedException();
            if (failure != null) {
                if (result.getResponse().getStatus() != 400) {
                    throw new IllegalStateException(
                            description
                                    + " expected HTTP 400 but observed "
                                    + result.getResponse().getStatus());
                }
                return failure;
            }
        } catch (Throwable failure) {
            return failure;
        }
        throw new IllegalStateException(description + " completed without an exception");
    }

    private static void serializeEmptyBean(int variant) throws IOException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.writeValueAsString(new ControlledValueA(variant));
    }

    private static void serializeDirectSelfReference(int variant) throws IOException {
        ControlledValueB value = new ControlledValueB("value-" + variant);
        value.self = value;
        new ObjectMapper().writeValueAsString(value);
    }

    private static void bindOccupiedLoopbackPort(int variant) throws IOException {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (ServerSocket occupied = new ServerSocket();
                ServerSocket duplicate = new ServerSocket()) {
            occupied.setReuseAddress(false);
            occupied.bind(new InetSocketAddress(loopback, 0), variant + 1);
            duplicate.setReuseAddress(false);
            duplicate.bind(new InetSocketAddress(loopback, occupied.getLocalPort()), variant + 1);
        }
    }

    private static Throwable malformedHttpStatusLine(int variant) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (ServerSocket server = loopbackServerSocket()) {
            Future<Void> serverTask =
                    executor.submit(
                            () -> {
                                serveMalformedStatusLine(server, variant);
                                return null;
                            });
            URI uri =
                    new URI(
                            "http",
                            null,
                            server.getInetAddress().getHostAddress(),
                            server.getLocalPort(),
                            "/fixture/" + variant,
                            null,
                            null);
            HttpClient client =
                    HttpClient.newBuilder()
                            .connectTimeout(CLIENT_TIMEOUT)
                            .executor(executor)
                            .build();
            Throwable failure =
                    capture(
                            () ->
                                    client.send(
                                            HttpRequest.newBuilder(uri)
                                                    .timeout(CLIENT_TIMEOUT)
                                                    .GET()
                                                    .build(),
                                            HttpResponse.BodyHandlers.discarding()));
            awaitServerTask(serverTask);
            return failure;
        } catch (Throwable failure) {
            return failure;
        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(
                        COORDINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException(
                            "malformed HTTP fixture thread did not terminate");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static ServerSocket loopbackServerSocket() throws IOException {
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(false);
        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1);
        server.setSoTimeout((int) COORDINATION_TIMEOUT_MILLIS);
        return server;
    }

    private static void serveMalformedStatusLine(ServerSocket server, int variant)
            throws IOException {
        try (Socket socket = server.accept()) {
            socket.setSoTimeout((int) COORDINATION_TIMEOUT_MILLIS);
            readRequestHeaders(socket);
            byte[] response =
                    (variant == 0 ? "NOT-HTTP\r\n\r\n" : "HTTP/1.1 two-hundred OK\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII);
            socket.getOutputStream().write(response);
            socket.getOutputStream().flush();
        }
    }

    private static void readRequestHeaders(Socket socket) throws IOException {
        byte[] terminator = {'\r', '\n', '\r', '\n'};
        int matched = 0;
        while (matched < terminator.length) {
            int next = socket.getInputStream().read();
            if (next == -1) {
                throw new IOException("client closed before request headers completed");
            }
            matched = next == terminator[matched] ? matched + 1 : (next == terminator[0] ? 1 : 0);
        }
    }

    private static void awaitServerTask(Future<Void> serverTask) throws Exception {
        try {
            serverTask.get(COORDINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw failure;
        }
    }

    private static Throwable capture(ThrowingAction action) {
        try {
            action.run();
        } catch (Throwable failure) {
            return failure;
        }
        throw new IllegalStateException("controlled operation completed without an exception");
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    @RestController
    private static final class RequiredInputController {
        @GetMapping("/fixture/header")
        String header(@RequestHeader("X-Fixture") String value) {
            return value;
        }

        @GetMapping("/fixture/cookie")
        String cookie(@CookieValue("fixture") String value) {
            return value;
        }

        @org.springframework.web.bind.annotation.PostMapping("/fixture/multipart")
        String multipart(@RequestPart("fixture") MultipartFile value) {
            return value.getOriginalFilename();
        }
    }

    private record NonSerializableValue(String value) {}

    private static final class ReadOnlyValue {
        private final String value;

        private ReadOnlyValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private static final class ControlledValueA {
        @SuppressWarnings("unused")
        private final int value;

        private ControlledValueA(int value) {
            this.value = value;
        }
    }

    private static final class ControlledValueB {
        public final String value;
        public ControlledValueB self;

        private ControlledValueB(String value) {
            this.value = value;
        }
    }
}
