package fi.virit.lahtokaavio.ui;

import static org.assertj.core.api.Assertions.assertThat;

import fi.virit.lahtokaavio.domain.CompetitionParameters;
import fi.virit.lahtokaavio.domain.StartDraw;
import fi.virit.lahtokaavio.domain.StartPlan;
import fi.virit.lahtokaavio.plan.StartPlanGenerator;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompetitionWorkspaceTest {

    private final CompetitionWorkspace workspace = new CompetitionWorkspace(new StartPlanGenerator());
    private final StartDraw draw = new StartDraw(List.of(), List.of(), List.of());

    @Test
    void changingThePlanDiscardsTheDraw() {
        workspace.setDraw(draw);
        workspace.setPlan(new StartPlan(CompetitionParameters.defaults(), List.of()));
        assertThat(workspace.draw()).isNull();
    }

    @Test
    void newEntriesDiscardTheDraw() {
        workspace.setDraw(draw);
        workspace.setEntries(List.of());
        assertThat(workspace.draw()).isNull();
    }
}
