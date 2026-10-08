package com.cinescout.dto;

/** Where a spot is, in words, e.g. for "use my current location": "Shoreditch, London, United Kingdom". */
public record PlaceResponse(String name, double latitude, double longitude, String attribution) {
}
