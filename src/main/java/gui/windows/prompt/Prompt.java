package gui.windows.prompt;

import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import gui.windows.MyBaseWindow;
import gui.windows.prompt.attribute.AttributePrompt;
import gui.windows.prompt.selection.Option;
import gui.windows.prompt.selection.SelectionPrompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This is the generic superclass for both {@link SelectionPrompt} and {@link AttributePrompt}. It represents a window
 * that is displayed to the user and contains three parts:
 * <ul>
 *     <li>A title.
 *     <li>A prompt message contained in a {@link Component}.
 *     <li>Some {@link Component} that presents the user with options to choose from.
 * </ul>
 */
public abstract class Prompt<T> extends MyBaseWindow {
    /**
     * This is the main {@link Panel} that contains everything in the prompt window. It is set as the component of this
     * window via {@link #setComponent(Component)}.
     */
    protected final Panel panel;

    /**
     * This is the {@link Component} that contains the prompt message for the user. It appears at the top of the
     * window.
     */
    protected final Component promptComponent;

    /**
     * This panel contains the mechanism for allowing the user to make a choice in response to the prompt. It appears
     * directly underneath the {@link #promptComponent}.
     */
    protected final Panel optionsPanel;

    /**
     * This contains the {@link Option#getValue() value} of the selected option. It is set when the user chooses an
     * option from the menu, closing this window.
     * <p>
     * While this {@link AtomicReference} can't be null, the value it contains can be.
     */
    @NotNull
    protected final AtomicReference<T> choice = new AtomicReference<>();

    protected Prompt(@Nullable String windowTitle,
                     @Nullable Component promptComponent,
                     @NotNull Panel optionComponent) {
        super(windowTitle == null ? "" : windowTitle);

        // Set the prompt style
        setHints(List.of(Hint.CENTERED));

        this.promptComponent = promptComponent == null ? new Panel() : promptComponent;
        this.optionsPanel = optionComponent;

        // Create the main panel that contains the window elements
        panel = new Panel(new LinearLayout(Direction.VERTICAL));
        setComponent(panel);

        // Add the prompt/message component
        if (promptComponent != null)
            panel.addComponent(promptComponent);

        // Add the options component
        panel.addComponent(optionsPanel);
    }

    /**
     * Get the user's selected {@link #choice}. This should only be called {@link #waitUntilClosed() after} the window
     * has {@link #close() closed}.
     *
     * @return The {@link AtomicReference#get() value} of the user's {@link #choice}.
     */
    public T getChoice() {
        return choice.get();
    }

    /**
     * {@link AtomicReference#set(Object) Set} the user's {@link #choice} and {@link #close() close} the window.
     *
     * @param value The user's choice.
     */
    protected void closeAndSet(@Nullable T value) {
        choice.set(value);
        close();
    }
}
