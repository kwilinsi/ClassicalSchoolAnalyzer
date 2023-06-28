package gui.components.buttons;

import com.googlecode.lanterna.graphics.ThemeDefinition;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

/**
 * This is an interface between {@link Button.FlatButtonRenderer} and other custom button renderers that allows
 * easily changing the location of the text in the {@link #drawComponent(TextGUIGraphics, Button)} method.
 */
public abstract class CustomRenderer extends Button.FlatButtonRenderer {
    /**
     * {@inheritDoc}
     * <p>
     * This is an exact copy of the {@link Button.FlatButtonRenderer#drawComponent(TextGUIGraphics, Button) super}
     * method, except that it doesn't actually draw the text. This should be overridden to add the text element.
     *
     * @param graphics Graphics object to use for drawing
     * @param button   Component to draw
     */
    @Override
    public void drawComponent(TextGUIGraphics graphics, Button button) {
        // Copied from the super class
        ThemeDefinition themeDefinition = button.getThemeDefinition();
        if (button.isFocused()) {
            graphics.applyThemeStyle(themeDefinition.getActive());
        } else {
            graphics.applyThemeStyle(themeDefinition.getInsensitive());
        }
        graphics.fill(' ');
        if (button.isFocused()) {
            graphics.applyThemeStyle(themeDefinition.getSelected());
        } else {
            graphics.applyThemeStyle(themeDefinition.getNormal());
        }
    }
}
