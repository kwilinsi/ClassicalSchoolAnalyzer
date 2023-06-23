package gui.utils;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.bundle.LanternaThemes;
import com.googlecode.lanterna.graphics.PropertyTheme;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.gui2.*;
import constructs.school.Attribute;
import constructs.school.School;
import org.apache.commons.text.WordUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import processing.schoolLists.matching.AttributeComparison;
import utils.Config;

import java.util.*;

public class GUIUtils {
    /**
     * Create a {@link Label} formatted to look like a proper header. It has the following attributes:
     * <ul>
     *     <li>{@link TextColor.ANSI#WHITE_BRIGHT WHITE_BRIGHT} foreground color
     *     <li>{@link LinearLayout.Alignment#Center Center} aligned horizontally
     *     <li>{@link SGR} style {@link SGR#BOLD BOLD}
     * </ul>
     *
     * @param text The text to display in the header.
     * @return The header label.
     */
    @NotNull
    public static Label header(@NotNull String text) {
        Label header = new Label(text);
        header.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        header.addStyle(SGR.BOLD);
        header.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Center));
        return header;
    }

    /**
     * Get a formatted {@link Label} with the given {@link Attribute Attribute} {@link Attribute#name() name}
     * followed by a colon. The label is colored {@link TextColor.ANSI#BLUE BLUE}.
     *
     * @param attribute The attribute to put in the label.
     * @return The newly created label.
     */
    @NotNull
    public static Label attributeLabel(@NotNull Attribute attribute) {
        return attributeLabel(attribute.name(), true);
    }

    /**
     * Formats the same as {@link #attributeLabel(Attribute)}, but accepts any text. Useful for the
     * {@link School#getId() id} pseudo-attribute.
     *
     * @param text      The text to put in the label.
     * @param withColon <code>True</code> to add a colon to the end of the text.
     * @return The newly created label.
     */
    public static Label attributeLabel(@NotNull String text, boolean withColon) {
        return new Label(withColon ? text + ":" : text)
                .setForegroundColor(TextColor.ANSI.BLUE);
    }

    /**
     * Get an attribute comparison {@link AttributeComparison.Level Level's}
     * {@link AttributeComparison.Level#abbreviation() abbreviation} as a formatted {@link Label}.
     *
     * @param level The level to use.
     * @return The newly created label.
     */
    public static Label attributeAbbreviationLabel(@NotNull AttributeComparison.Level level) {
        return new Label(String.valueOf(level.abbreviation()))
                .setForegroundColor(TextColor.ANSI.BLUE)
                .addStyle(SGR.BOLD);
    }

    /**
     * Create a {@link Label} formatted to look like a proper footer. It has the following attributes:
     * <ul>
     *     <li>{@link TextColor.ANSI#WHITE WHITE} foreground color
     *     <li>{@link TextColor.ANSI#BLACK BLACK} background color
     *     <li>{@link SGR} style {@link SGR#ITALIC ITALIC}
     * </ul>
     *
     * @param text The text to display in the footer.
     * @return The footer label.
     * @see #footer(String)
     */
    @NotNull
    public static Label footerLabel(@NotNull String text) {
        Label footer = new Label(text);
        footer.setForegroundColor(TextColor.ANSI.WHITE);
        footer.setBackgroundColor(TextColor.ANSI.BLACK);
        footer.addStyle(SGR.ITALIC);
        return footer;
    }

    /**
     * Create a comprehensive footer {@link Panel} for a window. It contains a
     * {@link #footerLabel(String) footer label}, and has a background color to differentiate it from the rest of the
     * window.
     *
     * @param text The text to display in the footer.
     * @return The footer panel.
     */
    @NotNull
    public static Panel footer(@NotNull String text) {
        Panel footer = new Panel();
        footer.setFillColorOverride(TextColor.ANSI.WHITE);
        footer.setLayoutManager(new LinearLayout(Direction.HORIZONTAL));
        footer.addComponent(footerLabel(text));
        return footer;
    }

    /**
     * Get a new {@link Theme} with the given background color. This method basically just exists because I couldn't
     * figure out how to get {@link com.googlecode.lanterna.graphics.SimpleTheme SimpleTheme} to do what I wanted.
     * It's pretty dubious.
     *
     * @param color The color to use.
     * @return The new theme.
     */
    public static Theme getThemeWithBackgroundColor(TextColor.ANSI color) {
        Properties properties = (Properties) DefaultThemeCopy.properties.clone();
        String name = color.name().toLowerCase();
        properties.forEach((key, value) -> {
            if (((String) key).contains("background") && "white".equals(value)) {
                properties.setProperty((String) key, name);
            }
        });

        return new PropertyTheme(properties);
    }

    /**
     * Get the {@link LanternaThemes#getDefaultTheme() default theme} but with the specified modifications.
     *
     * @param keysAndValues A list of keys and values to add to the default theme {@link DefaultThemeCopy#properties
     *                      properties}. This must be an even number of strings in the format
     *                      <code>"setTheme(key, value, key, value...)"</code>. None of these should be
     *                      <code>null</code>.
     * @return The new theme.
     * @throws IllegalArgumentException If an odd number of elements are given.
     */
    public static Theme getNewTheme(@NotNull String... keysAndValues) throws IllegalArgumentException {
        if (keysAndValues.length % 2 != 0)
            throw new IllegalArgumentException("Invalid number of keys and values: " + keysAndValues.length);

        Properties properties = (Properties) DefaultThemeCopy.properties.clone();
        for (int i = 0; i < keysAndValues.length; i += 2)
            properties.setProperty(keysAndValues[i], keysAndValues[i + 1]);

        return new PropertyTheme(properties);
    }

    /**
     * Add the specified number of {@link EmptySpace} components to the given {@link Panel}.
     *
     * @param panel The panel.
     * @param count The number of components to add.
     */
    public static void addEmptyComponents(@NotNull Panel panel, int count) {
        for (int i = 0; i < count; i++)
            panel.addComponent(new EmptySpace(panel.getTheme().getDefaultDefinition().getNormal().getBackground()));
    }

    /**
     * Create a {@link LinearLayout} panel containing a single label. The panel is filled with the given color and
     * set to expand horizontally to fill available space. It is specifically designed for use in a
     * {@link GridLayout} based panel of width 1. The header text is generated as a centered, {@link SGR#BOLD bold},
     * {@link TextColor.ANSI#WHITE_BRIGHT white_bright} label.
     *
     * @param text   The header text
     * @param color  The background color.
     * @param height The height of the panel. The header text is vertically centered within this height.
     * @return The new panel containing the header.
     */
    public static Panel createFilledHeader(@NotNull String text,
                                           @NotNull TextColor.ANSI color,
                                           int height) {
        // TODO this only seems to work if it's put in a GridLayout with vertical spacing 1. Fix that.
        Panel panel = new Panel()
                .setLayoutManager(new GridLayout(1)
                        .setLeftMarginSize(0)
                        .setRightMarginSize(0)
                )
                .setLayoutData(
                        GridLayout.createLayoutData(
                                GridLayout.Alignment.FILL,
                                GridLayout.Alignment.FILL,
                                true,
                                false,
                                1,
                                height
                        )
                )
                .addComponent(new Label(text)
                        .setForegroundColor(TextColor.ANSI.WHITE_BRIGHT)
                        .addStyle(SGR.BOLD)
                        .setLayoutData(
                                GridLayout.createLayoutData(
                                        GridLayout.Alignment.CENTER,
                                        GridLayout.Alignment.CENTER,
                                        true,
                                        true
                                )
                        )
                );

        panel.setTheme(getThemeWithBackgroundColor(color));
        return panel;
    }

    /**
     * Given some text for a {@link Label}, wrap it using {@link WordUtils#wrap(String, int, String, boolean)}.
     * <p>
     * The wrap length is based on {@link Config#GUI_POPUP_TEXT_WRAP_LENGTH GUI_POPUP_TEXT_WRAP_LENGTH}. This will
     * force wrap long words. The newline character is <code>"\n"</code>.
     *
     * @param text The text to wrap.
     * @return The formatted text.
     */
    @NotNull
    public static String wrapLabelText(@Nullable String text) {
        return WordUtils.wrap(
                text == null ? "" : text, Config.GUI_POPUP_TEXT_WRAP_LENGTH.getInt(), "\n", true
        );
    }

    /**
     * Remove the last child component from a {@link Container}, if it has any children.
     *
     * @param container The container to modify.
     * @see #replaceLastComponent(Panel, Component)
     */
    public static void removeLastComponent(@NotNull Container container) {
        List<Component> children = container.getChildrenList();
        if (children.size() > 0)
            container.removeComponent(children.get(children.size() - 1));
    }

    /**
     * Remove the last {@link Component} from a {@link Panel}, and replace it with a new one. If the panel is empty,
     * this just adds the new component.
     *
     * @param panel     The panel to modify.
     * @param component The new component to add.
     * @see #removeLastComponent(Container)
     */
    public static void replaceLastComponent(@NotNull Panel panel, @NotNull Component component) {
        removeLastComponent(panel);
        panel.addComponent(component);
    }
}
