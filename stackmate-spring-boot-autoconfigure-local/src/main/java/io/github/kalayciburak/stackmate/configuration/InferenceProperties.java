package io.github.kalayciburak.stackmate.configuration;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bounded controls retained for the historical, unsupported local inference subsystem. */
@ConfigurationProperties(prefix = "stackmate-research.inference", ignoreUnknownFields = false)
public class InferenceProperties {

    /** Selects an installed local inference engine. */
    public enum Engine {
        AUTO
    }

    /** Selects the supported local deployment strategy. */
    public enum Deployment {
        AUTO
    }

    private boolean enabled = true;
    private InferenceTriggerPolicy triggerPolicy =
            InferenceTriggerPolicy.WHEN_ENHANCEMENT_IS_USEFUL;
    private Engine engine = Engine.AUTO;
    private Deployment deployment = Deployment.AUTO;
    private String modelPath = "";
    private boolean lazyLoad = true;
    private double temperature;
    private int maximumInputCharacters = 16_000;
    private int maximumOutputTokens = 256;
    private Duration timeout = Duration.ofSeconds(10);
    private int repairAttempts = 1;
    private ExecutorProperties executor = new ExecutorProperties();
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public InferenceTriggerPolicy getTriggerPolicy() {
        return triggerPolicy;
    }

    public void setTriggerPolicy(InferenceTriggerPolicy triggerPolicy) {
        this.triggerPolicy = Objects.requireNonNull(triggerPolicy, "triggerPolicy");
    }

    public Engine getEngine() {
        return engine;
    }

    public void setEngine(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public Deployment getDeployment() {
        return deployment;
    }

    public void setDeployment(Deployment deployment) {
        this.deployment = Objects.requireNonNull(deployment, "deployment");
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        String normalized = modelPath == null ? "" : modelPath.trim();
        if (normalized.length() > 4_096) {
            throw new IllegalArgumentException("inference.modelPath is too long");
        }
        this.modelPath = normalized;
    }

    public boolean isLazyLoad() {
        return lazyLoad;
    }

    public void setLazyLoad(boolean lazyLoad) {
        this.lazyLoad = lazyLoad;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        if (Double.compare(temperature, 0.0) != 0) {
            throw new IllegalArgumentException(
                    "inference.temperature must be 0.0 in this StackMate version");
        }
        this.temperature = 0.0;
    }

    public int getMaximumInputCharacters() {
        return maximumInputCharacters;
    }

    public void setMaximumInputCharacters(int maximumInputCharacters) {
        this.maximumInputCharacters =
                PropertyValidation.range(
                        maximumInputCharacters, 1, 1_000_000, "inference.maximumInputCharacters");
    }

    public int getMaximumOutputTokens() {
        return maximumOutputTokens;
    }

    public void setMaximumOutputTokens(int maximumOutputTokens) {
        this.maximumOutputTokens =
                PropertyValidation.range(
                        maximumOutputTokens, 1, 8_192, "inference.maximumOutputTokens");
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        Duration validated = PropertyValidation.positive(timeout, "inference.timeout");
        if (validated.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("inference.timeout must not exceed one hour");
        }
        this.timeout = validated;
    }

    public int getRepairAttempts() {
        return repairAttempts;
    }

    public void setRepairAttempts(int repairAttempts) {
        this.repairAttempts =
                PropertyValidation.range(repairAttempts, 0, 1, "inference.repairAttempts");
    }

    public ExecutorProperties getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorProperties executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public CircuitBreakerProperties getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreakerProperties circuitBreaker) {
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
    }

    /** Bounds the dedicated inference executor. */
    public static class ExecutorProperties {

        private int threads = 1;
        private int queueCapacity = 8;

        public int getThreads() {
            return threads;
        }

        public void setThreads(int threads) {
            if (threads != 1) {
                throw new IllegalArgumentException(
                        "inference.executor.threads must be 1 for the serialized CPU engine");
            }
            this.threads = 1;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity =
                    PropertyValidation.range(
                            queueCapacity, 1, 10_000, "inference.executor.queueCapacity");
        }
    }

    /** Bounds failures before local inference temporarily pauses. */
    public static class CircuitBreakerProperties {

        private int failureThreshold = 3;
        private Duration cooldown = Duration.ofMinutes(5);

        public int getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold =
                    PropertyValidation.positive(
                            failureThreshold, "inference.circuitBreaker.failureThreshold");
        }

        public Duration getCooldown() {
            return cooldown;
        }

        public void setCooldown(Duration cooldown) {
            this.cooldown =
                    PropertyValidation.positive(cooldown, "inference.circuitBreaker.cooldown");
        }
    }
}
