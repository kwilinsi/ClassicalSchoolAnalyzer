package processing.schoolLists.extractors;

import constructs.OrganizationManager;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.schoolLists.extractors.helpers.ExtUtils;

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

        // Get the p tags that correspond to each school
        Elements elements = document.select("div.et_pb_tab > div.et_pb_tab_content > p");

        // Create a school from each div
        for (Element element : elements) {
            // If the element has basically no contents, skip it
            if (element.text().length() < 10) continue;

            list.add(extractSingleSchool(element));
        }

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
        CreatedSchool school = new CreatedSchool(OrganizationManager.OCSA);

        // Get the school name
        school.put(Attribute.name, ExtUtils.validateName(ExtUtils.extHtmlStrAll(el, "a")));

        // Get the website URL
        school.put(Attribute.website_url, ExtUtils.extHtmlLink(el, "a"));

        List<TextNode> nodes = new ArrayList<>(el.textNodes());

        if (nodes.size() < 2) {
            logger.warn("Not enough text nodes for school {}.", school.name());
            return school;
        }

        // The first node is always the address
        String nodeTxt = nodes.remove(0).text();
        if (nodeTxt.startsWith("Academy address: ")) nodeTxt = nodeTxt.substring("Academy address: ".length());
        school.put(Attribute.address, formatAddress(nodeTxt));

        // The second node might be the mailing address. If not, it's the phone number.
        nodeTxt = nodes.remove(0).text();
        if (nodeTxt.startsWith("Mailing address: ")) {
            nodeTxt = nodeTxt.substring("Mailing address: ".length());
            school.put(Attribute.mailing_address, formatAddress(nodeTxt));
            nodeTxt = nodes.remove(0).text();
        }
        school.put(Attribute.phone, ExtUtils.aliasNull(nodeTxt));

        // If there's another node, it's the grades offered
        if (!nodes.isEmpty())
            school.put(Attribute.grades_offered, ExtUtils.aliasNull(nodes.remove(0).text()));

        return school;
    }

    /**
     * Process an OCSA address. They have a bullet point in them that becomes a question mark through {@link Jsoup}.
     * This removes that question mark. It also passes the input through {@link ExtUtils#aliasNull(String)}.
     *
     * @param address The input address to format.
     *
     * @return The formatted address.
     */
    @Nullable
    private static String formatAddress(@Nullable String address) {
        if (address == null) return null;
        address = address.replace(" • ", "");
        return ExtUtils.aliasNull(address);
    }
}
