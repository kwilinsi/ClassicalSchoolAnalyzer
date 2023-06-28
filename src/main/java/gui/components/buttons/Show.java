package gui.components.buttons;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import gui.utils.GUIUtils;
import org.jetbrains.annotations.NotNull;

/**
 * This is a particular type of {@link Button} that uses the show {@link #THEME}.
 */
public class Show extends Button {
    /**
     * The {@link Theme} for {@link Show} buttons.
     */
    @NotNull
    private static final Theme THEME = GUIUtils.getNewTheme(
            "foreground", "cyan",
            "background", "black_bright",
            "sgr[SELECTED]", "underline,bold",
            "background[SELECTED]", "black_bright",
            "background[ACTIVE]", "black_bright",
            "com.googlecode.lanterna.gui2.Button.foreground[SELECTED]", "cyan_bright",
            "com.googlecode.lanterna.gui2.Button.sgr", "",
            "com.googlecode.lanterna.gui2.Button.sgr[PRELIGHT]", "underline",
            "com.googlecode.lanterna.gui2.Button.background[INSENSITIVE]", "black_bright"
    );

    private Show(@NotNull String label, @NotNull Runnable action) {
        super(label, action);
        setTheme(THEME);
        setRenderer(new ShowRenderer());
    }

    /**
     * Create a new {@link Show} style button.
     *
     * @param label  The button text.
     * @param action The {@link Runnable} to {@link Runnable#run() run} when the button is selected.
     * @return The new button.
     */
    @NotNull
    public static Show of(@NotNull String label, @NotNull Runnable action) {
        return new Show(label, action);
    }

    /**
     * A custom renderer for {@link Show Show} buttons that sets the size correctly.
     */
    public static class ShowRenderer extends CustomRenderer {
        @Override
        public TerminalSize getPreferredSize(Button component) {
            return new TerminalSize(TerminalTextUtils.getColumnWidth(component.getLabel()) + 2, 1);
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, Button button) {
            super.drawComponent(graphics, button);

            // Modified to center the text with some padding
            graphics.putString(1, 0, button.getLabel());
        }
    }
}
