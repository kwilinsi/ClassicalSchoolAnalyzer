package processing.schoolLists.extractors;

import constructs.organization.OrganizationManager;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import gui.windows.schoolMatch.SchoolListProgressWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import processing.schoolLists.extractors.helpers.ExtUtils;
import processing.schoolLists.extractors.helpers.HillsdaleParse;

import java.util.ArrayList;
import java.util.List;

public class HillsdaleExtractor implements Extractor {
    @Override
    public String abbreviation() {
        return OrganizationManager.HILLSDALE.getNameAbbr();
    }

    @Override
    @NotNull
    public List<CreatedSchool> extract(@NotNull Document document, @Nullable SchoolListProgressWindow progress) {
        List<CreatedSchool> list = new ArrayList<>();
        logHeader();

        // Get all the script tags, each of which contains a school
        Elements scriptTags = document.select("h2.page-title ~ script + script[type]");

        logPossibleCount(scriptTags.size(), progress);

        // Iterate through each script tag, extracting the attributes for each school
        for (Element scriptTag : scriptTags) {
            // Make a new school instance
            CreatedSchool school = new CreatedSchool(OrganizationManager.HILLSDALE);
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

            // Add the completed school, and log it
            incrementProgressBar(progress, school);
            list.add(school);
        }

        logParsedCount(list.size(), progress);
        return list;
    }
}
