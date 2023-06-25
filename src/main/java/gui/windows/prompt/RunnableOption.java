package gui.windows.prompt;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RunnableOption extends Option<Runnable> {
    protected RunnableOption(@NotNull String name, @Nullable Runnable runnable, @Nullable String confirmationMessage) {
        super(name, runnable, confirmationMessage);
    }

    /**
     * Create a new runnable option by providing the text shown to the user and the runnable to call when selected.
     *
     * @param name     The {@link #name}.
     * @param runnable The {@link #value}.
     *
     * @return The new runnable option.
     * @see #of(String, Runnable, String)
     */
    public static RunnableOption of(@NotNull String name, @Nullable Runnable runnable) {
        return new RunnableOption(name, runnable, null);
    }

    /**
     * Create a new runnable option with a confirmation message.
     *
     * @param name               The {@link #name}.
     * @param runnable          The {@link #value}.
     * @param confirmationMessage The {@link #confirmationMessage}.
     *
     * @return The new runnable option.
     * @see #of(String, Runnable)
     */
    public static RunnableOption of(@NotNull String name,
                                    @Nullable Runnable runnable,
                                    @Nullable String confirmationMessage) {
        return new RunnableOption(name, runnable, confirmationMessage);
    }
}
