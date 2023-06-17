package gui.windows.prompt.schoolMatch;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.graphics.ThemeDefinition;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import constructs.school.Attribute;
import gui.utils.GUIUtils;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.URLUtils;
import utils.Utils;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * This is a collection of specialty {@link Button Buttons} for use with {@link SchoolMatchDisplay}.
 */
public class SpecializedButtons {
    /**
     * The {@link Theme} for {@link Show Show} buttons.
     */
    @NotNull
    private static final Theme SHOW_THEME = GUIUtils.getNewTheme(
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

    /**
     * The {@link Theme} for {@link PageArrow PageArrow} buttons.
     */
    @NotNull
    private static final Theme PAGE_ARROW_THEME = GUIUtils.getNewTheme(
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

    /**
     * This is a particular type of {@link Button} that uses the {@link #SHOW_THEME}.
     */
    public static class Show extends Button {
        private Show(@NotNull String label, @NotNull Runnable action) {
            super(label, action);
            setTheme(SHOW_THEME);
            setRenderer(new ShowRenderer());
        }

        /**
         * Create a new {@link Show Show} style button.
         *
         * @param label  The button text.
         * @param action The {@link Runnable} to {@link Runnable#run() run} when the button is selected.
         * @return The new button.
         */
        public static Show of(@NotNull String label, @NotNull Runnable action) {
            return new Show(label, action);
        }
    }

    /**
     * This is a particular type of {@link Button} whose label is either a left or right unicode arrow. It's designed
     * for switching pages. It uses the {@link #PAGE_ARROW_THEME}.
     */
    public static class PageArrow extends Button {
        private PageArrow(boolean isLeft, @NotNull Panel panel, @NotNull Runnable action) {
            //noinspection UnnecessaryUnicodeEscape
            super(isLeft ? "\u25C4" : "\u25BA", action);
            setTheme(PAGE_ARROW_THEME);
            setRenderer(new PageArrowRenderer(panel));
        }

        /**
         * Create a new {@link PageArrow} style button.
         *
         * @param isLeft <code>True</code> to use the Unicode left arrow for the button text; <code>false</code> to
         *               use the right arrow.
         * @param panel  The {@link PageArrowRenderer#panel panel} whose height should be copied in setting the
         *               preferred height of this panel. This must <i>not</i> be any parent panel of this button.
         * @param action The {@link Runnable} to {@link Runnable#run() run} when the button is selected.
         * @return The new button.
         */
        public static PageArrow of(boolean isLeft, @NotNull Panel panel, @NotNull Runnable action) {
            return new PageArrow(isLeft, panel, action);
        }
    }

    /**
     * This is a particular type of {@link Button} that displays an {@link #open() openable} URL.
     */
    public static class Link extends Button {
        private static final Logger logger = LoggerFactory.getLogger(Link.class);

        /**
         * This is the URL that is {@link #open() opened} when the user selects the link.
         */
        @Nullable
        private URI uri;

        private Link(@Nullable String label) {
            super(label == null ? "" : label);

            setRenderer(new LinkRenderer());
            setUrl(label);
            addListener(button -> open());
        }

        /**
         * Change the label and {@link #uri} associated with this button. This may also change the theme.
         *
         * @param text The new URL text.
         */
        public void setUrl(@Nullable String text) {
            setLabel(text == null ? "" : text);

            if (Attribute.website_url.isEffectivelyNull(text)) {
                uri = null;
                setEnabled(false);
            } else {
                uri = makeUri(text);
                setEnabled(true);
            }
        }

        /**
         * Attempt to {@link Desktop#browse(URI) open} the link's {@link #uri URL}.
         * <p>
         * Opening the URL may fail for a variety of reasons. If it fails, a popup explaining the failure is
         * displayed. It may fail because:
         * <ul>
         *     <li>The URL is <code>null</code>, indicating that it's not a valid URL. Note that URLs which are
         *     {@link Attribute#isEffectivelyNull(Object) effectively null} are {@link #setEnabled(boolean) disabled}
         *     to begin with, preventing this method from ever being called on them.
         *     <li>Browsing URLs isn't supported on {@link Desktop#getDesktop() this} {@link Desktop}. This triggers
         *     a warning {@link #logger log} message.
         *     <li>Browsing is supported but fails for some reason. This triggers an error log message.
         * </ul>
         * If opening the URL is successful, a debug message is logged.
         */
        public void open() {
            if (uri == null) {
                errorOpening("Cannot parse the URL to open it. Is it malformed?");
                return;
            }

            Desktop desktop;
            try {
                desktop = Desktop.getDesktop();
            } catch (UnsupportedOperationException e) {
                errorOpening("Failed to load the desktop environment:%n%n%s%n%n%s",
                        e.getLocalizedMessage(),
                        Utils.getStackTraceAsString(e)
                );
                return;
            }

            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    desktop.browse(uri);
                    logger.debug("Successfully opened URL " + uri + " from Link button");
                } catch (IOException e) {
                    logger.error("Failed to open link " + uri, e);
                    errorOpening(
                            "Failed to open the URL %s:%n%n%s%n%n%s",
                            e.getLocalizedMessage(),
                            Utils.getStackTraceAsString(e)
                    );
                }
            } else {
                logger.warn("BROWSE action not supported on this desktop environment.");
                errorOpening("Your desktop environment doesn't support browsing URLs.");
            }
        }

        /**
         * This is called when the user tried to {@link #open() open} the URL but couldn't for some reason. It
         * displays an appropriate error message in a popup.
         *
         * @param message The message to display.
         */
        public void errorOpening(@NotNull String message) {
            MessageDialog.showMessageDialog(
                    Main.GUI.getWindowGUI(),
                    "Error",
                    GUIUtils.wrapLabelText(message),
                    MessageDialogButton.OK
            );
        }

        /**
         * Wrapper for {@link #errorOpening(String)} that allows passing a format string with arguments to
         * {@link String#format(String, Object...)}.
         *
         * @param message The format string to display.
         * @param args    The arguments.
         */
        public void errorOpening(@NotNull String message, @Nullable Object... args) {
            errorOpening(String.format(message, args));
        }

        /**
         * Create a new link, whose label is the URL to open when this link is selected.
         *
         * @param label The URL.
         * @return The new button.
         */
        public static Link of(@Nullable String label) {
            return new Link(label);
        }

        /**
         * {@link URLUtils#createURL(String) Create} a {@link URI} from the given text. If this fails for any reason,
         * an error message is {@link #logger logged} at the debug level, and <code>null</code> is returned.
         *
         * @param text The text to convert to a URI.
         * @return The converted text, or <code>null</code> if it cannot be converted.
         */
        private static URI makeUri(@Nullable String text) {
            if (text == null) return null;

            URL url = URLUtils.createURL(text);
            if (url == null) {
                logger.debug("Cannot create a URL from {}; link button reverting to default", text);
                return null;
            }

            try {
                return url.toURI();
            } catch (URISyntaxException e) {
                logger.debug(String.format(
                        "Cannot get URI from URL %s parsed from %s; link button reverting to default",
                        url, text), e);
                return null;
            }
        }
    }

