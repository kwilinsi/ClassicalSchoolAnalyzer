package main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import schools.School;
import schools.SchoolListExtractor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class Actions {
    private static final Logger logger = LoggerFactory.getLogger(Actions.class);

    public static void notImplemented() {
        logger.error("Sorry, this feature is not yet implemented.");
    }

    public static void downloadSchoolList() {
        logger.info("Downloading school list");

        for (Map.Entry<String, Function<String, List<School>>> entry :
                SchoolListExtractor.SCHOOL_LIST_WEBSITES.entrySet())
            entry.getValue().apply(entry.getKey());
    }

    public static void downloadSchoolWebsites() {
        logger.info("Downloading school websites");
        notImplemented();
    }

    public static void performAnalysis() {
        logger.info("Performing analysis on classical schools");
        notImplemented();
    }
}
