package main;

import org.jetbrains.annotations.NotNull;

/**
 * These actions correspond to <code>static</code> methods in the {@link Actions} class. They are used by
 * {@link Main#main(String[]) Main.main()} to prompt the user for the desired action to run.
 */
public enum Action {
    /**
     * Run {@link Actions#updateSchoolList()}.
     */
    UPDATE_SCHOOL_LIST(Actions::updateSchoolList),

    /**
     * Run {@link Actions#downloadSchoolWebsites()}.
     */
    DOWNLOAD_SCHOOL_WEBSITES(Actions::downloadSchoolWebsites),

    /**
     * Run {@link Actions#performAnalysis()}.
     */
    PERFORM_ANALYSIS(Actions::performAnalysis),

    /**
     * Run {@link Actions#setupDatabase()}.
     */
    SETUP_DATABASE(Actions::setupDatabase),

    /**
     * Run {@link Actions#clearDataDirectory()}.
     */
    CLEAR_DATA_DIRECTORY(Actions::clearDataDirectory),

    /**
     * Run {@link Actions#test()}.
     */
    TEST(Actions::test);

    /**
     * This is the {@link Runnable} that is {@link Runnable#run() executed} when this action is {@link #run() run}.
     */
    private final Runnable method;

    /**
     * Create an {@link main.Action} that calls the provided {@link Runnable} when {@link #run() run}.
     *
     * @param method The {@link #method}.
     */
    Action(@NotNull Runnable method) {
        this.method = method;
    }

    /**
     * Run the {@link #method}.
     */
    public void run() {
        method.run();
    }
}
