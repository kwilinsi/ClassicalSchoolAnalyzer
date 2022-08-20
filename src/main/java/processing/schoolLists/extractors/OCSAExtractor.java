package processing.schoolLists.extractors;

import constructs.school.CreatedSchool;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class OCSAExtractor implements Extractor {
    private static final Logger logger = LoggerFactory.getLogger(OCSAExtractor.class);

    /**
     * Extract schools from the {@link constructs.OrganizationManager#OCSA Orthodox Christian School Association}
     * website.
     *
     * @param document The HTML document from which to extract the list.
     *
     * @return An array of created schools.
     */
    @Override
    public @NotNull List<CreatedSchool> extract(@NotNull Document document) {
        List<CreatedSchool> list = new ArrayList<>();
        logHeader();

        return list;
    }
}
