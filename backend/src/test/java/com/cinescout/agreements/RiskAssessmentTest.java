package com.cinescout.agreements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskAssessmentTest {

    private static RiskFacts bare(String heading, String setting) {
        return new RiskFacts("The Night Ferry", "Studio 9", null, heading, setting, SceneHeading.exterior(heading), false,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, List.of(), "Ada",
                LocalDate.of(2026, 10, 14));
    }

    private static List<String> titles(RiskFacts facts) {
        return RiskRules.hazards(facts).stream().map(Hazard::title).toList();
    }

    @Test
    void everyShootGetsTheCommonHazardsAndNothingItHasNoReasonFor() {
        assertThat(titles(bare("INT. OFFICE - DAY", "office"))).containsExactly("Cables and kit underfoot: slips and trips",
                "Carrying kit in and out", "Electricity", "Hot lamps and fire");
        Hazard power = RiskRules.hazards(bare("INT. OFFICE - DAY", "office")).get(2);
        assertThat(power.why()).isEqualTo("The venue's power has not been checked on the recce.");
        assertThat(power.controls()).anyMatch(c -> c.startsWith("A generator"));
    }

    @Test
    void theSceneAndRecceAddHazardsSayingWhy() {
        RiskFacts facts = new RiskFacts("The Night Ferry", "Old Quay Pier", "1 Dock St", "EXT. FERRY DECK - NIGHT", "ferry pier", true, true,
                null, null, LocalTime.of(17, 0), LocalTime.of(2, 0), 40, 6, true, null, 5.5, "Through the side gate", "STAIRS", false,
                "NONE", false, false, null, null, List.of("Strong wind: secure lights and flags", "Rain likely."), "Ada", LocalDate.of(2026, 10, 14));

        List<Hazard> hazards = RiskRules.hazards(facts);

        assertThat(hazards).extracting(Hazard::title).contains("Working at height", "Water nearby", "Weather",
                "Members of the public and traffic", "Night work and fatigue", "Numbers on site and welfare", "Calling for help",
                "Access for everyone");
        assertThat(hazards).filteredOn(h -> h.title().equals("Working at height")).first()
                .extracting(Hazard::why).asString().contains("5.5 m ceiling");
        assertThat(hazards).filteredOn(h -> h.title().equals("Weather")).first()
                .extracting(Hazard::why).asString().contains("Strong wind: secure lights and flags; Rain likely.");
        assertThat(hazards).filteredOn(h -> h.title().equals("Carrying kit in and out")).first()
                .extracting(Hazard::why).asString().isEqualTo("Kit goes up and down stairs (no lift); load-in: Through the side gate.");
        assertThat(hazards).filteredOn(h -> h.title().equals("Electricity")).first()
                .extracting(Hazard::why).asString().isEqualTo("The recce counted 6 sockets and three-phase supply.");
        assertThat(hazards).filteredOn(h -> h.title().equals("Numbers on site and welfare")).first()
                .extracting(Hazard::why).asString().contains("no toilets and no holding space");
    }

    @Test
    void placeWordsAreWholeWords() {
        assertThat(titles(bare("INT. SEASIDE CAFE", "seaside cafe"))).contains("Water nearby", "A working kitchen or bar");
        assertThat(titles(bare("INT. SEASON TICKET OFFICE", "barrister's chambers"))).doesNotContain("Water nearby", "A working kitchen or bar");
    }

    @Test
    void headingsAreReadFromTheTitleOrTheScript() {
        assertThat(SceneHeading.of("Ferry deck", "EXT. FERRY DECK - NIGHT\nWaves.")).isEqualTo("EXT. FERRY DECK - NIGHT");
        assertThat(SceneHeading.of("12 INT./EXT. CAR - DAY", "x")).isEqualTo("12 INT./EXT. CAR - DAY");
        assertThat(SceneHeading.of("Ferry deck", "Waves crash.")).isEqualTo("Ferry deck");
        assertThat(SceneHeading.exterior("12 INT./EXT. CAR - DAY")).isTrue();
        assertThat(SceneHeading.exterior("I/E CAR")).isTrue();
        assertThat(SceneHeading.exterior("INT. EXTRA ROOM")).isFalse();
        assertThat(SceneHeading.exterior("Exterior of the pub")).isFalse();
        assertThat(SceneHeading.timeOfDay("EXT. FERRY DECK - NIGHT")).isEqualTo("NIGHT");
        assertThat(SceneHeading.timeOfDay("INT. NIGHTCLUB - DAY")).isEqualTo("DAY");
        assertThat(SceneHeading.timeOfDay("Nightclub - late")).isNull();
    }

    @Test
    void theNearestHospitalAndWeatherWarningsComeFromTheLogisticsReport() throws IOException {
        JsonNode report = new ObjectMapper().readTree("""
                {"environment": {"nearbyServices": [
                    {"kind": "PHARMACY", "name": "Boots", "distanceMeters": 100},
                    {"kind": "HOSPITAL", "name": "Far General", "distanceMeters": 4200},
                    {"kind": "HOSPITAL", "name": "Royal London Hospital", "distanceMeters": 1240}]},
                 "weather": {"days": [{"warnings": ["Rain likely."]}, {"warnings": ["Rain likely.", "Cold: below 2 °C"]}]}}
                """);

        assertThat(RiskAssessmentService.nearestHospital(report)).isEqualTo("Royal London Hospital, 1.2 km away (check it has an A&E)");
        assertThat(RiskAssessmentService.weatherWarnings(report)).containsExactly("Rain likely", "Cold: below 2 °C");
        assertThat(RiskAssessmentService.nearestHospital(null)).isNull();
        assertThat(RiskAssessmentService.weatherWarnings(null)).isEmpty();
    }

    @Test
    void thePdfListsTheHazardsAndSaysOnEveryPageItIsADraft() throws IOException {
        RiskFacts facts = new RiskFacts("The Night Ferry", "Old Quay Pier", "1 Dock St", "EXT. FERRY DECK - NIGHT", "ferry pier", true, true,
                LocalDate.of(2026, 11, 2), null, LocalTime.of(17, 0), LocalTime.of(2, 0), 40, 0, null, null, 5.5, null, "STAIRS", false,
                "NONE", false, false, "Gate code 4411", "Royal London Hospital, 1.2 km away (check it has an A&E)", List.of("Rain likely"),
                "Ada", LocalDate.of(2026, 10, 14));

        byte[] pdf = RiskAssessmentPdf.write(facts, RiskRules.hazards(facts));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            int pages = document.getNumberOfPages();
            assertThat(text).contains("RISK ASSESSMENT", "DRAFT", "Venue: Old Quay Pier", "Scene: EXT. FERRY DECK - NIGHT",
                    "Nearest hospital: Royal London Hospital", "Emergency number: " + AgreementPdf.BLANK, "Phone signal: None on the recce",
                    "1. Cables and kit underfoot", "Water nearby", "The recce found no sockets, so a generator is likely",
                    "Risk before: L / M / H", "Gate code 4411", "Assessed by", "Page 1 of " + pages);
            assertThat(text.split("Draft from recorded details, not a completed assessment", -1).length - 1).isEqualTo(pages);
        }
    }

    @Test
    void theFileIsNamedAfterTheVenue() {
        assertThat(PdfFile.named("risk-assessment", "Old Quay Pier", "")).isEqualTo("risk-assessment-old-quay-pier.pdf");
    }
}
