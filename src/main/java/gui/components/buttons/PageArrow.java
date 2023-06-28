package gui.components.buttons;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import gui.utils.GUIUtils;
import org.jetbrains.annotations.NotNull;

/**
 * This is a particular type of {@link Button} whose label is either a left or right unicode arrow. It's designed
 * for switching pages. It uses the page arrow {@link #THEME}.
 */
public class PageArrow extends Button {
    /**
     * The {@link Theme} for {@link PageArrow} buttons.
     */
    @NotNull
    private static final Theme THEME = GUIUtils.getNewTheme(
            "foreground", "white_bright",
            "background", "cyan",
            "sgr", "bold",
            "foreground[SELECTED]", "white_bright",
            "background[SELECTED]", "cyan_bright",
            "sgr[SELECTED]", "bold",
            "background[ACTIVE]", "cyan_bright",
            "com.googlecode.lanterna.gui2.Button.background[PRELIGHT]", "cyan",
            "com.googlecode.lanterna.gui2.Button.background[INSENSITIVE]", "cyan"
    );

    private PageArrow(boolean isLeft, @NotNull Panel panel, @NotNull Runnable action) {
        //noinspection UnnecessaryUnicodeEscape
        super(isLeft ? "\u25C4" : "\u25BA", action);
        setTheme(THEME);
        setRenderer(new PageArrowRenderer(panel));
    }

    /**
     * Create a new {@link PageArrow} style button.
     *
     * @param isLeft <code>True</code> to use the Unicode left arrow for the button text; <code>false</code> to
     *               use the right arrow.
     * @param panel  The {@link PageArrowRenderer#panel panel} whose height should be copied in
     *               setting the
     *               preferred height of this panel. This must <i>not</i> be any parent panel of this button.
     * @param action The {@link Runnable} to {@link Runnable#run() run} when the button is selected.
     * @return The new button.
     */
    @NotNull
    public static PageArrow of(boolean isLeft, @NotNull Panel panel, @NotNull Runnable action) {
        return new PageArrow(isLeft, panel, action);
    }

    /**
     * A custom renderer for {@link PageArrow PageArrow} buttons that sets the size correctly.
     */
    public static class PageArrowRenderer extends CustomRenderer {
        /**
         * A {@link Panel} whose preferred {@link Panel#getPreferredSize() height} should be copied in setting the
         * {@link #getPreferredSize(Button) preferred} height of this button.
         */
        @NotNull
        private final Panel panel;

        public PageArrowRenderer(@NotNull Panel panel) {
            this.panel = panel;
        }

        @Override
        public TerminalSize getPreferredSize(Button component) {
            return new TerminalSize(3, panel.getPreferredSize().getRows());
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, Button button) {
            super.drawComponent(graphics, button);

            // Modified to center the text
            TerminalSize size = graphics.getSize();
            graphics.putString(half(size.getColumns()), half(size.getRows()), button.getLabel());
        }

        /**
         * Take some window size, x, and divide it in half to find the index of the best midpoint visually.
         *
         * @param x The size.
         * @return The halfway point.
         */
        private static int half(int x) {
            return (int) ((x - 0.5) / 2);
        }
    }
}
