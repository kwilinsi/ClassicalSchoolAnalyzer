package gui.utils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GUILogAppender extends AppenderBase<ILoggingEvent> {
    /**
     * This is the list of log entries that have been obtained via {@link #append(ILoggingEvent)}.
     */
    @NotNull
    private final static List<ILoggingEvent> log = new ArrayList<>();

    /**
     * This runnable is {@link Runnable#run() called} whenever a new log entry is added and this object is
     * {@link #append(ILoggingEvent) notified}.
     * <p>
     * If this is <code>null</code>, nothing is called.
     */
    @Nullable
    private static Runnable onUpdate;

    /**
     * This is called whenever a new message is logged. It adds the event to the {@link #log} list and calls the
     * {@link #onUpdate} runnable.
     *
     * @param eventObject The event that was logged.
     */
    @Override
    protected void append(ILoggingEvent eventObject) {
        log.add(eventObject);
        if (onUpdate != null)
            onUpdate.run();
    }

    /**
     * Set the runnable that is {@link Runnable#run() called} every time the log updates.
     *
     * @param onUpdate The {@link #onUpdate} runnable.
     */
    public static void setOnUpdate(@Nullable Runnable onUpdate) {
        GUILogAppender.onUpdate = onUpdate;
    }

    /**
     * Get the last <code>n</code> entries from the {@link #log}.
     *
     * @param n The maximum number of entries to retrieve.
     *
     * @return A list containing up to the <code>n</code> entries from the log.
     */
    @NotNull
    public static List<ILoggingEvent> getLastEntries(int n) {
        return log.subList(Math.max(0, log.size() - n), log.size());
    }
}
