package io.github.kalayciburak.stackmate.inference.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ChildProcessLauncherTest {

    @Test
    void launchesTheCurrentJavaExecutableWithoutShellInterpolation() throws Exception {
        String javaExecutable =
                ProcessHandle.current()
                        .info()
                        .command()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "current Java command is unavailable"));

        Process process = ChildProcessLauncher.system().launch(List.of(javaExecutable, "-version"));

        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8))
                .containsIgnoringCase("version");
    }
}
