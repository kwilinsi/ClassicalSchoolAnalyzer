package processing.schoolLists.extractors;

import constructs.school.CreatedSchool;
import gui.windows.prompt.selection.Option;
import gui.windows.prompt.selection.SelectionPrompt;
import main.Main;
import org.jetbrains.annotations.NotNull;
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

    /**
     * Extract schools from the {@link constructs.OrganizationManager#ICLE Institute for Catholic Liberal Education}
     * website.
     *
     * @param document The HTML document from which to extract the list.
     *
     * @return An array of created schools.
     */
    @Override
    @NotNull
    public List<CreatedSchool> extract(@NotNull Document document) {
        List<CreatedSchool> list = new ArrayList<>();
        logHeader();

        // Prompt the user for the mode of extraction for individual school pages.
        int choice = Main.GUI.showPrompt(SelectionPrompt.of(
                "ICLE - Cache",
                "Select a mode for loading ICLE school and school list pages:",
                Option.of("Use Cache", 1),
                Option.of("Force New Download", 2),
                Option.of("Test a sample (incl. cache)", 3)
        ));

        boolean useCache = choice != 2;

        // Get a list of all the pages that show schools
        Integer pageCount = ExtUtils.extHtmlInt(document, "div.aui-nav-links ul li:eq(4) a");
        if (pageCount == null || pageCount < 1) {
            logger.warn("Could not identify the number of ICLE school pages.");
            return list;
        }
        logger.info("Identified {} ICLE school list pages.", pageCount);

        // Create parser threads
        List<ICLEPageParser> parsers = new ArrayList<>();
        for (int i = 1; i <= pageCount; i++) {
            ICLEPageParser parser = new ICLEPageParser(i, useCache);
            parsers.add(parser);
        }

        // Create thread pool and run the parsers
        ExecutorService threadPool = Executors.newFixedThreadPool(Config.MAX_THREADS_ORGANIZATIONS.getInt());
        List<Future<List<CreatedSchool>>> futures = null;
        try {
            futures = threadPool.invokeAll(parsers);
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for thread pool to process ICLE schools.", e);
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
                    logger.warn("An ICLE parsing thread encountered an error during execution: " +
                                parsers.get(i).toString(), e);
                }
            }

        return list;
    }
}
