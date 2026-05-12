package fi.virit.lahtokaavio.domain;

import java.util.List;

public record StartPlan(CompetitionParameters parameters, List<SeriesStart> seriesStarts) {

    public StartPlan {
        seriesStarts = List.copyOf(seriesStarts);
    }
}
