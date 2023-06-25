package processing.schoolLists.extractors.helpers;

import gui.windows.schoolMatch.SchoolListProgressWindow;
import org.jetbrains.annotations.Nullable;
import processing.schoolLists.extractors.Extractor;

import java.util.concurrent.Callable;

/**
 * A helper is an individual instance spawned by an {@link Extractor} to help process an Organization's school list.
 * It is designed to run in its own thread for performance gains, as it is used to parse data from a separate URL
 * than the main extractor, thus requiring a (relatively) slow HTTP request.
 * <p>
 * A helper typically returns either a single {@link constructs.school.CreatedSchool CreatedSchool} or, occasionally,
 * a small list of schools.
 *
 * @param <T>
 */
public abstract class Helper<T> implements Callable<T> {
    /**
     * The parent {@link Extractor} that spawned this individual helper.
     */
    protected final Extractor parent;

    /**
     * This controls whether {@link utils.JsoupHandler.DownloadConfig#CACHE_ONLY caching} should be employed when
     * downloading pages.
     */
    protected final boolean useCache;

    /**
     * An optional instance of {@link SchoolListProgressWindow}. This is updated accordingly if it is
     * not <code>null</code>.
     */
    @Nullable
    protected final SchoolListProgressWindow progress;

    /**
     * Initialize a helper.
     *
     * @param parent   See {@link #parent}.
     * @param useCache See {@link #useCache}.
     * @param progress See {@link #progress}.
     */
    public Helper(Extractor parent, boolean useCache, @Nullable SchoolListProgressWindow progress) {
        this.parent = parent;
        this.useCache = useCache;
        this.progress = progress;
    }
}
