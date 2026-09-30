package com.cinescout.dto;

/**
 * A call sheet as the crew sees it through a shared link: the production's name and area, who prepared it, and
 * the schedule. Nothing else of the project (its description, its scenes' scripts, the other candidates) is in it.
 */
public record PublicCallSheetResponse(String projectTitle, String locationArea, String preparedBy, ScheduleResponse schedule) {
}
