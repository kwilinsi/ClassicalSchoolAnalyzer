package processing.schoolLists.extractors;

import constructs.organization.OrganizationManager;
import constructs.school.CreatedSchool;
import gui.windows.prompt.schoolMatch.SchoolListProgressWindow;
import gui.windows.prompt.selection.Option;
import gui.windows.prompt.selection.SelectionPrompt;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.schoolLists.extractors.helpers.ICLEPageParser;
import processing.schoolLists.extractors.helpers.ExtUtils;
import utils.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ICLEExtractor implements Extractor {
    private static final Logger logger = LoggerFactory.getLogger(ICLEExtractor.class);

    @Override
    public String abbreviation() {
        return OrganizationManager.ICLE.getNameAbbr();
    }

    @Override
    @NotNull
    public List<CreatedSchool> extract(@NotNull Document document, @Nullable SchoolListProgressWindow progress) {
        List<CreatedSchool> list = new ArrayList<>();
        logHeader();

        // Prompt the user for the mode of extraction for individual school pages.
        int choice = Main.GUI.showPrompt(SelectionPrompt.of(
                abbreviation() + " - Cache",
                "Select a mode for loading " + abbreviation() + " school and school list pages:",
                Option.of("Use Cache", 1),
                Option.of("Force New Download", 2),
                Option.of("Test a sample (incl. cache)", 3)
        ));

        boolean useCache = choice != 2;

        // Get a list of all the pages that show schools
        Integer pageCount = ExtUtils.extHtmlInt(document, "div.aui-nav-links ul li:eq(4) a");
        if (pageCount == null || pageCount < 1) {
            logger.warn("Could not identify the number of {} school pages.", abbreviation());
            logParsedCount(0, progress);
            return list;
        }

        logPossibleCount(pageCount, progress);

        // Create parser threads
        List<ICLEPageParser> parsers = new ArrayList<>();
        for (int i = 1; i <= pageCount; i++) {
            ICLEPageParser parser = new ICLEPageParser(this, useCache, progress, i);
            parsers.add(parser);
        }

        // Create thread pool and run the parsers
        ExecutorService threadPool = Executors.newFixedThreadPool(Config.MAX_THREADS_ORGANIZATIONS.getInt());
        List<Future<List<CreatedSchool>>> futures = null;
        try {
            futures = threadPool.invokeAll(parsers);
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for thread pool to process " + abbreviation() + " schools.", e);
        }

        // Combine the results from each parser
        if (futures != null)
            for (int i = 0; i < futures.size(); i++) {
                Future<List<CreatedSchool>> future = futures.get(i);
                try {
                    list.addAll(future.get());
                } catch (InterruptedException | ExecutionException e) {
                    // Any thread that encountered an error will log this to the console and be omitted from the
                    // final returned list.
                    logger.warn("An " + abbreviation() + " parsing thread encountered an error during execution: " +
                            parsers.get(i).toString(), e);
                }
            }

        logParsedCount(list.size(), progress);
        return list;
    }

    /**
     * Log a message indicating the number of identified <b>pages</b>. Also use this to update the progress bar
     * maximum, if a progress window is given, because it still gives some idea of the process.
     *
     * @param n        The number of pages.
     * @param progress The optional progress window or <code>null</code> to omit this.
     */
    @Override
    public void logPossibleCount(int n, @Nullable SchoolListProgressWindow progress) {
        logger.info("Identified {} possible {} pages", n, abbreviation());
        if (progress != null)
            progress.resetSubProgressBar(n, "Schools");
    }
}
