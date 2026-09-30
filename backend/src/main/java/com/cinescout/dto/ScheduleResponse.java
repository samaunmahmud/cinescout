package com.cinescout.dto;

import com.cinescout.domain.Location;
import com.cinescout.domain.Scene;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A project's shoot as a calendar: which scenes start on which day, and where each will be shot.
 *
 * @param days        the days on which scenes start shooting, earliest first
 * @param unscheduled scenes without shoot dates, in script order
 */
public record ScheduleResponse(List<ShootDay> days, List<ScheduledScene> unscheduled) {

    /** @param scenes the scenes whose shoot starts that day, in script order */
    public record ShootDay(LocalDate date, List<ScheduledScene> scenes) {
    }

    /**
     * @param shootDateStart null when the scene has only a last day
     * @param shootDateEnd   null when the scene has only a first day
     * @param settingType    the kind of place the scene needs, once it has been analysed
     * @param venues         the scene's confirmed locations; empty while none is confirmed
     * @param candidates     how many candidate locations the scene has in all
     */
    public record ScheduledScene(
            UUID id,
            Integer sceneNumber,
            String title,
            LocalDate shootDateStart,
            LocalDate shootDateEnd,
            String settingType,
            String timeOfDay,
            List<Venue> venues,
            long candidates
    ) {

        public static ScheduledScene from(Scene scene, List<Location> confirmed, long candidates) {
            var requirements = scene.requirements();
            return new ScheduledScene(scene.getId(), scene.getSceneNumber(), scene.getTitle(),
                    scene.getShootDateStart(), scene.getShootDateEnd(),
                    requirements == null ? null : requirements.settingType(),
                    requirements == null ? null : requirements.timeOfDay(),
                    confirmed.stream().map(Venue::from).toList(), candidates);
        }
    }

    public record Venue(UUID id, String name, String address, BigDecimal latitude, BigDecimal longitude) {

        static Venue from(Location location) {
            return new Venue(location.getId(), location.getName(), location.getAddress(), location.getLatitude(), location.getLongitude());
        }
    }
}
