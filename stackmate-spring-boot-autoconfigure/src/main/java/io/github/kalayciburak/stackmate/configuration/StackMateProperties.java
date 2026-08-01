package io.github.kalayciburak.stackmate.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/** Type-safe root configuration for the bounded StackMate runtime. */
@ConfigurationProperties("stackmate")
public class StackMateProperties {

    /** Selects deterministic-only or deterministic-plus-local-inference processing. */
    public enum Mode {
        DETERMINISTIC,
        HYBRID
    }

    /** Selects the supported console language. */
    public enum Language {
        EN,
        TR
    }

    private boolean enabled = true;
    private Mode mode = Mode.DETERMINISTIC;
    private Language language = Language.EN;
    private List<String> applicationPackages = new ArrayList<>();

    @NestedConfigurationProperty private CaptureProperties capture = new CaptureProperties();

    @NestedConfigurationProperty
    private DiagnosticsProperties diagnostics = new DiagnosticsProperties();

    @NestedConfigurationProperty private PrivacyProperties privacy = new PrivacyProperties();

    @NestedConfigurationProperty private InferenceProperties inference = new InferenceProperties();

    @NestedConfigurationProperty
    private DeduplicationProperties deduplication = new DeduplicationProperties();

    @NestedConfigurationProperty private ConsoleProperties console = new ConsoleProperties();

    @NestedConfigurationProperty private ExportProperties export = new ExportProperties();

    @NestedConfigurationProperty private SafetyProperties safety = new SafetyProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = Objects.requireNonNull(language, "language");
    }

    public List<String> getApplicationPackages() {
        return List.copyOf(applicationPackages);
    }

    public void setApplicationPackages(List<String> applicationPackages) {
        this.applicationPackages =
                new ArrayList<>(
                        PropertyValidation.textList(
                                applicationPackages, "applicationPackages", 32, 256));
    }

    public CaptureProperties getCapture() {
        return capture;
    }

    public void setCapture(CaptureProperties capture) {
        this.capture = Objects.requireNonNull(capture, "capture");
    }

    public DiagnosticsProperties getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(DiagnosticsProperties diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public PrivacyProperties getPrivacy() {
        return privacy;
    }

    public void setPrivacy(PrivacyProperties privacy) {
        this.privacy = Objects.requireNonNull(privacy, "privacy");
    }

    public InferenceProperties getInference() {
        return inference;
    }

    public void setInference(InferenceProperties inference) {
        this.inference = Objects.requireNonNull(inference, "inference");
    }

    public DeduplicationProperties getDeduplication() {
        return deduplication;
    }

    public void setDeduplication(DeduplicationProperties deduplication) {
        this.deduplication = Objects.requireNonNull(deduplication, "deduplication");
    }

    public ConsoleProperties getConsole() {
        return console;
    }

    public void setConsole(ConsoleProperties console) {
        this.console = Objects.requireNonNull(console, "console");
    }

    public ExportProperties getExport() {
        return export;
    }

    public void setExport(ExportProperties export) {
        this.export = Objects.requireNonNull(export, "export");
    }

    public SafetyProperties getSafety() {
        return safety;
    }

    public void setSafety(SafetyProperties safety) {
        this.safety = Objects.requireNonNull(safety, "safety");
    }
}
