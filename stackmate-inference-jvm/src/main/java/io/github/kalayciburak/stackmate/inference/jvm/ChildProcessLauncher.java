package io.github.kalayciburak.stackmate.inference.jvm;

import java.io.IOException;
import java.util.List;

interface ChildProcessLauncher {
    Process launch(List<String> command) throws IOException;

    static ChildProcessLauncher system() {
        return command ->
                new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
    }
}
