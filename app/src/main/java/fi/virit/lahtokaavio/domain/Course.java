package fi.virit.lahtokaavio.domain;

import java.util.List;

public record Course(String name, StartPlace startPlace, List<Integer> controlCodes) {

    public Course {
        controlCodes = List.copyOf(controlCodes);
    }
}
