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
        assertThat(OFFICES.offices().stream().filter(office -> office.codes().stream().anyMatch(code -> code.startsWith("GB-")))).hasSize(33);
        assertThat(OFFICES.offices()).extracting(FilmingOffices.Office::area).contains("New York City", "Los Angeles", "Paris");
        assertThat(OFFICES.lastReviewed()).isNotNull();
        assertThat(OFFICES.sources()).isNotEmpty();
        assertThat(OFFICES.fallback().contactUrl()).startsWith("https://");
        Set<String> codes = new HashSet<>();
        OFFICES.offices().forEach(office -> {
            assertThat(office.contactUrl()).startsWith("https://");
            assertThat(office.codes().isEmpty() && office.names().isEmpty()).as(office.area() + " can be matched").isFalse();
            office.codes().forEach(code -> assertThat(codes.add(code)).as(code).isTrue());
        });
    }

    @Test
    void newYorkLosAngelesAndParisAreMatchedAsOpenStreetMapNamesThemAndParisDrawsTheLargeCrewAtEleven() {
        assertThat(OFFICES.officeFor(new AdminArea("Kings County", List.of("US-NY"), "us", 40.69, -73.92)).orElseThrow().area())
                .isEqualTo("New York City");
        assertThat(OFFICES.officeFor(new AdminArea("New York County", List.of("US-NY"), "us", 40.76, -73.99)).orElseThrow().office())
                .contains("Media and Entertainment");
        assertThat(OFFICES.officeFor(new AdminArea("Los Angeles", List.of("US-CA"), "us", 34.04, -118.23)).orElseThrow().office())
                .startsWith("FilmLA");
        assertThat(OFFICES.officeFor(new AdminArea("Los Angeles County", List.of("US-CA"), "us", 34.02, -118.40))).isPresent();
        assertThat(OFFICES.officeFor(new AdminArea("Erie County", List.of("US-NY"), "us", 42.9, -78.8))).isEmpty();

        FilmingOffices.Office paris = OFFICES.officeFor(new AdminArea("Paris", List.of("FR-75C", "FR-IDF"), "fr", 48.87, 2.38)).orElseThrow();
        assertThat(paris.leadTimeFor(10)).isEqualTo(5);
        assertThat(paris.leadTimeFor(11)).isEqualTo(15);
        assertThat(OFFICES.checklistFor(paris)).anyMatch(item -> item.contains("Eiffel Tower"));
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
