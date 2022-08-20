package gui.windows;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.gui2.*;
import gui.utils.GUILogAppender;
import gui.utils.GUIUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HomeScreen extends MyBaseWindow {
    private final Panel logPanel;

    public HomeScreen() {
        super();

        setHints(List.of(Hint.FULL_SCREEN));

        setTheme(new SimpleTheme(TextColor.ANSI.BLACK, TextColor.ANSI.BLACK_BRIGHT));

        // Create a new panel that will contain components, and set it as the main component of this window
        Panel mainPanel = new Panel(new LinearLayout(Direction.VERTICAL).setSpacing(1));

        // Add a header to the panel. Use custom LayoutData to horizontally center it.
        Label header = GUIUtils.header("WELCOME TO CLASSICAL SCHOOL ANALYZER!");
        mainPanel.addComponent(header);

        // Create the log panel
        logPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        mainPanel.addComponent(logPanel);

        // Add a footer to the panel
        Panel footer = GUIUtils.footer("Press Ctrl + X to exit");
        mainPanel.addComponent(footer);

        setComponent(mainPanel);

        addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onResized(Window window, TerminalSize oldSize, TerminalSize newSize) {
                // Update spacing to force the footer to the bottom
                logPanel.setPreferredSize(new TerminalSize(newSize.getColumns(), Math.max(0, newSize.getRows() - 4)));
                updateLog();
            }
        });
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
     * Populate the {@link #logPanel} with a list of log entries.
     *
     * @param entries The list of entries to add.
     */
    public void setLogEntries(@NotNull List<ILoggingEvent> entries) {
        logPanel.removeAllComponents();
        for (ILoggingEvent entry : entries)
            logPanel.addComponent(GUIUtils.logEntry(entry));

        // TODO make the thread text narrower and the class name wider in the log
    }

    /**
     * Whenever a new log message is received or the window is resized, call this method to update the messages that
     * display in the {@link #logPanel}.
     */
    public void updateLog() {
        setLogEntries(GUILogAppender.getLastEntries(getLogHeight()));
    }
}