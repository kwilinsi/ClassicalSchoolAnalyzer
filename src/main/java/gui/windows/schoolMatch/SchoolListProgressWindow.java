package gui.windows.schoolMatch;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import gui.GUI;
import gui.utils.GUIUtils;
import gui.windows.MyBaseWindow;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This window is designed to show progress bars while {@link main.Action#UPDATE_SCHOOL_LIST updating} the school list.
 * <p>
 * Note that all custom methods for updating this window route {@link GUI#run(Runnable) route} any modifications through
 * the {@link GUI} thread. This allows multiple threads to independently {@link #incrementSubProgress() increment} the
 * {@link #subProgressBar}, for example. As a side effect, the methods may return before their changes are actually
 * visible on the screen.
 */
@SuppressWarnings("UnusedReturnValue")
public class SchoolListProgressWindow extends MyBaseWindow {
    private static final Logger logger = LoggerFactory.getLogger(SchoolListProgressWindow.class);

    /**
     * These are the various distinct phases of updating the school list. {@link #setPhase(Phase) Set} the current
     * phase to update the main progress bar.
     * <p>
     * The phases enums are listed according to their natural order in the process. Thus, a phase's
     * {@link #ordinal() ordinal} indicates the progress numerically for the {@link #mainProgressBar main} progress bar.
     */
    public enum Phase {
        /**
         * Retrieving a list of existing schools from the database.
         */
        RETRIEVING_SCHOOL_CACHE("Retrieving school cache..."),

        /**
         * Retrieving a list of existing districts from the database.
         */
        RETRIEVING_DISTRICT_CACHE("Retrieving district cache..."),

        /**
         * Retrieving a list of existing district-organization relations from the database.
         */
        RETRIEVING_DISTRICT_ORGANIZATION_CACHE("Retrieving district-organization cache..."),

        /**
         * Downloading and parsing the school lists from each Organization.
         */
        DOWNLOADING_SCHOOLS("Downloading school lists..."),

        /**
         * Preprocessing and normalization of each downloaded school.
         */
        PRE_PROCESSING_SCHOOLS("Pre-processing schools..."),

        /**
         * Checking each school for possible matches already in the database.
         */
        IDENTIFYING_MATCHES("Identifying duplicates..."),

        /**
         * Saving the downloaded schools, along with districts and district organization relations, to the database.
         */
        SAVING_TO_DATABASE("Saving to database..."),

        /**
         * Finished updating the school list.
         */
        FINISHED("Finished");

        /**
         * A user-readable message indicating the task.
         */
        @NotNull
        private final String message;

        Phase(@NotNull String message) {
            this.message = message;
        }
    }

    /**
     * The maximum width of the progress bars.
     */
    private static final int PROGRESS_BAR_MAX_WIDTH = 75;

    /**
     * This label displays the current general task being completed.
     *
     * @see #setGeneralTask(String)
     */
    @NotNull
    private final Label generalTask;

    /**
     * This label displays a sub-task of the {@link #generalTask}. It is either currently being completed or was just
     * completed. This is intended to change rapidly.
     *
     * @see #setSubTask(String)
     */
    @NotNull
    private final Label subTask;

    /**
     * This label appears above the {@link #mainProgressBar}. It shows the current phase's {@link Phase#message
     * message} prefixed by a step number.
     */
    @NotNull
    private final Label phaseLabel;

    /**
     * The main progress bar that indicates the overall progress in updating the school list. This progress bar has
     * one step for each of the {@link Phase Phases}. It is updated with {@link #setPhase(Phase) setPhase()}. Since
     * some phases will be significantly faster than others, this progress bar has the disadvantage of being
     * unpredictable to the user. However, it has the advantage of only finishing when the entire process is done.
     *
     * @see #subProgressBar
     */
    @NotNull
    private final ProgressBar mainProgressBar;

    /**
     * This is the secondary progress bar that indicates the progress in the {@link #setPhase(Phase) current}
     * {@link Phase Phase}. It should have the advantage of progressing relatively smoothly for most phases, but it
     * has the disadvantage of not representing the total progress in updating the school list.
     *
     * @see #mainProgressBar
     */
    @NotNull
    private final ProgressBar subProgressBar;

    /**
     * This label appears above the {@link #subProgressBar}. It shows the actual numerical progress of the progress
     * bar as a fraction.
     */
    @NotNull
    private final Label subProgressLabel;

    /**
     * The prefix for the {@link #subProgressLabel}. This is used to generate the complete label via
     * {@link #setProgressLabel()}.
     * <p>
     * If this is <code>null</code>, that means the label should be hidden.
     */
    @Nullable
    private String subProgressPrefix;

    /**
     * This is the panel that contains both the {@link #subProgressBar} and he {@link #subProgressLabel}. The label
     * is added and removed from this panel based on {@link #resetSubProgressBar(int, String)}.
     */
    @NotNull
    private final Panel subProgressPanel;

    private SchoolListProgressWindow() {
        super();

        generalTask = new Label("");
        subTask = new Label("");
        phaseLabel = new Label("");
        mainProgressBar = new ProgressBar(0, Phase.values().length, PROGRESS_BAR_MAX_WIDTH);
        subProgressBar = new ProgressBar(-2, -1, PROGRESS_BAR_MAX_WIDTH);
        subProgressLabel = new Label("");
        subProgressPanel = new Panel();

        setHints(List.of(Hint.MODAL, Hint.CENTERED));
        setComponent(makePanel());
    }

    /**
     * Initialize a new window.
     */
    public static SchoolListProgressWindow of() {
        return new SchoolListProgressWindow();
    }

    /**
     * Make the central panel for this progress window.
     *
     * @return The panel.
     */
    private Panel makePanel() {
        subProgressPanel
                .setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
                .addComponent(subProgressBar,
                        LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow)
                );
        resetSubProgressBar(1, null);

        return new Panel()
                .setLayoutManager(new GridLayout(1)
                        .setBottomMarginSize(1).setVerticalSpacing(1))
                .addComponent(GUIUtils.createFilledHeader(
                        "School List Updater", TextColor.ANSI.BLUE_BRIGHT, 3
                ))
                .addComponent(new Panel()
                        .addComponent(generalTask)
                        .addComponent(subTask)
                )
                .addComponent(subProgressPanel)
                .addComponent(new Separator(Direction.HORIZONTAL),
                        GridLayout.createHorizontallyFilledLayoutData())
                .addComponent(phaseLabel)
                .addComponent(mainProgressBar);
    }

    /**
     * Update the {@link #generalTask} label.
     *
     * @param task The task text. If this is <code>null</code>, the task is cleared.
     * @return Itself, for chaining.
     */
    public SchoolListProgressWindow setGeneralTask(@Nullable String task) {
        Main.GUI.run(() -> generalTask.setText(task == null ? "" : task));
        return this;
    }

    /**
     * Update the {@link #subTask} label.
     *
     * @param task The task text. If this is <code>null</code>, the task is cleared.
     * @return Itself, for chaining.
     */
    public SchoolListProgressWindow setSubTask(@Nullable String task) {
        Main.GUI.run(() -> subTask.setText(task == null ? "" : task));
        return this;
    }

    /**
     * Clear both the {@link #generalTask general} and {@link #subTask sub} task labels.
     *
     * @return Itself, for chaining.
     */
    public SchoolListProgressWindow clearTasks() {
        Main.GUI.run(() -> {
            generalTask.setText("");
            subTask.setText("");
        });
        return this;
    }

    /**
     * Set the current {@link Phase Phase} of the school list updating process. This adjusts the progress of the
     * {@link #mainProgressBar main} progress bar. It also resets the {@link #subProgressBar sub} progress bar to 0.
     *
     * @param phase The current phase.
     * @return Itself, for chaining.
     */
    public SchoolListProgressWindow setPhase(@NotNull Phase phase) {
        Main.GUI.run(() -> {
            mainProgressBar.setValue(phase.ordinal() + 1);
            subProgressBar.setValue(subProgressBar.getMin());
            phaseLabel.setText(
                    (phase == Phase.FINISHED ? "" : String.format("Step %d: ", phase.ordinal() + 1)) + phase.message
            );
        });
        return this;
    }

    /**
     * Reset the {@link #subProgressBar}. This sets it {@link ProgressBar#setMin(int) minimum} to 0, its
     * {@link ProgressBar#setMax(int) maximum} to the given value, and its current {@link ProgressBar#setValue(int)
     * value} to 0.
     * <p>
     * This also clears and hides the {@link #subProgressLabel}.
     *
     * @param max The maximum value for the progress bar. Set this to <code>0</code> to effectively disable the
     *            progress bar.
     * @return Itself, for chaining.
     * @see #resetSubProgressBar(int, String)
     */
    public SchoolListProgressWindow resetSubProgressBar(int max) {
        return resetSubProgressBar(max, null);
    }

    /**
     * Reset the {@link #subProgressBar}, and set a new prefix for the {@link #subProgressLabel}. If that prefix is
     * <code>null</code>, the label is hidden.
     *
     * @param max    The new maximum for the progress bar. Set this to <code>-1</code> to effectively disable the
     *               progress bar. The minimum will be set to <code>-2</code> and the label will include question
     *               marks, if present. See {@link #setProgressLabel()}.
     * @param prefix The new prefix. This should not include a colon, as that is added automatically.
     * @return Itself, for chaining.
     * @see #resetSubProgressBar(int)
     */
    public SchoolListProgressWindow resetSubProgressBar(int max, @Nullable String prefix) {
        Main.GUI.run(() -> {
            if (max == 0)
                subProgressBar.setMin(-2).setMax(-1).setValue(-2);
            else
                subProgressBar.setMin(0).setMax(max).setValue(0);

            subProgressPrefix = prefix;
            updateProgressBarWidth();
            setProgressLabel();
        });
        return this;
    }

    /**
     * Add <code>1</code> to the {@link #subProgressBar subProgressBar's} {@link ProgressBar#getValue() value}. This
     * also updates the {@link #subProgressLabel}, if that is currently visible.
     *
     * @return Itself, for chaining.
     * @see #increaseSubProgressMax(int)
     */
    public SchoolListProgressWindow incrementSubProgress() {
        Main.GUI.run(() -> {
            subProgressBar.setValue(subProgressBar.getValue() + 1);
            setProgressLabel();
        });
        return this;
    }

    /**
     * Increase the {@link ProgressBar#getMax() maximum} of the {@link #subProgressBar}, thus making it further from
     * completion. This may be necessary with the emergence of more data to process.
     *
     * @param delta The amount to increase the maximum. This may be negative, as well, to decrease it.
     * @return Itself, for chaining.
     * @see #incrementSubProgress()
     */
    public SchoolListProgressWindow increaseSubProgressMax(int delta) {
        Main.GUI.run(() -> {
            subProgressBar.setMax(subProgressBar.getMax() + delta);
            updateProgressBarWidth();
            setProgressLabel();
        });
        return this;
    }

    /**
     * Mark the {@link #subProgressBar} completed by setting its {@link ProgressBar#getValue() value} to the
     * {@link ProgressBar#getMax() maximum}.
     *
     * @return Itself, for chaining.
     */
    public SchoolListProgressWindow completeSubProgress() {
        Main.GUI.run(() -> subProgressBar.setValue(subProgressBar.getValue()));
        return this;
    }

    /**
     * Some fatal error occurred, and all progress must now halt. The <code>message</code> and <code>error</code> are
     * logged, and a dialog appears showing the error message. When dismissed, the window {@link #close() closes}.
     * <p>
     * This is <code>synchronized</code> to prevent multiple simultaneous calls. If, when it is called, its
     * {@link #getComponent() component} is <code>null</code>, this does nothing besides log the error.
     *
     * @param message The message to log and show the user.
     * @param error   The throwable that caused the program to error out. This is logged, and its
     *                {@link Throwable#getLocalizedMessage() message} is shown to the user.
     */
    public void errorOut(@NotNull String message, @Nullable Throwable error) {
        if (error == null)
            logger.error(message);
        else
            logger.error(message, error);

        if (getComponent() == null) return;

        Main.GUI.dialog(
                "Fatal Error",
                "A fatal error occurred, and the process must now halt.%s",
                List.of(message, error == null ? "" : "\n\n" + error.getLocalizedMessage())
        );

        close();
    }

    /**
     * Show a dialog with the error message, and log the message to the console. If the user chooses to
     * {@link MessageDialogButton#Abort Abort}, the window is {@link #close() closed} and <code>true</code> is
     * returned. Otherwise, if the user acknowledges but {@link MessageDialogButton#Ignore Ignores} the error, nothing
     * else happens.
     * <p>
     * This is <code>synchronized</code> to prevent multiple simultaneous calls. If, when it is called, its
     * {@link #getComponent() component} is <code>null</code>, this does nothing besides log the error, and it
     * returns <code>false</code>.
     * <p>
     * This is similar to {@link #errorOut(String, Throwable) errorOut()}, except that the user is given the
     * opportunity to continue processing.
     *
     * @param message The message to log and show the user.
     * @param error   The error the program encountered. This is logged, and its
     *                {@link Throwable#getLocalizedMessage() message} is shown to the user.
     * @return Whether the window is closed as a result of this call.
     */
    public synchronized boolean errorPrompt(@NotNull String message, @Nullable Throwable error) {
        if (error == null)
            logger.error(message);
        else
            logger.error(message, error);

        if (getComponent() == null) return false;

        MessageDialogButton selection = Main.GUI.dialog(
                "Error",
                "An error occurred. Select Ignore to continue or Abort to stop the process.\n\n%s%s"
                        .formatted(message, error == null ? "" : "\n\n" + error.getLocalizedMessage()),
                MessageDialogButton.Ignore,
                MessageDialogButton.Abort
        );

        if (selection == MessageDialogButton.Ignore)
            return false;

        close();
        return true;
    }

    /**
     * This does the following:
     * <ul>
     *     <li>{@link #setPhase(Phase) Update} the {@link #phaseLabel phase} to {@link Phase#FINISHED FINISHED}.
     *     <li>{@link #clearTasks() Clear} all tasks.
     *     <li>Show a concluding message in a popup dialog.
     *     <li>Once the user acknowledges the message, {@link #close() close} the window.
     * </ul>
     *
     * @param schoolCount The number of schools that were processed. This is shown in the dialog message.
     */
    public void finishAndWait(int schoolCount) {
        setPhase(Phase.FINISHED);
        clearTasks();
        Main.GUI.dialog(
                null,
                "Finished processing %s schools. Press enter to continue.".formatted(schoolCount),
                MessageDialogButton.Continue
        );
        close();
    }

    /**
     * {@link Label#setText(String) Set} the label text on the {@link #subProgressLabel}. This is done by combining
     * the {@link #subProgressPrefix prefix} with the current progress bar {@link ProgressBar#getValue() value} and
     * {@link ProgressBar#getMax() maximum} in a fraction. The fraction is right aligned and padded with spaces to
     * fill its maximum possible length.
     * <p>
     * All of this only happens when the prefix is not <code>null</code>. If it is <code>null</code>, nothing
     * happens, as that indicates that the label is not visible.
     * <p>
     * If the progress bar's {@link ProgressBar#getMax() max} is <code>-1</code> and the prefix isn't <code>null</code>,
     * it's assumed that the true maximum hasn't been determined yet. In that case, the label is shown, but a question
     * mark is used for the denominator to indicate insufficient information.
     * <p>
     * This does not redirect any calls to the GUI thread. The caller must explicitly {@link GUI#run(Runnable) run}
     * it on the GUI thread.
     */
    private synchronized void setProgressLabel() {
        if (subProgressPrefix != null)
            if (subProgressBar.getMax() == -1)
                subProgressLabel.setText(subProgressPrefix + ": 0 / ?");
            else
                subProgressLabel.setText(String.format(
                        "%s: %" + (String.valueOf(subProgressBar.getMax()).length() * 2 + 3) + "s",
                        subProgressPrefix,
                        subProgressBar.getValue() + " / " + subProgressBar.getMax()
                ));
    }

    /**
     * Whenever a new {@link #subProgressPrefix prefix} and {@link #subProgressBar} {@link ProgressBar#getMax()
     * maximum} are set, call this to update the widths of the components to make them align properly.
     * <p>
     * This does not redirect any calls to the GUI thread. The caller must explicitly {@link GUI#run(Runnable) run}
     * it on the GUI thread.
     */
    private synchronized void updateProgressBarWidth() {
        if (subProgressPrefix == null) {
            if (subProgressPanel.getChildCount() == 2)
                subProgressPanel.removeComponent(subProgressLabel);

            subProgressLabel.setText("");
            subProgressBar.setPreferredWidth(PROGRESS_BAR_MAX_WIDTH);
        } else {
            if (subProgressPanel.getChildCount() == 1)
                subProgressPanel.addComponent(subProgressLabel);

            subProgressBar.setPreferredWidth(PROGRESS_BAR_MAX_WIDTH -
                    (subProgressPrefix.length() + String.valueOf(subProgressBar.getMax()).length() * 2 + 6)
            );
        }
    }
}
