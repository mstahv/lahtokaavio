package fi.virit.lahtokaavio.domain;

import java.time.LocalTime;

/** A competitor's drawn start time, on the lane their class starts from. */
public record DrawnStart(LocalTime startTime, String laneId, Entry entry) {
}
