package com.cinescout.pack;

import java.time.LocalDate;
import java.util.List;

/**
 * What a production's location pack says, gathered from the database: per scene, its confirmed venue, or its best
 * shortlisted ones while none is confirmed.
 */
public record PackFacts(String production, String area, String preparedBy, LocalDate preparedOn, List<Scene> scenes) {

    public record Scene(String label, String dates, String access, List<Venue> venues) {
    }

    /**
     * One venue. {@code photo} is its cover photo as JPEG or PNG bytes, or null; {@code recce} is label and answer pairs;
     * {@code days} are the booked or held days as sentences.
     */
    public record Venue(String name, String status, String address, String position, String mapUrl, Integer fitScore, String fitReason,
                        String booking, String bookingNote, List<String> warnings, String contact, String quote, String notes,
                        String website, List<String> days, List<String[]> recce, String permit, byte[] photo) {

        public Venue withPhoto(byte[] bytes) {
            return new Venue(name, status, address, position, mapUrl, fitScore, fitReason, booking, bookingNote, warnings, contact, quote,
                    notes, website, days, recce, permit, bytes);
        }
    }
}
