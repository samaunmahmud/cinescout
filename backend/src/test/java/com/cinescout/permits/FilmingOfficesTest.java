package com.cinescout.permits;

import com.cinescout.domain.AdminArea;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilmingOfficesTest {

    private static final FilmingOffices OFFICES = FilmingOffices.load();

    private static AdminArea area(String name, String... codes) {
        return new AdminArea(name, List.of(codes), "gb", 51.5, -0.1);
    }

    @Test
    void theRepositorysFileListsEveryLondonBoroughOnceWithAnHttpsContact() {
        assertThat(OFFICES.offices()).hasSize(33);
        assertThat(OFFICES.lastReviewed()).isNotNull();
        assertThat(OFFICES.sources()).isNotEmpty();
        assertThat(OFFICES.fallback().contactUrl()).startsWith("https://");
        Set<String> codes = new HashSet<>();
        OFFICES.offices().forEach(office -> {
            assertThat(office.contactUrl()).startsWith("https://");
            assertThat(office.codes()).hasSize(1).allSatisfy(code -> assertThat(codes.add(code)).as(code).isTrue());
        });
    }

    @Test
    void anAreaIsMatchedByItsCodeFirstAndThenByName() {
        assertThat(OFFICES.officeFor(area("London Borough of Camden", "GB-CMD", "GB-ENG")).orElseThrow().area()).isEqualTo("Camden");
        assertThat(OFFICES.officeFor(area("Somewhere", "GB-WSM")).orElseThrow().area()).isEqualTo("Westminster");
        assertThat(OFFICES.officeFor(area("City of Westminster")).orElseThrow().area()).isEqualTo("Westminster");
        assertThat(OFFICES.officeFor(area("Manchester", "GB-MAN", "GB-ENG"))).isEmpty();
        assertThat(OFFICES.officeFor(null)).isEmpty();
    }

    @Test
    void theLeadTimeFollowsTheCrewSize() {
        FilmingOffices.Office camden = OFFICES.officeFor(area("x", "GB-CMD")).orElseThrow();

        assertThat(camden.leadTimeFor(null)).isEqualTo(5);
        assertThat(camden.leadTimeFor(12)).isEqualTo(5);
        assertThat(camden.leadTimeFor(40)).isEqualTo(7);
        assertThat(OFFICES.checklistFor(OFFICES.officeFor(area("x", "GB-WSM")).orElseThrow()))
                .last().asString().contains("Notice of No Objection");
    }

    @Test
    void aFileWithAnInsecureOrMissingContactIsRefused() {
        String yaml = """
                lastReviewed: 2026-10-04
                fallback: { area: UK, office: Council, contactUrl: "https://www.gov.uk/find-local-council", note: Ask }
                offices:
                  - { area: Nowhere, codes: [GB-XXX], office: Nobody, contactUrl: "http://example.com" }
                """;

        assertThatThrownBy(() -> FilmingOffices.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))))
                .hasMessageContaining("https contactUrl");
    }

    @Test
    void anUnquotedReviewDateIsReadAsADate() {
        String yaml = """
                lastReviewed: 2026-10-04
                fallback: { area: UK, office: Council, contactUrl: "https://www.gov.uk/find-local-council", note: Ask }
                """;

        assertThat(FilmingOffices.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).lastReviewed())
                .isEqualTo(LocalDate.of(2026, 10, 4));
    }
}
