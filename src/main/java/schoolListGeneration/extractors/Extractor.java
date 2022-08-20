package schoolListGeneration.extractors;

import constructs.CreatedSchool;
import org.jetbrains.annotations.NotNull;

import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public interface Extractor {
    Logger logger = LoggerFactory.getLogger(Extractor.class);

    @NotNull
    List<CreatedSchool> extract(@NotNull Document document);

    default void logHeader() {
        logger.info(
                "========== Running {} extractor ==========",
                getClass().getSimpleName().replace("Extractor", "")
        );
    }
}
