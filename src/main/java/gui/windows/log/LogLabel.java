package gui.windows.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.Panel;
import gui.utils.GUIUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LogLabel {
    /**
     * This is the format used for timestamps in the {@link #time} label.
     */
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("mm:ss");

    /**
     * This is the fixed length of the {@link #thread} label.
     */
    private static final int THREAD_NAME_LENGTH = 14;

    /**
     * This is the fixed length of the {@link #className} label.
     */
    private static final int CLASS_NAME_LENGTH = 12;

    /**
     * The maximum number of lines to display for any given log message. See
     * {@link GUIUtils#trimLineCount(String, int) GUIUtils.trimLineCount()}.
     */
    private static final int MAX_LINES_PER_MESSAGE = 5;

    /**
     * The number of columns in the GUI display reserved for things other than the {@link #message} label. This is
     * the sum of the following:
     * <ul>
     *     <li><b>5</b> — The {@link #time} column.
     *     <li><b>{@value #THREAD_NAME_LENGTH}</b> — The {@link #thread} column.
     *     <li><b>{@value #CLASS_NAME_LENGTH}</b> — The {@link #className} column.
     *     <li><b>5</b> — The {@link #level} column.
     *     <li><b>4</b> — Spacing between the columns.
     * </ul>
     */
    private static final int RESERVED_COLUMNS = 5 + THREAD_NAME_LENGTH + CLASS_NAME_LENGTH + 5 + 4;

    /**
     * The label displaying the log timestamp.
     */
    @NotNull
    private final Label time;

    /**
     * The label displaying the thread that logged the message.
     */
    @NotNull
    private final Label thread;

    /**
     * The label displaying the name of the class that logged the message.
     */
    @NotNull
    private final Label className;

    /**
     * The label displaying the log level (<code>INFO</code>, <code>WARN</code>, etc).
     */
    @NotNull
    private final Label level;

    /**
     * The label displaying the log message, which is stored in the {@link #messageText}.
     */
    @NotNull
    private final Label message;

    /**
     * The text displayed in the {@link #message} label.
     */
    @NotNull
    private final String messageText;

    @SuppressWarnings("UnnecessaryUnicodeEscape")
    private LogLabel(@NotNull ILoggingEvent event, int currentColumns) {
        // Set the timestamp
        time = new Label(LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimeStamp()), ZoneId.systemDefault())
                .format(LOG_TIME_FORMAT)).setForegroundColor(TextColor.ANSI.WHITE);

        // Set the thread
        thread = new Label(StringUtils.abbreviate(event.getThreadName(), "\u2026", THREAD_NAME_LENGTH))
                .setForegroundColor(TextColor.ANSI.CYAN_BRIGHT)
                .addStyle(SGR.BOLD)
                .setPreferredSize(new TerminalSize(THREAD_NAME_LENGTH, 1));

        // Set the class name
        String[] name = event.getLoggerName().split("\\.");
        className = new Label(StringUtils.abbreviate(name[name.length - 1], "\u2026", CLASS_NAME_LENGTH))
                .setForegroundColor(TextColor.ANSI.GREEN_BRIGHT)
                .addStyle(SGR.BOLD)
                .setPreferredSize(new TerminalSize(CLASS_NAME_LENGTH, 1));

        // Set the level
        level = new Label(event.getLevel().toString())
                .addStyle(SGR.BOLD)
                .setPreferredSize(new TerminalSize(5, 1));

        switch (event.getLevel().toString()) {
            case "ERROR" -> level.setForegroundColor(TextColor.ANSI.RED);
            case "WARN" -> level.setForegroundColor(TextColor.ANSI.YELLOW);
            case "INFO" -> level.setForegroundColor(TextColor.ANSI.CYAN);
            case "DEBUG" -> level.setForegroundColor(TextColor.ANSI.GREEN);
            case "TRACE" -> level.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        }

        // Set the message
        messageText = GUIUtils.trimLineCount(event.getFormattedMessage(), MAX_LINES_PER_MESSAGE);
        message = new Label("").setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        updateMessage(currentColumns);
    }

    /**
     * Initialize a new set of log labels from the given logging event.
     *
     * @param event          The log event.
     * @param currentColumns The current number of columns available across the GUI for log messages.
     */
    public static LogLabel of(@NotNull ILoggingEvent event, int currentColumns) {
        return new LogLabel(event, currentColumns);
    }

    /**
     * Update the alignment and wrapping of the {@link #messageText} in the {@link #message} label to reflect the new
     * GUI size.
     *
     * @param columns The total number of GUI columns available (some of which are {@link #RESERVED_COLUMNS reserved}).
     */
    void updateMessage(int columns) {
        message.setText(GUIUtils.trimLineCount(
                WordUtils.wrap(messageText, columns - RESERVED_COLUMNS, "\n", true),
                MAX_LINES_PER_MESSAGE
        ));
    }

    /**
     * Add each of the labels to the given panel, presumably a {@link com.googlecode.lanterna.gui2.GridLayout
     * GridLayout} with 5 columns. The labels are added in the following order:
     * <ol>
     *     <li>{@link #time}
     *     <li>{@link #thread}
     *     <li>{@link #className}
     *     <li>{@link #level}
     *     <li>{@link #message}
     * </ol>
     *
     * @param panel The panel to which to add the labels.
     * @return Itself, for chaining.
     */
    @NotNull
    LogLabel addTo(@NotNull Panel panel) {
        panel.addComponent(time)
                .addComponent(thread)
                .addComponent(className)
                .addComponent(level)
                .addComponent(message);
        return this;
    }

    /**
     * Remove each of the labels from the given panel.
     *
     * @param panel The panel from which to remove the labels.
     */
    void removeFrom(@NotNull Panel panel) {
        panel.removeComponent(time);
        panel.removeComponent(thread);
        panel.removeComponent(className);
        panel.removeComponent(level);
        panel.removeComponent(message);
    }
}
