package processing.schoolLists.extractors;

import constructs.school.CreatedSchool;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CCLEExtractor implements Extractor {
    private static final Logger logger = LoggerFactory.getLogger(CCLEExtractor.class);

    /**
     * Extract schools from the {@link constructs.OrganizationManager#CCLE Consortium for Classical Lutheran Education}
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

        return list;
    }
}