    /**
     * This is an interface between {@link Button.FlatButtonRenderer} and other custom button renderers that allows
     * easily changing the location of the text in the {@link #drawComponent(TextGUIGraphics, Button)} method.
     */
    public static abstract class CustomRenderer extends Button.FlatButtonRenderer {
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

    /**
     * A custom renderer for {@link Link Link} buttons. For any other button type, this defaults to a
     * {@link com.googlecode.lanterna.gui2.Button.FlatButtonRenderer FlatButtonRenderer}.
     */
    public static class LinkRenderer extends Button.FlatButtonRenderer {
        @Override
        public void drawComponent(TextGUIGraphics graphics, Button button) {
            // If it's not a link button, revert to the default
            if (!(button instanceof Link link)) {
                super.drawComponent(graphics, button);
                return;
            }

            // Clear out the space with the default background color
            graphics.applyThemeStyle(link.getThemeDefinition().getInsensitive());
            graphics.fill(' ');

            // If it has an empty label, don't draw anything
            if (link.getLabel() == null || link.getLabel().isBlank())
                return;

            // Add appropriate styles and coloring based on focus
            if (link.isFocused()) {
                graphics.setBackgroundColor(graphics.getBackgroundColor() == TextColor.ANSI.BLACK_BRIGHT ?
                        TextColor.ANSI.WHITE : TextColor.ANSI.BLACK_BRIGHT
                );
                graphics.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
                graphics.enableModifiers(SGR.UNDERLINE, SGR.BOLD);
            } else {
                graphics.enableModifiers(SGR.UNDERLINE);
            }

            // Color it red if it's invalid; otherwise, leave it at the default
            if (link.uri == null)
                graphics.setForegroundColor(TextColor.ANSI.RED);

            // Draw the label
            graphics.putString(0, 0, link.getLabel());
        }
    }
}
