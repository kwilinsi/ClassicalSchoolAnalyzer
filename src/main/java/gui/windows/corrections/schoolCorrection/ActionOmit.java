package gui.windows.corrections.schoolCorrection;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.Label;
import constructs.correction.schoolCorrection.ActionType;
import constructs.correction.schoolCorrection.OmitAction;
import constructs.correction.schoolCorrection.Action;
import org.jetbrains.annotations.NotNull;

/**
 * The panel for the {@link ActionType ActionType} {@link ActionType#OMIT OMIT}.
 */
class ActionOmit extends ActionPanel {
    ActionOmit() {
        super();
        addComponent(new Label("The school will be omitted from the database.")
                .setForegroundColor(TextColor.ANSI.BLACK_BRIGHT));
    }

    /**
     * Return a new {@link OmitAction OmitAction}.
     *
     * @return The new action.
     */
    @Override
    @NotNull
    Action makeAction() {
        return new OmitAction();
    }

    /**
     * The omit action is always valid, because it has no user input.
     *
     * @return <code>True</code>.
     */
    @Override
    boolean validateInput() {
        return true;
    }
}
