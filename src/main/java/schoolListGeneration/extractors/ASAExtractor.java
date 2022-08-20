package schoolListGeneration.extractors;

import constructs.CreatedSchool;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ASAExtractor implements Extractor {
    private static final Logger logger = LoggerFactory.getLogger(ASAExtractor.class);

    /**
     * Extract schools from the Anglican School Association website.
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
