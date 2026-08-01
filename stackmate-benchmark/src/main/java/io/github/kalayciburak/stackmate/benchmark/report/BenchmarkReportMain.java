package io.github.kalayciburak.stackmate.benchmark.report;

import java.nio.file.Path;

/** Explicit report entry point; a normal test run also generates both target reports. */
public final class BenchmarkReportMain {

    private BenchmarkReportMain() {}

    public static void main(String[] arguments) throws Exception {
        Path target = arguments.length == 0 ? Path.of("target") : Path.of(arguments[0]);
        BenchmarkReportGenerator.GeneratedReports reports =
                new BenchmarkReportGenerator().writeReports(target);
        System.out.println(reports.json().toAbsolutePath());
        System.out.println(reports.markdown().toAbsolutePath());
    }
}
