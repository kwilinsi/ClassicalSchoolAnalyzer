package gui.utils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import constructs.school.Attribute;
import constructs.District;
import constructs.school.MatchLevel;
import constructs.school.School;
import org.jetbrains.annotations.NotNull;
import utils.Utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class GUIUtils {
    /**
     * This is the format used for displaying the timestamps of log entries.
     *
     * @see #logEntry(ILoggingEvent)
     */
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("mm:ss");

    /**
     * Create a {@link Label} formatted to look like a proper header. It has the following attributes:
     * <ul>
     *     <li>{@link TextColor.ANSI#WHITE_BRIGHT WHITE_BRIGHT} foreground color
     *     <li>{@link LinearLayout.Alignment#Center Center} aligned horizontally
     *     <li>{@link SGR} style {@link SGR#BOLD BOLD}
     * </ul>
     *
     * @param text The text to display in the header.
     *
     * @return The header label.
     * @see #warningHeader(String)
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
     * This is identical to {@link #header(String)}, except that the text is {@link TextColor.ANSI#RED RED} to indicate
     * a warning message.
     *
     * @param text The text to display in the warning header.
     *
     * @return The warning header label.
     */
    @NotNull
    public static Label warningHeader(@NotNull String text) {
        Label header = new Label(text);
        header.setForegroundColor(TextColor.ANSI.RED);
        header.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Center));
        return header;
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
     *
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
     *
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
     * Take a {@link ILoggingEvent log event} and create from it a single-line {@link Panel} containing a nicely
     * formatted log message.
     *
     * @param event The log event to format.
     *
     * @return The formatted log message.
     */
    @NotNull
    public static Panel logEntry(@NotNull ILoggingEvent event) {
        Panel logEntry = new Panel();
        logEntry.setLayoutManager(new LinearLayout(Direction.HORIZONTAL));

        // Add the timestamp
        long timeStamp = event.getTimeStamp();
        Instant instant = Instant.ofEpochMilli(timeStamp);
        LocalDateTime lt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        Label time = new Label(lt.format(LOG_TIME_FORMAT));
        time.setForegroundColor(TextColor.ANSI.WHITE);
        logEntry.addComponent(time);

        // Add the thread
        Label thread = new Label(Utils.padTrimString(event.getThreadName(), 14, false));
        thread.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        logEntry.addComponent(thread);

        // Add the logger name (e.g. the class name)
        String[] name = event.getLoggerName().split("\\.");
        Label logger = new Label(Utils.padTrimString(name[name.length - 1], 12, false));
        logger.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        logger.addStyle(SGR.BOLD);
        logEntry.addComponent(logger);

        // Add the log level
        String level = event.getLevel().toString();
        Label levelLbl = new Label(Utils.padTrimString(level, 5, false));
        switch (level) {
            case "ERROR" -> levelLbl.setForegroundColor(TextColor.ANSI.RED);
            case "WARN" -> levelLbl.setForegroundColor(TextColor.ANSI.YELLOW);
            case "INFO" -> levelLbl.setForegroundColor(TextColor.ANSI.CYAN);
            case "DEBUG" -> levelLbl.setForegroundColor(TextColor.ANSI.GREEN);
            case "TRACE" -> levelLbl.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        }
        levelLbl.addStyle(SGR.BOLD);
        logEntry.addComponent(levelLbl);

        // Add the message
        Label message = new Label(event.getFormattedMessage());
        message.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        logEntry.addComponent(message);

        // Return the completed panel
        return logEntry;
    }

    /**
     * Generate a nicely formatted {@link Panel} containing a list of {@link Attribute Attributes} for a given
     * {@link School}.
     * <p>
     * The panel is formatted as a {@link GridLayout} containing the given attributes and their corresponding values for
     * the school. The name of each attribute is prefixed by its corresponding {@link MatchLevel}
     * {@link MatchLevel#getPrefix() prefix}.
     * <p>
     * The <code>attributes</code> list is automatically sorted according to the natural order of attribute enums in the
     * {@link Attribute} class. If <code>includeId</code> is true, indicating that the school's
     * {@link School#getId() id} should be included, it will be placed first in the list.
     *
     * @param school     The school from which to retrieve the attribute values.
     * @param attributes The list of attributes to include in the panel, and their corresponding match state.
     * @param includeId  Whether to include the school's {@link School#getId() id} as a pseudo-attribute in the panel.
     *
     * @return A nicely formatted panel displaying some of the school's attributes.
     */
    @NotNull
    public static Panel formatSchoolAttributes(@NotNull School school,
                                               Map<Attribute, MatchLevel> attributes,
                                               boolean includeId) {
        // Create a copy of the attributes list, sorted according to their natural order
        LinkedHashMap<Attribute, MatchLevel> sortedAttributes = new LinkedHashMap<>();
        attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEachOrdered(e -> sortedAttributes.put(e.getKey(), e.getValue()));

        // Initialize the panel with two columns: attributes and values
        Panel panel = new Panel(new GridLayout(2));

        // If including the id, add it first
        if (includeId) {
            panel.addComponent(new Label("id:").setForegroundColor(TextColor.ANSI.BLUE));
            panel.addComponent(new Label(String.valueOf(school.getId())));
        }

        // Add each attribute
        for (Attribute a : sortedAttributes.keySet()) {
            panel.addComponent(
                    new Label(sortedAttributes.get(a).getPrefix() + a.name() + ":")
                            .setForegroundColor(TextColor.ANSI.BLUE)
            );
            panel.addComponent(new Label(String.valueOf(school.get(a))));
        }

        return panel;
    }

    /**
     * Create a nicely formatted {@link Panel} representing a {@link District}. The panel is formatted as a
     * {@link GridLayout}. It contains the district's {@link District#getId() id}, {@link District#getName() name}, and
     * {@link District#getWebsiteURL() website URL}.
     *
     * @param district The district to format.
     *
     * @return A formatted panel with the district's attributes.
     * @see #formatSchoolAttributes(School, Map, boolean)
     */
    @NotNull
    public static Panel formatDistrictAttributes(@NotNull District district) {
        // Initialize the panel with two columns: attributes and values
        Panel panel = new Panel(new GridLayout(2));

        // Add the id
        panel.addComponent(new Label("id:").setForegroundColor(TextColor.ANSI.BLUE));
        panel.addComponent(new Label(String.valueOf(district.getId())));

        // Add the name
        panel.addComponent(new Label("name:").setForegroundColor(TextColor.ANSI.BLUE));
        panel.addComponent(new Label(district.getName()));

        // Add the website URL
        panel.addComponent(new Label("id:").setForegroundColor(TextColor.ANSI.BLUE));
        panel.addComponent(new Label(district.getWebsiteURL()));

        return panel;
    }
}
