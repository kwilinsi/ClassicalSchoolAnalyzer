package constructs.organization;

import constructs.school.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import processing.schoolLists.extractors.*;

import java.util.ArrayList;
import java.util.List;

public class OrganizationManager {
    /**
     * <b>Name:</b> Association of Classical Christian Schools
     * <br><b>Name Abbreviation:</b> ACCS
     * <br><b>Homepage Link:</b> <a href="https://classicalchristian.org">website</a>
     * <br><b>School List Link:</b> <a href="https://classicalchristian.org/find-a-school/">school list</a>
     * <br><b>Additional Indirect Matching Attributes:</b> {@link Attribute#accs_page_url accs_page_url}
     * <br><b>Additional Relevant Matching Attributes:</b> <i>N/A</i>
     */
    public static final Organization ACCS = new Organization(
            1,
            "Association of Classical Christian Schools",
            "ACCS",
            "https://classicalchristian.org",
            "https://classicalchristian.org/find-a-school/",
            indAttr(Attribute.accs_page_url),
            relAttr(Attribute.grades_offered),
            new ACCSExtractor()
    );

    /**
     * <b>Name:</b> Great Hearts Institute
     * <br><b>Name Abbreviation:</b> GHI
     * <br><b>Homepage Link:</b> <a href="https://greathearts.institute">website</a>
     * <br><b>School List Link:</b>
     * <a href="https://static.batchgeo.com/map/json/f0a726285be76dc6dc336e561b0726e6/1654008594?_=1660413403330">
     * school list</a>
     * <br><b>Additional Indirect Matching Attributes:</b> {@link Attribute#latitude latitute} and
     * {@link Attribute#longitude longitude}
     * <br><b>Additional Relevant Matching Attributes:</b> <i>N/A</i>
     * <p><br>
     * <b>Notes:</b> This organization was formerly known as the "Institute for Classical Education". During the
     * development of this project, they rebranded to the Great Hearts Institute.
     * <p>
     * This organization does not have an easily "parsable" list of schools. Their actual school list page can be found
     * <a href="https://greathearts.institute/communities-networking/find-a-school/">here</a>. This forwards to a
     * fullscreen
     * <a href="https://batchgeo.com/map/f0a726285be76dc6dc336e561b0726e6">batchgeo page</a>
     * that shows the schools at the bottom. But those schools are loaded through JavaScript, so Jsoup can't read them.
     * Instead, inspecting the network tab reveals a link to
     * <a href="https://static.batchgeo.com/map/json/f0a726285be76dc6dc336e561b0726e6/1654008594?_=1656448797243">this
     * page</a>, which contains JSON data for the list of schools. This will probably break in the future, though, as
     * new schools are added.
     */
    public static final Organization GHI = new Organization(
            2,
            "Great Hearts Institute",
            "GHI",
            "https://greathearts.institute",
            "https://static.batchgeo.com/map/json/f0a726285be76dc6dc336e561b0726e6/1654008594?_=1660413403330",
            indAttr(Attribute.latitude, Attribute.longitude),
            relAttr(Attribute.grades_offered),
            new GHIExtractor()
    );

    /**
     * <b>Name:</b> Hillsdale Classical Schools
     * <br><b>Name Abbreviation:</b> HILLSDALE
     * <br><b>Homepage Link:</b> <a href="https://k12.hillsdale.edu">website</a>
     * <br><b>School List Link:</b>
     * <a href="https://k12.hillsdale.edu/Schools/Affiliate-Classical-Schools/">school list</a>
     * <br><b>Additional Indirect Matching Attributes:</b> <i>N/A</i>
     * <br><b>Additional Relevant Matching Attributes:</b> <i>N/A</i>
     */
    public static final Organization HILLSDALE = new Organization(
            3,
            "Hillsdale Classical Schools",
            "Hillsdale",
            "https://k12.hillsdale.edu",
            "https://k12.hillsdale.edu/Schools/Affiliate-Classical-Schools/",
            indAttr(),
            relAttr(),
            new HillsdaleExtractor()
    );

    /**
     * <b>Name</b>: Institute for Catholic Liberal Education
     * <br><b>Name Abbreviation:</b> ICLE
     * <br><b>Homepage Link:</b> <a href="https://catholicliberaleducation.org">website</a>
     * <br><b>School List Link:</b> <a href="org">school list</a>
     * <br><b>Additional Indirect Matching Attributes:</b> {@link Attribute#icle_page_url icle_page_url}
     * <br><b>Additional Relevant Matching Attributes:</b> <i>N/A</i>
     */
    public static final Organization ICLE = new Organization(
            4,
            "Institute for Catholic Liberal Education",
            "ICLE",
            "https://catholicliberaleducation.org",
            "https://my.catholicliberaleducation.org/schools/",
            indAttr(Attribute.icle_page_url),
            relAttr(),
            new ICLEExtractor()
    );

    /**
     * <b>Name</b>: Anglican School Association
     * <br><b>Name Abbreviation:</b> ASA
     * <br><b>Homepage Link:</b> <a href="https://anglicanschools.org">website</a>
     * <br><b>School List Link:</b> <a href="https://anglicanschools.org/members/">school list</a>
     * <br><b>Additional Indirect Matching Attributes:</b> {@link Attribute#email email}
     * <br><b>Additional Relevant Matching Attributes:</b> <i>N/A</i>
     */
    public static final Organization ASA = new Organization(
            5,
            "Anglican School Association",
            "ASA",
            "https://anglicanschools.org",
            "https://anglicanschools.org/members/",
            indAttr(Attribute.email),
            relAttr(),
            new ASAExtractor()
    );

