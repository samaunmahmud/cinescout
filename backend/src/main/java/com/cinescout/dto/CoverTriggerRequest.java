package com.cinescout.dto;

import jakarta.validation.constraints.Size;

/** @param trigger when to switch to the cover; null or blank clears it */
public record CoverTriggerRequest(@Size(max = 200) String trigger) {
}
