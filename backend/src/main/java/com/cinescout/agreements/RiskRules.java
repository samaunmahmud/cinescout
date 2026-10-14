package com.cinescout.agreements;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Picks the hazards a draft risk assessment starts with, from what is recorded about the venue and the scene. Every
 * shoot gets the common ones (cables, carrying kit, power, hot lamps); the rest appear only when something recorded
 * points to them, each saying what did. It is a starting point for a competent person, never a finished assessment.
 */
final class RiskRules {

    /** Cast and crew above which welfare and numbers get their own hazard. */
    static final int LARGE_UNIT = 30;
    /** Ceilings at or above this need lights rigged from towers or ladders. */
    static final double HIGH_CEILING_M = 4.0;

    private static final Pattern HEIGHT = Pattern.compile("\\b(roof|rooftop|balcony|bridge|tower|scaffold\\w*|cliff|terrace|fire escape|mezzanine)\\b");
    private static final Pattern WATER = Pattern.compile("\\b(water|river\\w*|canal\\w*|dock\\w*|pier|quay\\w*|harbou?r\\w*|wharf|beach\\w*|lake\\w*|pool|pond|marina|ferry|boat\\w*|jetty|sea|seafront|seaside|lido)\\b");
    private static final Pattern KITCHEN = Pattern.compile("\\b(kitchen|diner|restaurant|bakery|cafe|café|canteen|bar)\\b");

    private RiskRules() {
    }

    static List<Hazard> hazards(RiskFacts f) {
        List<Hazard> hazards = new ArrayList<>();
        String place = ((f.settingType() == null ? "" : f.settingType()) + " " + (f.sceneHeading() == null ? "" : f.sceneHeading())
                + " " + (f.venueName() == null ? "" : f.venueName())).toLowerCase(Locale.ROOT);

        hazards.add(new Hazard("Cables and kit underfoot: slips and trips",
                "Every shoot runs cables and stands across floors and walkways.",
                List.of("Run cables along walls and under mats; tape or ramp crossings.",
                        "Keep fire exits, stairs and walkways clear of kit at all times.",
                        "Light dark routes and mark changes of level.")));

        hazards.add(carrying(f));
        hazards.add(power(f));

        hazards.add(new Hazard("Hot lamps and fire",
                "Film lights run hot and sit near fabrics, sets and the venue's own fittings.",
                List.of("Keep lamps clear of curtains, ceilings and anything that burns; use scrims and safety bonds.",
                        "Know the fire exits, alarm points and assembly point; brief the crew at the start of each day.",
                        "Keep an extinguisher of the right type by each lighting position.")));

        if (high(f, place)) {
            hazards.add(new Hazard("Working at height",
                    whyHeight(f, place),
                    List.of("Use towers or podiums for rigging, not chairs or stacked cases; ladders for short work only.",
                            "Only trained crew rig overhead; safety-bond everything hung.",
                            "Keep people out from under rigging while it goes up and comes down.",
                            "Edges people could fall from are barriered or have someone watching them.")));
        }
        if (WATER.matcher(place).find()) {
            hazards.add(new Hazard("Water nearby",
                    "The scene or venue (\"" + matched(WATER, place) + "\") suggests open water close to the set.",
                    List.of("Keep cast and crew back from edges; barrier or mark them.",
                            "Life-saving equipment (ring buoys, throw lines) at hand; a water safety person if anyone works near the edge.",
                            "Wet, slippery surfaces: suitable footwear, mats at the edge of the set.")));
        }
        if (KITCHEN.matcher(place).find()) {
            hazards.add(new Hazard("A working kitchen or bar",
                    "The venue (\"" + matched(KITCHEN, place) + "\") may have hot surfaces, knives, gas and wet floors.",
                    List.of("Agree with the venue what equipment is off, isolated or out of bounds.",
                            "Props food and drink prepared by someone trained; ask cast about allergies.",
                            "Clean spills straight away.")));
        }
        if (f.exterior()) {
            hazards.add(new Hazard("Weather",
                    f.weatherWarnings().isEmpty()
                            ? "The scene is exterior."
                            : "The scene is exterior, and the forecast says: " + String.join("; ", f.weatherWarnings()) + ".",
                    List.of("Check the forecast the day before and on the morning; agree who calls a weather stop.",
                            "Shelter, warm or cool drinks, and sun cream or warm layers for cast and crew.",
                            "Weight or lower stands, flags and tents in wind; stop high-wind work when gusts pick up.",
                            "Keep electrical kit out of the rain and off wet ground.")));
            hazards.add(new Hazard("Members of the public and traffic",
                    "Exterior work means people and vehicles passing the set.",
                    List.of("Marshals in hi-vis at the edges of the set; keep pavements usable or signpost a way round.",
                            "Any road work under the right permit, with traffic management where needed.",
                            "Unit vehicles parked legally and never blocking views at junctions.")));
        }
        if (f.night()) {
            hazards.add(new Hazard("Night work and fatigue",
                    "The scene is set at night or the day wraps late" + (f.wrapTime() == null ? "." : " (wrap " + f.wrapTime() + ")."),
                    List.of("Light walkways, unit base and parking.",
                            "Plan rest breaks and turnaround; nobody drives home too tired: offer taxis or rest.",
                            "Keep noise down for neighbours.")));
        }
        if (f.crewSize() != null && f.crewSize() >= LARGE_UNIT) {
            hazards.add(new Hazard("Numbers on site and welfare",
                    "About " + f.crewSize() + " cast and crew are expected." + welfareGaps(f),
                    List.of("Agree how many people the venue holds and keep to it.",
                            "Enough toilets, drinking water and somewhere to rest and eat out of the way of the set.",
                            "A sign-in sheet so everyone is accounted for in an evacuation.")));
        } else if (Boolean.FALSE.equals(f.toilets()) || Boolean.FALSE.equals(f.holdingSpace())) {
            hazards.add(new Hazard("Welfare", welfareGaps(f).strip(),
                    List.of("Hire toilets or agree access to nearby ones; somewhere to rest and eat out of the way of the set.")));
        }
        if ("NONE".equals(f.phoneSignal()) || "POOR".equals(f.phoneSignal())) {
            hazards.add(new Hazard("Calling for help",
                    "The recce found " + ("NONE".equals(f.phoneSignal()) ? "no" : "poor") + " phone signal.",
                    List.of("Radios for the crew, and a landline or a spot with signal everyone knows.",
                            "Write the venue's address and nearest hospital on the call sheet before the day.")));
        }
        if (Boolean.FALSE.equals(f.stepFree())) {
            hazards.add(new Hazard("Access for everyone",
                    "The recce says the venue is not step-free.",
                    List.of("Ask cast and crew ahead about access needs and plan around them.",
                            "Plan how anyone who needs help would leave in an emergency.")));
        }
        return hazards;
    }

