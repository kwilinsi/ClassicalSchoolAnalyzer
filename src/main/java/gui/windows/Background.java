package gui.windows;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.gui2.*;
import gui.utils.GUILogAppender;
import gui.utils.GUIUtils;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import utils.Utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * This is the background window added at the root of the {@link main.Main#GUI GUI}. It displays the title, the
 * {@link #logPanel log}, and a footer.
 */
public class Background extends MyBaseWindow {
    /**
     * This is the format used for displaying the timestamps of log entries.
     *
     * @see #loggingEventToPanel(ILoggingEvent)
     */
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("mm:ss");

    /**
     * This is the fixed length of the thread name in each log entry.
     *
     * @see #loggingEventToPanel(ILoggingEvent)
     */
    private static final int THREAD_NAME_LENGTH = 14;

    /**
     * This is the fixed length of the class name in each log entry.
     *
     * @see #loggingEventToPanel(ILoggingEvent)
     */
    private static final int CLASS_NAME_LENGTH = 12;

    /**
     * This panel displays a list of the most recent log messages.
     */
    @NotNull
    private final Panel logPanel;

    public Background() {
        super();

        setHints(List.of(Hint.FULL_SCREEN));
        setTheme(new SimpleTheme(TextColor.ANSI.BLACK, TextColor.ANSI.BLACK_BRIGHT));

        // Create the log panel
        logPanel = new Panel(new LinearLayout(Direction.VERTICAL));

        // Set the main component of this window to show a header, the log, and a footer
        setComponent(new Panel(new LinearLayout(Direction.VERTICAL).setSpacing(1))
                .addComponent(GUIUtils.header("WELCOME TO CLASSICAL SCHOOL ANALYZER!"))
                .addComponent(logPanel)
                .addComponent(GUIUtils.footer("Press Ctrl + X to exit"))
        );

        // Add a listener to update the log display if the window is resized. This is called by the GUI thread naturally
        addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onResized(Window window, TerminalSize oldSize, TerminalSize newSize) {
                // Update spacing to force the footer to the bottom
                logPanel.setPreferredSize(new TerminalSize(newSize.getColumns(), Math.max(0, newSize.getRows() - 4)));
            }
        });

        // Tell the log appender to notify this window when anything changes
        GUILogAppender.setOnUpdate(this::updateLog);
    }

    /**
     * Get the number of rows in the log. This changes whenever the window is
     * {@link WindowListenerAdapter#onResized(Window, TerminalSize, TerminalSize) resized}.
     *
     * @return The number of rows in the log.
     */
    public int getLogHeight() {
        return logPanel.getPreferredSize().getRows();
    }

    /**
     * Whenever a new log message is received or the window is resized, call this method to update the messages that
     * display in the {@link #logPanel}.
     */
    public void updateLog(@Nullable ILoggingEvent event) {
        if (event == null) return;

        Main.GUI.run(() -> {
            // If the screen is full, remove the first log entry
            if (logPanel.getChildCount() == getLogHeight())
                logPanel.removeComponent(logPanel.getChildrenList().get(0));
            logPanel.addComponent(loggingEventToPanel(event));
        });
    }

    /**
     * Take a {@link ILoggingEvent log event} and create from it a single-line {@link Panel} containing a nicely
     * formatted log message.
     *
     * @param event The log event to format.
     * @return The formatted log message.
     */
    @NotNull
    private static Panel loggingEventToPanel(@NotNull ILoggingEvent event) {
        // Get the timestamp date
        LocalDateTime lt = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimeStamp()), ZoneId.systemDefault());

        // Get the logger name (e.g. the class name)
        String[] name = event.getLoggerName().split("\\.");

        // Get the log level
        String level = event.getLevel().toString();
        Label levelLbl = new Label(Utils.padTrimString(level, 5, false)).addStyle(SGR.BOLD);
        switch (level) {
            case "ERROR" -> levelLbl.setForegroundColor(TextColor.ANSI.RED);
            case "WARN" -> levelLbl.setForegroundColor(TextColor.ANSI.YELLOW);
            case "INFO" -> levelLbl.setForegroundColor(TextColor.ANSI.CYAN);
            case "DEBUG" -> levelLbl.setForegroundColor(TextColor.ANSI.GREEN);
            case "TRACE" -> levelLbl.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        }

        // Return the completed panel
        return new Panel(new LinearLayout(Direction.HORIZONTAL))
                .addComponent(new Label(lt.format(LOG_TIME_FORMAT)).setForegroundColor(TextColor.ANSI.WHITE))
                .addComponent(new Label(Utils.padTrimString(event.getThreadName(), THREAD_NAME_LENGTH, false))
                        .setForegroundColor(TextColor.ANSI.CYAN_BRIGHT))
                .addComponent(new Label(Utils.padTrimString(name[name.length - 1], CLASS_NAME_LENGTH, false))
                        .setForegroundColor(TextColor.ANSI.GREEN_BRIGHT)
                        .addStyle(SGR.BOLD))
                .addComponent(levelLbl)
                .addComponent(new Label(event.getFormattedMessage()).setForegroundColor(TextColor.ANSI.WHITE_BRIGHT));
    }
}
