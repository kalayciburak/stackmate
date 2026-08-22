package io.github.kalayciburak.stackmate.benchmark.corpus.realworld;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.throwable.ThrowableNode;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotLimits;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Explicit Docker-backed development recipe lab; excluded from normal test discovery. */
class RealWorldPostgresRecipeLab {

    private static final ThrowableSnapshotter SNAPSHOTTER =
            new ThrowableSnapshotter(
                    ThrowableSnapshotLimits.defaults(), DefaultSanitizer.defaults());

    private static final PostgreSQLContainer CONTAINER =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("stackmate_gate_c")
                    .withUsername("stackmate_fixture")
                    .withPassword("controlled_fixture_password")
                    .withReuse(false);

    @BeforeAll
    static void startPostgres() {
        CONTAINER.start();
    }

    @AfterAll
    static void stopPostgres() {
        CONTAINER.stop();
    }

    @ParameterizedTest(name = "{0} variant {1}")
    @MethodSource("developmentPostgresRecipes")
    void reproducesDevelopmentPostgresSqlState(
            RealWorldCorpusManifest.Family family, int variant) {
        Throwable failure =
                RealWorldPostgresFixtures.execute(
                        family.familyId(), variant, RealWorldPostgresRecipeLab::open);

        assertThat(failure).isInstanceOf(java.sql.SQLException.class);
        List<String> observed =
                SNAPSHOTTER.capture(failure).causeChain().stream()
                        .map(ThrowableNode::type)
                        .toList();
        List<String> expected = new ArrayList<>(family.wrapperTopology());
        expected.add(family.rootType());
        assertThat(observed).containsExactlyElementsOf(expected);
    }

    static Stream<Arguments> developmentPostgresRecipes() {
        return RealWorldCorpusManifest.load().developmentFamilies().stream()
                .filter(item -> item.fixtureCode().equals("N-PG"))
                .flatMap(
                        item ->
                                Stream.of(
                                        Arguments.of(item, 0),
                                        Arguments.of(item, 1)));
    }

    private static Connection open(String catalog) throws SQLException {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setServerNames(new String[] {CONTAINER.getHost()});
        dataSource.setPortNumbers(new int[] {CONTAINER.getFirstMappedPort()});
        dataSource.setDatabaseName(
                catalog == null ? CONTAINER.getDatabaseName() : catalog);
        dataSource.setUser(CONTAINER.getUsername());
        dataSource.setPassword(CONTAINER.getPassword());
        dataSource.setConnectTimeout(5);
        return dataSource.getConnection();
    }
}