    private static Hazard carrying(RiskFacts f) {
        List<String> why = new ArrayList<>();
        if ("STAIRS".equals(f.stairsOrLift())) {
            why.add("kit goes up and down stairs (no lift)");
        } else if ("LIFT".equals(f.stairsOrLift()) || "STAIRS_AND_LIFT".equals(f.stairsOrLift())) {
            why.add("kit goes in a lift: check its load limit");
        }
        if (present(f.loadInRoute())) {
            why.add("load-in: " + f.loadInRoute().strip());
        }
        return new Hazard("Carrying kit in and out",
                why.isEmpty() ? "Every shoot moves heavy cases, stands and lamps." : capital(String.join("; ", why)) + ".",
                List.of("Two people for heavy or awkward items; trolleys and ramps where the route allows.",
                        "Walk the load-in route first and clear it; protect the venue's floors and corners.",
                        "No one carries kit up stairs alone."));
    }

    private static Hazard power(RiskFacts f) {
        List<String> why = new ArrayList<>();
        boolean noMains = f.sockets() != null && f.sockets() == 0;
        if (f.sockets() == null) {
            why.add("The venue's power has not been checked on the recce");
        } else if (noMains) {
            why.add("The recce found no sockets, so a generator is likely");
        } else {
            why.add("The recce counted " + f.sockets() + " socket" + (f.sockets() == 1 ? "" : "s")
                    + (Boolean.TRUE.equals(f.threePhase()) ? " and three-phase supply" : ""));
        }
        if (present(f.powerNotes())) {
            why.add("notes: " + f.powerNotes().strip());
        }
        List<String> controls = new ArrayList<>(List.of(
                "Only tested equipment, on RCD protection; a qualified electrician makes any tie-in to the venue's supply.",
                "Do not overload the venue's circuits; agree what may be plugged in where."));
        if (noMains || f.sockets() == null) {
            controls.add("A generator stands outside, away from doors, windows and air intakes, with fuel stored and refilled safely.");
        }
        return new Hazard("Electricity", String.join("; ", why) + ".", controls);
    }

    private static boolean high(RiskFacts f, String place) {
        return (f.ceilingHeightM() != null && f.ceilingHeightM() >= HIGH_CEILING_M) || HEIGHT.matcher(place).find();
    }

    private static String whyHeight(RiskFacts f, String place) {
        List<String> why = new ArrayList<>();
        if (f.ceilingHeightM() != null && f.ceilingHeightM() >= HIGH_CEILING_M) {
            why.add("the recce measured a " + trim(f.ceilingHeightM()) + " m ceiling, so lights are rigged high");
        }
        if (HEIGHT.matcher(place).find()) {
            why.add("the scene or venue mentions \"" + matched(HEIGHT, place) + "\"");
        }
        return capital(String.join("; ", why)) + ".";
    }

    private static String welfareGaps(RiskFacts f) {
        List<String> gaps = new ArrayList<>();
        if (Boolean.FALSE.equals(f.toilets())) {
            gaps.add("no toilets");
        }
        if (Boolean.FALSE.equals(f.holdingSpace())) {
            gaps.add("no holding space");
        }
        return gaps.isEmpty() ? "" : " The recce found " + String.join(" and ", gaps) + ".";
    }

    private static String matched(Pattern pattern, String text) {
        var m = pattern.matcher(text);
        return m.find() ? m.group() : "";
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static String capital(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static boolean present(String text) {
        return text != null && !text.isBlank();
    }
}
