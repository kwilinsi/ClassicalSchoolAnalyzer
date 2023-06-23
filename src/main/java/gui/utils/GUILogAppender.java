package gui.utils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class GUILogAppender extends AppenderBase<ILoggingEvent> {
    /**
     * This runnable is {@link Runnable#run() called} whenever a new log entry is added and this object is
     * {@link #append(ILoggingEvent) notified}.
     * <p>
     * If this is <code>null</code>, nothing is called.
     */
    @Nullable
    private static Consumer<ILoggingEvent> onUpdate;

    /**
     * This is called whenever a new message is logged. It calls the {@link #onUpdate} callable to update the log
     * display.
     *
     * @param eventObject The event that was logged.
     */
    @Override
    protected void append(ILoggingEvent eventObject) {
        if (onUpdate != null)
            onUpdate.accept(eventObject);
    }

    /**
     * Set the runnable that is {@link Runnable#run() called} every time the log updates.
     *
     * @param onUpdate The {@link #onUpdate} runnable.
     */
    public static void setOnUpdate(@Nullable Consumer<ILoggingEvent> onUpdate) {
        GUILogAppender.onUpdate = onUpdate;
    }
}