    /**
     * <b>Name</b>: Consortium for Classical Lutheran Education
     * <br><b>Name Abbreviation:</b> CCLE
     * <br><b>Homepage Link:</b> <a href="http://www.ccle.org">website</a>
     * <br><b>School List Link:</b> <a href="http://www.ccle.org/directory/">school list</a>
     * <br><b>Additional Indirect Matching Attributes:</b> <i>N/A</i>
     * <br><b>Additional Relevant Matching Attributes:</b> <i>N/A</i>
     */
    public static final Organization CCLE = new Organization(
            6,
            "Consortium for Classical Lutheran Education",
            "CCLE",
            "http://www.ccle.org",
            "http://www.ccle.org/directory/",
            indAttr(),
            relAttr(),
            new CCLEExtractor()
    );

    /**
     * <b>Name</b>: Orthodox Christian School Association
     * <br><b>Name Abbreviation:</b> OCSA
     * <br><b>Homepage Link:</b> <a href="https://www.orthodoxschools.org">website</a>
     * <br><b>School List Link:</b> <a href="https://www.orthodoxschools.org/directory-of-schools/">school list</a>
     * <br><b>Additional Indirect Matching Attributes:</b> <i>Removed</i> {@link Attribute#phone phone} from defaults.
     * <br><b>Additional Relevant Matching Attributes:</b> <i>Added</i> {@link Attribute#phone phone}
     * <p><br>
     * <b>Notes:</b> This organization is not explicitly classical. However, it's hard to conceive of a truly
     * Orthodox school that is not at least somewhat classical; thus, it is included here.
     */
    public static final Organization OCSA = new Organization(
            7,
            "Orthodox Christian School Association",
            "OCSA",
            "https://www.orthodoxschools.org",
            "https://www.orthodoxschools.org/directory-of-schools/",
            indAttr(Attribute.phone),
            relAttr(Attribute.phone),
            new OCSAExtractor()
    );

    /**
     * This is the complete list of all organizations supported by this program. It contains an {@link Organization}
     * object for each of the organizations. No other objects should be created for any organization at any time during
     * the program execution.
     * <p>
     * These objects may be modified as more data is obtained for each organization, such as downloading the
     * school_list_page_file. However, the list itself is immutable.
     */
    @Unmodifiable
    @NotNull
    public static final List<Organization> ORGANIZATIONS = List.of(ACCS, GHI, HILLSDALE, ICLE, ASA, CCLE, OCSA);

    /**
     * Generate an array of {@link Attribute Attributes} for the
     * {@link Organization#getMatchIndicatorAttributes() matchIndicatorAttributes} of an organization. This
     * automatically includes the default list of attributes; any attributes passed as parameters are added to this
     * list. The following attributes are included by default:
     * <ul>
     *     <li>{@link Attribute#website_url}</li>
     *     <li>{@link Attribute#address}</li>
     *     <li>{@link Attribute#phone}</li>
     * </ul>
     * <p>
     * If any attributes are provided that are already added by default (in the list above), they will be
     * <i>removed</i>. Thus, the incoming attribute list acts as a list of changes, rather than a list of additions.
     *
     * @param attributes Zero or more attributes to change. If they are already added, they are removed; if they aren't
     *                   already included, they will be added. Do <i>not</i> include the same attribute twice; the
     *                   behavior cannot be guaranteed.
     * @return An array of all match indicator attributes.
     */
    private static Attribute[] indAttr(Attribute... attributes) {
        List<Attribute> attrList = new ArrayList<>();
        attrList.add(Attribute.website_url);
        attrList.add(Attribute.address);
        attrList.add(Attribute.phone);

        for (Attribute a : attributes)
            if (!attrList.contains(a))
                attrList.add(a);
            else
                attrList.remove(a);

        return attrList.toArray(new Attribute[0]);
    }

    /**
     * Generate an array of {@link Attribute Attributes} for the
     * {@link Organization#getMatchRelevantAttributes() matchRelevantAttributes} of an organization. This automatically
     * includes the following list of default attributes:
     * <ul>
     *     <li>{@link Attribute#name}</li>
     *     <li>{@link Attribute#is_excluded}</li>
     *     <li>{@link Attribute#excluded_reason}</li>
     * </ul>
     * <p>
     * If any attributes are provided that are already added by default (in the list above), they will be
     * <i>removed</i>. Thus, the incoming attribute list acts as a list of changes, rather than a list of additions.
     *
     * @param attributes Zero or more attributes to change. If they are already added, they are removed; if they aren't
     *                   already included, they will be added. Do <i>not</i> include the same attribute twice; the
     *                   behavior cannot be guaranteed.
     * @return An array of all match relevant attributes.
     */
    private static Attribute[] relAttr(Attribute... attributes) {
        List<Attribute> attrList = new ArrayList<>();
        attrList.add(Attribute.name);
        attrList.add(Attribute.is_excluded);
        attrList.add(Attribute.excluded_reason);

        for (Attribute a : attributes)
            if (!attrList.contains(a))
                attrList.add(a);
            else
                attrList.remove(a);

        return attrList.toArray(new Attribute[0]);
    }
}
