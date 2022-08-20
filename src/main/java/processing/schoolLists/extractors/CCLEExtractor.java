package processing.schoolLists.extractors;

import constructs.OrganizationManager;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.schoolLists.extractors.helpers.ExtUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CCLEExtractor implements Extractor {
    private static final Logger logger = LoggerFactory.getLogger(CCLEExtractor.class);

    /**
     * This pattern matches a phone number.
     */
    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("(\\(?\\d{3}\\)?\\D\\d{3}\\D\\d{4})");

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

        // Get the div tags that correspond to each school
        Elements elements = document.select(
                "div.elementor-widget-container section.elementor-section > " +
                "div.elementor-container div.elementor-widget-text-editor > div.elementor-widget-container"
        );

        // Create a school from each div
        for (Element element : elements)
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
        CreatedSchool school = new CreatedSchool(OrganizationManager.CCLE);

        // Set basic attributes
        school.put(Attribute.name, ExtUtils.validateName(ExtUtils.extHtmlStr(el, "h5")));

        // Get the link tags. For some schools, there is one link, which goes to the school's website. For others,
        // there are two, one of which is the email address
        Elements links = el.select("a");
        if (links.size() == 0)
            logger.warn("No links found for school {}", school.name());
        else if (links.size() == 1)
            school.put(Attribute.website_url, ExtUtils.aliasNullLink(links.get(0).attr("href")));
        else {
            // Get the text for both. The one with an @ is the email address.
            String text1 = links.get(0).text();
            String text2 = links.get(1).text();
            boolean firstIsEmail = text1.contains("@");
            school.put(Attribute.email, ExtUtils.aliasNull(firstIsEmail ? text1 : text2));
            school.put(Attribute.website_url,
                    ExtUtils.aliasNullLink(links.get(firstIsEmail ? 1 : 0).attr("href"))
            );
        }

        // Get the address and other info in the body
        List<TextNode> nodes = el.select("p").textNodes();

        if (nodes.size() < 2) {
            logger.warn("Expected more than {} nodes in text for school {}", nodes.size(), school.name());
            return school;
        }

        // The first two nodes are the address
        String address = nodes.remove(0).text() + " " + nodes.remove(0).text();
        school.put(Attribute.address, ExtUtils.aliasNull(address));

        // Check the other nodes for a phone or fax number
        for (TextNode node : nodes) {
            String text = ExtUtils.aliasNull(node.text());
            if (text == null) continue;

            Matcher matcher = PHONE_NUMBER_PATTERN.matcher(text);
            if (matcher.find()) {
                if (text.contains("fax"))
                    school.put(Attribute.fax_number, matcher.group(1));
                else
                    school.put(Attribute.phone, matcher.group(1));
            } else
                logger.warn("Expected a phone/fax number in node '{}' for school {}", text, school.name());
        }

        return school;
    }
}
