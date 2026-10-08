package com.cinescout.logistics.solar;

/**
 * How the sun lights a shot, from where the sun is and which way the camera faces: in the lens (the subject backlit),
 * behind the camera (front-lit), from the side, or down. Golden hour is the sun under 6° up.
 *
 * @param light  null when the camera's direction is not known (the sun's place is still given)
 * @param golden whether the sun is up but low enough for golden-hour light
 */
public record ShotLight(SunPosition sun, Light light, boolean golden, String text) {

    public enum Light { BACKLIT, SIDE_LIT, FRONT_LIT, SUN_DOWN }

    /** Below this the sun's disc is under the horizon (refraction included). */
    private static final double HORIZON = -0.833;
    private static final double GOLDEN_BELOW = 6;

    public static ShotLight of(SunPosition sun, Integer cameraBearing) {
        boolean up = sun.elevation() > HORIZON;
        boolean golden = up && sun.elevation() < GOLDEN_BELOW;
        Light light;
        if (!up) {
            light = Light.SUN_DOWN;
        } else if (cameraBearing == null) {
            light = null;
        } else {
            double off = angleBetween(sun.azimuth(), cameraBearing);
            light = off <= 45 ? Light.BACKLIT : off >= 135 ? Light.FRONT_LIT : Light.SIDE_LIT;
        }
        return new ShotLight(sun, light, golden, text(sun, light, golden));
    }

    /** 0 when the camera looks straight at the sun, 180 when the sun is right behind it. */
    static double angleBetween(double azimuth, double bearing) {
        return Math.abs(((azimuth - bearing) % 360 + 540) % 360 - 180);
    }

    private static String text(SunPosition sun, Light light, boolean golden) {
        String where = sun.text();
        if (light == null) {
            return where + (golden ? "; golden hour" : "") + ". Set which way the camera faces to see how it lights the shot.";
        }
        String how = switch (light) {
            case BACKLIT -> "the camera faces the sun: subject backlit, watch for flare";
            case FRONT_LIT -> "sun behind the camera: subject lit from the front";
            case SIDE_LIT -> "sun from the side: modelled, contrasty light";
            case SUN_DOWN -> "the sun is down: plan for lamps or blue hour";
        };
        return where + "; " + how + (golden ? ", golden hour" : "") + ".";
    }
}
