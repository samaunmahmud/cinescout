package com.cinescout.dto;

import java.util.List;
import java.util.UUID;

/**
 * The scenes found in a script: what an import would add (a preview) or has added.
 *
 * @param scenes            in script order
 * @param scriptNumbersKept true when the scenes carry the numbers printed in the script; false when they were
 *                          numbered on from the project's last scene, because the script is not numbered
 *                          throughout or its numbers are already taken
 */
public record ScriptImportResponse(List<ImportedScene> scenes, boolean scriptNumbersKept) {

    /**
     * @param id         null in a preview
     * @param characters the length of the scene's text as saved
     * @param truncated  true when the scene was longer than a scene may be and its end was cut off
     */
    public record ImportedScene(UUID id, Integer sceneNumber, String title, int characters, boolean truncated) {
    }
}
