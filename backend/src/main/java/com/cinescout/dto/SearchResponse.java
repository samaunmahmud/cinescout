package com.cinescout.dto;

import java.util.List;
import java.util.UUID;

/** What a quick search found across the projects the person is on, a few of each kind, best matches first. */
public record SearchResponse(List<ProjectHit> projects, List<SceneHit> scenes, List<VenueHit> venues) {

    public record ProjectHit(UUID id, String title, String locationArea) {
    }

    public record SceneHit(UUID id, Integer sceneNumber, String title, UUID projectId, String projectTitle) {
    }

    public record VenueHit(UUID id, String name, String address, String status, UUID sceneId, String sceneTitle, String projectTitle) {
    }
}
