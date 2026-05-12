package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.theme.aura.Aura;
import org.vaadin.firitin.appframework.MainLayout;

@StyleSheet(Aura.STYLESHEET)
public class TopLayout extends MainLayout {
    @Override
    protected Object getDrawerHeader() {
        return "Lähtökaavio}>";
    }
}
