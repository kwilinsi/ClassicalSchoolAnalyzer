package gui.windows.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.gui2.*;
import gui.utils.GUILogAppender;
import gui.utils.GUIUtils;
import gui.windows.MyBaseWindow;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

/**
 * This is the background window added at the root of the {@link main.Main#GUI GUI}. It displays the title, the
 * {@link #logPanel log}, and a footer.
 */
public class Background extends MyBaseWindow {
    /**
     * This panel displays a list of the most recent log messages. Those messages are stored in the
     * {@link #logEntries} list.
     */
    @NotNull
    private final Panel logPanel;

    /**
     * This is a list of all log entries currently in the {@link #logPanel}. The number of entries stored in this
     * list has an upper bound of the {@link #MAX_RECORDED_HEIGHT}.
     */
    @NotNull
    private final List<@NotNull LogLabel> logEntries = new LinkedList<>();

    /**
     * This it the maximum number of rows dedicated to the {@link #logPanel} seen at any one time. It's also the
     * maximum number of {@link #logEntries} that are stored. That allows log entries to be preserved if the window
     * is quickly decreased and increased in size, while also not wasting too much space storing log entries.
     * <p>
     * By default, this is initialized to 10.
     */
    private int MAX_RECORDED_HEIGHT = 10;

    public Background() {
        super();

        setHints(List.of(Hint.FULL_SCREEN));
        setTheme(new SimpleTheme(TextColor.ANSI.BLACK, TextColor.ANSI.BLACK_BRIGHT));

        // Create the log panel
        logPanel = new Panel(new GridLayout(5).setLeftMarginSize(0).setRightMarginSize(0));

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

                MAX_RECORDED_HEIGHT = Math.max(MAX_RECORDED_HEIGHT, newSize.getColumns());

                int columns = logPanel.getPreferredSize().getColumns();
                for (LogLabel label : logEntries)
                    label.updateMessage(columns);
            }
        });

        // Tell the log appender to notify this window when anything changes
        GUILogAppender.setOnUpdate(this::updateLog);
    }

    /**
     * Whenever a new log message is received or the window is resized, call this method to update the messages that
     * display in the {@link #logPanel}.
     */
    public void updateLog(@Nullable ILoggingEvent event) {
        if (event == null) return;

        Main.GUI.run(() -> {
            logEntries.add(LogLabel.of(event, logPanel.getPreferredSize().getColumns()).addTo(logPanel));

            // If the screen is full, remove the first log entry
            while (logEntries.size() > MAX_RECORDED_HEIGHT)
                logEntries.remove(0).removeFrom(logPanel);
        });
    }
}
