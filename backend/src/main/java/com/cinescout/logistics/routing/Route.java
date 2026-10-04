package com.cinescout.logistics.routing;

/** A drive between two points: how far by road and how long without traffic. */
public record Route(double distanceMeters, double durationSeconds) {
}
