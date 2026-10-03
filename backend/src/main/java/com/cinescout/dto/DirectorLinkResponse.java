package com.cinescout.dto;

import com.cinescout.domain.DirectorLink;

import java.time.Instant;
import java.util.UUID;

/** A director link: its token (the web app builds the URL from it), and whether it is for one scene. */
public record DirectorLinkResponse(String token, UUID sceneId, boolean showPrivate, Instant createdAt) {

    public static DirectorLinkResponse from(DirectorLink link) {
        return new DirectorLinkResponse(link.getToken(), link.getScene() == null ? null : link.getScene().getId(),
                link.isShowPrivate(), link.getCreatedAt());
    }
}
