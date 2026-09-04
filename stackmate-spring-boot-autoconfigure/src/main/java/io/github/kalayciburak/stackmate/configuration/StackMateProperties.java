package io.github.kalayciburak.stackmate.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/** Type-safe root configuration for the bounded StackMate runtime. */
@ConfigurationProperties(prefix = "stackmate", ignoreUnknownFields = false)
public class StackMateProperties {

    private boolean enabled = true;
    private List<String> applicationPackages = new ArrayList<>();

    @NestedConfigurationProperty private CaptureProperties capture = new CaptureProperties();

    @NestedConfigurationProperty
    private DiagnosticsProperties diagnostics = new DiagnosticsProperties();

    @NestedConfigurationProperty private PrivacyProperties privacy = new PrivacyProperties();

    @NestedConfigurationProperty
    private DeduplicationProperties deduplication = new DeduplicationProperties();

    @NestedConfigurationProperty private ConsoleProperties console = new ConsoleProperties();

    @NestedConfigurationProperty private ExportProperties export = new ExportProperties();

    @NestedConfigurationProperty private SafetyProperties safety = new SafetyProperties();

    /** Creates root properties with StackMate enabled and all nested defaults initialized. */
    public StackMateProperties() {}

    /**
     * Returns whether StackMate auto-configuration is enabled.
     *
     * @return whether StackMate is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether StackMate auto-configuration is enabled.
     *
     * @param enabled whether StackMate is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the configured application package prefixes used for frame selection.
     *
     * @return unmodifiable copy of application package prefixes
     */
    public List<String> getApplicationPackages() {
        return List.copyOf(applicationPackages);
    }

    /**
     * Replaces the application package prefixes used for frame selection.
     *
     * @param applicationPackages up to 32 nonblank prefixes of at most 256 characters each
     */
    public void setApplicationPackages(List<String> applicationPackages) {
        this.applicationPackages =
                new ArrayList<>(
                        PropertyValidation.textList(
                                applicationPackages, "applicationPackages", 32, 256));
    }

    /**
     * Returns servlet exception capture settings.
     *
     * @return capture settings
     */
    public CaptureProperties getCapture() {
        return capture;
    }

    /**
     * Replaces servlet exception capture settings.
     *
     * @param capture non-null capture settings
     */
    public void setCapture(CaptureProperties capture) {
        this.capture = Objects.requireNonNull(capture, "capture");
    }

    /**
     * Returns throwable and frame extraction bounds.
     *
     * @return diagnostic settings
     */
    public DiagnosticsProperties getDiagnostics() {
        return diagnostics;
    }

    /**
     * Replaces throwable and frame extraction bounds.
     *
     * @param diagnostics non-null diagnostic settings
     */
    public void setDiagnostics(DiagnosticsProperties diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    /**
     * Returns additional sanitizer redaction settings.
     *
     * @return privacy settings
     */
    public PrivacyProperties getPrivacy() {
        return privacy;
    }

    /**
     * Replaces additional sanitizer redaction settings.
     *
     * @param privacy non-null privacy settings
     */
    public void setPrivacy(PrivacyProperties privacy) {
        this.privacy = Objects.requireNonNull(privacy, "privacy");
    }

    /**
     * Returns duplicate incident retention settings.
     *
     * @return deduplication settings
     */
    public DeduplicationProperties getDeduplication() {
        return deduplication;
    }

    /**
     * Replaces duplicate incident retention settings.
     *
     * @param deduplication non-null deduplication settings
     */
    public void setDeduplication(DeduplicationProperties deduplication) {
        this.deduplication = Objects.requireNonNull(deduplication, "deduplication");
    }

    /**
     * Returns deterministic console presentation settings.
     *
     * @return console settings
     */
    public ConsoleProperties getConsole() {
        return console;
    }

    /**
     * Replaces deterministic console presentation settings.
     *
     * @param console non-null console settings
     */
    public void setConsole(ConsoleProperties console) {
        this.console = Objects.requireNonNull(console, "console");
    }

    /**
     * Returns local sanitized incident export settings.
     *
     * @return export settings
     */
    public ExportProperties getExport() {
        return export;
    }

    /**
     * Replaces local sanitized incident export settings.
     *
     * @param export non-null export settings
     */
    public void setExport(ExportProperties export) {
        this.export = Objects.requireNonNull(export, "export");
    }

    /**
     * Returns production-profile safety settings.
     *
     * @return safety settings
     */
    public SafetyProperties getSafety() {
        return safety;
    }

    /**
     * Replaces production-profile safety settings.
     *
     * @param safety non-null safety settings
     */
    public void setSafety(SafetyProperties safety) {
        this.safety = Objects.requireNonNull(safety, "safety");
    }
}
