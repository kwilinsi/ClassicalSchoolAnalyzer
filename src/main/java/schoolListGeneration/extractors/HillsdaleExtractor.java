package schoolListGeneration.extractors;

import constructs.Attribute;
import constructs.CreatedSchool;
import constructs.SchoolManager;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import schoolListGeneration.extractors.helpers.ExtUtils;
import schoolListGeneration.extractors.helpers.HillsdaleParse;

import java.util.ArrayList;
import java.util.List;

public class HillsdaleExtractor implements Extractor {
    private static final Logger logger = LoggerFactory.getLogger(HillsdaleExtractor.class);

    /**
     * Extract schools from the Hillsdale K-12 Education website.
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

        // Get all the script tags, each of which contains a school
        Elements scriptTags = document.select("h2.page-title ~ script + script[type]");

        logger.info("Identified " + scriptTags.size() + " possible Hillsdale schools.");

        // Iterate through each script tag, extracting the attributes for each school
        for (Element scriptTag : scriptTags) {
            // Make a new school instance
            CreatedSchool school = SchoolManager.newHillsdale();
            String text = scriptTag.outerHtml();

            String level = HillsdaleParse.match(text, HillsdaleParse.Regex.HILLSDALE_AFFILIATION_LEVEL);
            // Ignore the school whose type is "". It's "Ryan's test school" — definitely not real.
            if (level == null || level.isBlank())
                continue;

            school.put(Attribute.hillsdale_affiliation_level, level);
            school.put(Attribute.latitude, HillsdaleParse.matchDouble(text, HillsdaleParse.Regex.LATITUDE));
            school.put(Attribute.longitude, HillsdaleParse.matchDouble(text, HillsdaleParse.Regex.LONGITUDE));
            school.put(Attribute.city, HillsdaleParse.match(text, HillsdaleParse.Regex.CITY));
            school.put(Attribute.state, HillsdaleParse.match(text, HillsdaleParse.Regex.STATE));
            school.put(Attribute.name, ExtUtils.validateName(HillsdaleParse.match(text, HillsdaleParse.Regex.NAME)));
            school.put(Attribute.website_url,
                    ExtUtils.aliasNullLink(HillsdaleParse.match(text, HillsdaleParse.Regex.WEBSITE_URL)));
            school.put(Attribute.year_founded, HillsdaleParse.matchInt(text, HillsdaleParse.Regex.FOUNDED_YEAR));
            school.put(Attribute.enrollment, HillsdaleParse.matchInt(text, HillsdaleParse.Regex.ENROLLMENT));
            school.put(Attribute.grades_offered, HillsdaleParse.match(text, HillsdaleParse.Regex.GRADES));
            school.put(Attribute.projected_opening,
                    HillsdaleParse.matchInt(text, HillsdaleParse.Regex.PROJECTED_OPENING));

            list.add(school);
        }

        return list;
    }
}
