package main;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * These actions correspond to <code>static</code> methods in the {@link Actions} class. They are used by
 * {@link Main#main(String[]) Main.main()} to prompt the user for the desired action to run.
 */
public enum Action {
    /**
     * Run {@link Actions#updateSchoolList()}.
     */
    UPDATE_SCHOOL_LIST(Actions::updateSchoolList, "Download school list", null),

    /**
     * Run {@link Actions#downloadSchoolWebsites()}.
     */
    DOWNLOAD_SCHOOL_WEBSITES(Actions::downloadSchoolWebsites, "Download school websites", null),

    /**
     * Run {@link Actions#performAnalysis()}.
     */
    PERFORM_ANALYSIS(Actions::performAnalysis, "Perform analysis", null),

    /**
     * Run {@link Actions#manageDatabase()}.
     */
    MANAGE_DATABASE(Actions::manageDatabase, "Manage database", null),

    MANAGE_CORRECTIONS(Actions::manageCorrections, "Manage corrections", null),

    /**
     * Run {@link Actions#clearDataDirectory()}.
     */
    CLEAR_DATA_DIRECTORY(Actions::clearDataDirectory, "Clear data directory",
            "This will delete all downloaded files in the data directory.\n" +
                    "Are you sure you wish to continue?"),

    /**
     * Run {@link Actions#test()}.
     */
    TEST(Actions::test, "Test script", null),

    /**
     * Run {@link Main#shutdown()}.
     */
    CLOSE(Main::shutdown, "Close", "Are you sure you want to exit Classical School Analyzer?");

    /**
     * This is the {@link Runnable} that is {@link Runnable#run() executed} when this action is {@link #run() run}.
     */
    @NotNull
    private final Runnable runnable;

    /**
     * This is the user-friendly name for the action to put in the {@link gui.windows.MainMenu MainMenu}.
     */
    @NotNull
    private final String friendlyName;

    /**
     * This confirmation message, if not <code>null</code>, is shown to the user when they attempt to select this
     * action in the {@link gui.windows.MainMenu MainMenu}. The action is only run if the user approves the
     * confirmation.
     */
    @Nullable
    private final String confirmation;

    /**
     * Create an action for one of the {@link Actions} methods.
     *
     * @param runnable       The {@link #runnable}.
     * @param friendlyName The {@link #friendlyName}.
     * @param confirmation The {@link #confirmation}.
     */
    Action(@NotNull Runnable runnable, @NotNull String friendlyName, @Nullable String confirmation) {
        this.friendlyName = friendlyName;
        this.runnable = runnable;
        this.confirmation = confirmation;
    }

    @NotNull
    public String getFriendlyName() {
        return friendlyName;
    }

    @Nullable
    public String getConfirmation() {
        return confirmation;
    }

    /**
     * {@link Runnable#run() Run} the {@link #runnable}.
     */
    public void run() {
        runnable.run();
    }
}
