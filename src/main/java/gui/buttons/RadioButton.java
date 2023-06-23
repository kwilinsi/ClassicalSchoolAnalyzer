package gui.buttons;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

public class RadioButton extends Button {
    private static final ButtonRenderer RENDERER = new RadioButtonRenderer();

    /**
     * The appearance of the button when it's not selected.
     */
    private static final String NOT_SELECTED = "[ ]";

    /**
     * The appearance of the button when it's selected.
     */
    private static final String SELECTED = "[X]";

    /**
     * Whether this button is currently selected.
     */
    private boolean isSelected = false;

    public RadioButton(Runnable runnable) {
        super(NOT_SELECTED, runnable);
        setRenderer(RENDERER);
    }

    /**
     * Set whether the button is {@link #isSelected selected}. If this changes the button's state, it is
     * automatically {@link #invalidate() invalidated}.
     *
     * @param selected The new selection state for this button.
     */
    public void setSelected(boolean selected) {
        if (isSelected != selected) {
            isSelected = selected;
            invalidate();
        }
    }

    /**
     * Get whether button is {@link #isSelected selected}.
     *
     * @return <code>True</code> if and only if it's selected.
     */
    public boolean isSelected() {
        return isSelected;
    }

    /**
     * A custom renderer for {@link RadioButton RadioButtons}.
     */
    private static class RadioButtonRenderer extends FlatButtonRenderer {
        @Override
        public void drawComponent(TextGUIGraphics graphics, Button button) {
            if (button instanceof RadioButton rb) {
                graphics.applyThemeStyle(rb.getThemeDefinition().getInsensitive());

                if (rb.isFocused()) {
                    graphics.setBackgroundColor(TextColor.ANSI.BLUE_BRIGHT)
                            .setForegroundColor(TextColor.ANSI.WHITE_BRIGHT)
                            .enableModifiers(SGR.BOLD);
                }

                graphics.putString(0, 0, rb.isSelected ? SELECTED : NOT_SELECTED);

            } else {
                super.drawComponent(graphics, button);
            }
        }
    }
}
