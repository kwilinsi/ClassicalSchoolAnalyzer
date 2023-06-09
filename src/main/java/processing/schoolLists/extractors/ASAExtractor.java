package processing.schoolLists.extractors;

import constructs.organization.OrganizationManager;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.schoolLists.extractors.helpers.ExtUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ASAExtractor implements Extractor {
    private static final Logger logger = LoggerFactory.getLogger(ASAExtractor.class);

    /**
     * This pattern is used for extracting the email and phone number from the school's contact information.
     */
    private static final Pattern CONTACT_INFO_PATTERN = Pattern.compile(
            "Email: ?(.+@.+\\.\\w+)?[\\s\\S]*Phone: ?([()\\d -]+)?"
    );

    /**
     * Extract schools from the {@link OrganizationManager#ASA Anglican School Association} website.
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

        // Get the div tags that correspond to each school
        Elements schoolElements = document.select("div.et_pb_code_inner > div.member-info");

        // For each school div, extract the school information
        for (Element element : schoolElements)
            list.add(extractSingleSchool(element));

        return list;
    }

    /**
     * Extract a single {@link CreatedSchool} from an HTML {@link Element} containing the school info.
     *
     * @param el The HTML element.
     *
     * @return The created school.
     */
    @NotNull
    private CreatedSchool extractSingleSchool(@NotNull Element el) {
        CreatedSchool school = new CreatedSchool(OrganizationManager.ASA);

        // Set basic attributes of the school
        school.put(Attribute.name, ExtUtils.validateName(
                ExtUtils.extHtmlStr(el, "div.member-heading div.member-title a")
        ));
        school.put(Attribute.website_url,
                ExtUtils.extHtmlLink(el, "div.member-heading div.location-website a")
        );
        school.put(Attribute.grades_offered,
                ExtUtils.extHtmlStr(el, "div.member-grid div.grades p")
        );
        school.put(Attribute.bio,
                ExtUtils.extHtmlStr(el, "div.member-grid div.member-description p")
        );

        // Get the location, and split it into the city and state
        String location = ExtUtils.extHtmlStr(el, "div.member-heading div.location-website h4");
        if (location != null) {
            String[] locationSplit = location.split("[,|]");
            if (locationSplit.length > 1) {
                school.put(Attribute.city, ExtUtils.aliasNull(locationSplit[0]));
                school.put(Attribute.state, ExtUtils.aliasNull(locationSplit[1]));
            } else
                logger.warn("Failed to extract city and state from location '{}' for school '{}'",
                        location, school.name());
        } else
            logger.warn("No location found for school {}", school.name());

        // Get the contact information and identify the email and phone number
        String contact = ExtUtils.extHtmlStrAll(el, "div.member-grid div.contact p");
        if (contact != null) {
            Matcher matcher = CONTACT_INFO_PATTERN.matcher(contact);
            if (matcher.find()) {
                school.put(Attribute.email, ExtUtils.aliasNull(matcher.group(1)));
                school.put(Attribute.phone, ExtUtils.aliasNull(matcher.group(2)));
            } else
                logger.warn("Failed to extract email/phone from '{}' for school {}", contact, school.name());
        } else
            logger.warn("No contact information found for school {}", school.name());

        return school;
    }
}
