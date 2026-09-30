package com.cinescout.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTest {

    @Test
    void writesAHeaderAndRowsWithCrLfAfterAByteOrderMark() {
        String csv = Csv.of(List.of("Venue", "Fit"), List.of(List.of("Tom's Diner", "82"), Arrays.asList("Sky Bar", null)));

        assertThat(csv).isEqualTo("﻿Venue,Fit\r\nTom's Diner,82\r\nSky Bar,\r\n");
    }

    @Test
    void quotesCellsThatWouldBreakTheTable() {
        assertThat(Csv.cell("Brooklyn, NY")).isEqualTo("\"Brooklyn, NY\"");
        assertThat(Csv.cell("The \"best\" bar")).isEqualTo("\"The \"\"best\"\" bar\"");
        assertThat(Csv.cell("line one\nline two")).isEqualTo("\"line one\nline two\"");
        assertThat(Csv.cell(" padded ")).isEqualTo("\" padded \"");
        assertThat(Csv.cell("plain")).isEqualTo("plain");
        assertThat(Csv.cell("")).isEmpty();
        assertThat(Csv.cell(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(A1)", "+1+1", "-2+3", "@SUM(A1)", "\tTAB", "=1,2"})
    void defusesCellsASpreadsheetWouldRunAsAFormula(String value) {
        assertThat(Csv.cell(value).replaceFirst("^\"", "")).startsWith("'" + value.charAt(0));
    }

    @Test
    void aPlainNegativeNumberStaysANumber() {
        assertThat(Csv.cell("-73.963316")).isEqualTo("-73.963316");
        assertThat(Csv.cell("-73.9+cmd|' /C calc'!A0")).startsWith("'-73.9");
    }
}
