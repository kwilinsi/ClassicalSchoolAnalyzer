package gui.components.buttons;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import org.jetbrains.annotations.NotNull;

/**
 * This button only draws one character. That character is highlighted with a different color to make it stand out.
 */
public class SymbolButton extends Button {
    private SymbolButton(char symbol, @NotNull Runnable action) {
        super(String.valueOf(symbol), action);
        setRenderer(new SymbolRenderer());
    }

    /**
     * Create a new {@link SymbolButton} style button.
     *
     * @param symbol The button symbol.
     * @param action The {@link Runnable} to {@link Runnable#run() run} when the button is selected.
     * @return The new button.
     */
    public static SymbolButton of(char symbol, @NotNull Runnable action) {
        return new SymbolButton(symbol, action);
    }

    private static class SymbolRenderer extends CustomRenderer {
        @Override
        public TerminalSize getPreferredSize(Button component) {
            return new TerminalSize(3, 1);
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, Button button) {
            super.drawComponent(graphics, button);

            if (button.isFocused())
                graphics.putString(0, 0, "[" + button.getLabel() + "]");
            else {
                graphics.putString(0, 0, "[");
                graphics.putString(2, 0, "]");

                if (button.isEnabled())
                    graphics.setForegroundColor(TextColor.ANSI.BLUE).enableModifiers(SGR.BOLD);
                else
                    graphics.setForegroundColor(TextColor.ANSI.RED).disableModifiers(SGR.BOLD);

                graphics.putString(1, 0, button.getLabel());
            }
        }
    }
}
