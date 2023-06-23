package gui.buttons;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import constructs.school.Attribute;
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
 * This is a particular type of {@link Button} that displays an {@link #open() openable} URL.
 */
public class Link extends Button {
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
     * Create a new link, whose label is the URL to open when this link is selected.
     *
     * @param label The URL.
     * @return The new button.
     */
    @NotNull
    public static Link of(@Nullable String label) {
        return new Link(label);
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
        Main.GUI.dialog("Error", message);
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

    /**
     * A custom renderer for {@link Link Link} buttons. For any other button type, this defaults to a
     * {@link FlatButtonRenderer FlatButtonRenderer}.
     */
    public static class LinkRenderer extends FlatButtonRenderer {
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
