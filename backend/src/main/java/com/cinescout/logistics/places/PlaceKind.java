package com.cinescout.logistics.places;

/**
 * What a nearby place means to a film crew: a service it may need, or a source of noise that can ruin
 * a take. Each kind carries how far away it is still worth knowing about.
 *
 * <p>For noise sources, {@code loudness} (1 to 3) is how disruptive the source is at close range and
 * {@code radiusMeters} how far that disruption can carry: an airport's approach path reaches kilometres,
 * a church bell a couple of hundred metres.
 *
 * <p>The radii are also a cost: the map lookup's time grows with the area searched, and the public Overpass
 * server gives up after its timeout. Searching 10 km for a hospital in a dense city takes seconds on its own.
 */
public enum PlaceKind {

    // Services a unit needs: medical, parking for trucks, food, fuel, rooms, a hardware store.
    HOSPITAL(Group.SERVICE, 5_000, 0),
    PHARMACY(Group.SERVICE, 1_500, 0),
    PARKING(Group.SERVICE, 800, 0),
    FOOD(Group.SERVICE, 600, 0),
    TOILETS(Group.SERVICE, 800, 0),
    FUEL(Group.SERVICE, 2_000, 0),
    LODGING(Group.SERVICE, 2_000, 0),
    HARDWARE(Group.SERVICE, 2_000, 0),
    GROCERY(Group.SERVICE, 1_000, 0),

    // Noise sources, loudest reach first.
    AIRPORT(Group.NOISE, 5_000, 3),
    HELIPORT(Group.NOISE, 1_500, 3),
    STADIUM(Group.NOISE, 1_000, 2),
    RAILWAY(Group.NOISE, 500, 3),
    EMERGENCY_STATION(Group.NOISE, 500, 2),
    CONSTRUCTION(Group.NOISE, 400, 2),
    MAJOR_ROAD(Group.NOISE, 300, 3),
    SCHOOL(Group.NOISE, 300, 2),
    NIGHTLIFE(Group.NOISE, 250, 2),
    PLACE_OF_WORSHIP(Group.NOISE, 250, 1),

    // Somewhere to park the trucks: open car parks, roadside bays and lay-bys, rest areas. The radius is the
    // default; the Overpass client may be configured to look further.
    UNIT_BASE(Group.UNIT_BASE, 1_000, 0);

    public enum Group { SERVICE, NOISE, UNIT_BASE }

    private final Group group;
    private final int radiusMeters;
    private final int loudness;

    PlaceKind(Group group, int radiusMeters, int loudness) {
        this.group = group;
        this.radiusMeters = radiusMeters;
        this.loudness = loudness;
    }

    public Group group() { return group; }
    public int radiusMeters() { return radiusMeters; }
    /** 1 (a nuisance) to 3 (stops takes); 0 for services. */
    public int loudness() { return loudness; }
}
