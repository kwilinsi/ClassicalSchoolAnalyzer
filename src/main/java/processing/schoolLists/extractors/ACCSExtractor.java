package processing.schoolLists.extractors;

import constructs.organization.OrganizationManager;
import constructs.school.CreatedSchool;
import gui.windows.schoolMatch.SchoolListProgressWindow;
import gui.windows.prompt.Option;
import gui.windows.prompt.SelectionPrompt;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.schoolLists.extractors.helpers.ACCSSchoolParser;
import utils.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class ACCSExtractor implements Extractor {
    private static final Logger logger = LoggerFactory.getLogger(ACCSExtractor.class);

    @Override
    public String abbreviation() {
        return OrganizationManager.ACCS.getNameAbbr();
    }

    @Override
    @NotNull
    public List<CreatedSchool> extract(@NotNull Document document, @Nullable SchoolListProgressWindow progress) {
        List<CreatedSchool> list = new ArrayList<>();
        logHeader();

        int choice = Main.GUI.showPrompt(SelectionPrompt.of(
                abbreviation() + " - Cache",
                "Select a mode for loading " + abbreviation() + " school pages:",
                Option.of("Use Cache", 1),
                Option.of("Force New Download", 2),
                Option.of("Test a sample (incl. cache)", 3)
        ));
        boolean useCache = choice != 2;

        // Look for links in the form <a style="display: inline-block ......">
        // These are links to the "More info" page for each school.
        Elements linkElements = document.select("a[style*=display: inline-block;]");

        // If the user only wants to test a small sample, shuffle the links and remove all but 5 randomly.
        if (choice == 3) {
            Collections.shuffle(linkElements);
            while (linkElements.size() > 5)
                linkElements.remove(0);
        }

        logPossibleCount(linkElements.size(), progress);

        // Create a parser Callable for each school link
        List<ACCSSchoolParser> parsers = linkElements.stream()
                .map(e -> new ACCSSchoolParser(this, useCache, progress, e.attr("href")))
                .collect(Collectors.toList());

        // Iterate through each school, going to its "more info" page and scraping the information there.
        // This is done with a thread pool to improve performance
        ExecutorService threadPool = Executors.newFixedThreadPool(Config.MAX_THREADS_ORGANIZATIONS.getInt());
        List<Future<CreatedSchool>> futures = null;
        try {
            futures = threadPool.invokeAll(parsers);
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for thread pool to process " + abbreviation() + " schools.", e);
        }

        if (progress != null)
            progress.setSubTask("Extracting futures...");

        // Add the resulting Schools to the list
        if (futures != null)
            for (int i = 0; i < futures.size(); i++) {
                Future<CreatedSchool> future = futures.get(i);

                try {
                    list.add(future.get());
                } catch (InterruptedException | ExecutionException e) {
                    // Any thread that encountered an error will log this to the console and be omitted from the
                    // final returned list.
                    logger.warn("An " + abbreviation() + " parsing thread encountered an error during execution: " +
                            parsers.get(i).get_accs_page_url(), e);
                }
            }

        // Return the final assembled list of schools.
        logParsedCount(list.size(), progress);
        return list;
    }
}
