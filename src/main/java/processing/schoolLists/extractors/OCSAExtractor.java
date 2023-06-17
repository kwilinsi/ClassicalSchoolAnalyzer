package processing.schoolLists.extractors;

import constructs.organization.OrganizationManager;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import gui.windows.prompt.schoolMatch.SchoolListProgressWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.schoolLists.extractors.helpers.ExtUtils;

import java.util.ArrayList;
import java.util.List;

public class OCSAExtractor implements Extractor {
    private static final Logger logger = LoggerFactory.getLogger(OCSAExtractor.class);

    @Override
    public String abbreviation() {
        return OrganizationManager.OCSA.getNameAbbr();
    }

    @Override
    @NotNull
    public List<CreatedSchool> extract(@NotNull Document document, @Nullable SchoolListProgressWindow progress) {
        // Run the basic extraction process: applying a single function to a set of HTML elements
        return Extractor.processElements(this,
                document.select("div.et_pb_tab > div.et_pb_tab_content > p"),
                this::extractSingleSchool,
                progress
        );
    }

    /**
     * Extract a single {@link CreatedSchool} from an HTML {@link Element} containing the school info.
     *
     * @param el The HTML element.
     * @return The created school, or <code>null</code> if the element is basically empty (less than 10 characters).
     */
    @Nullable
    private CreatedSchool extractSingleSchool(@NotNull Element el) {
        // If the element has basically no contents, skip it
        if (el.text().length() < 10) return null;

        CreatedSchool school = new CreatedSchool(OrganizationManager.OCSA);

        // Get the school name
        school.put(Attribute.name, ExtUtils.validateName(ExtUtils.extHtmlStrAll(el, "a")));

        // Get the website Link
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
     * @return The formatted address.
     */
    @Nullable
    private static String formatAddress(@Nullable String address) {
        if (address == null) return null;
        address = address.replace(" • ", "");
        return ExtUtils.aliasNull(address);
    }
}
