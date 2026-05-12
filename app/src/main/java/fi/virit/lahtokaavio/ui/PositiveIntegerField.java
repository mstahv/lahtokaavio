package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.textfield.IntegerField;

/**
 * Integer field with a label, initial value, minimum bound, and visible
 * step-buttons — the standard combo used throughout the parameter form.
 */
public class PositiveIntegerField extends IntegerField {

    public PositiveIntegerField(String label, int initialValue, int min) {
        super(label);
        setValue(initialValue);
        setMin(min);
        setStepButtonsVisible(true);
    }
}
