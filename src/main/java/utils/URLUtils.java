package utils;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * This class contains utility methods for handling {@link URL URLs}.
 */
public class URLUtils {
    private static final Logger logger = LoggerFactory.getLogger(URLUtils.class);

    /**
     * This pattern helps remove multiple <code>http://</code> and <code>https://</code> prefix from malformed URLs.
     *
     * @see #createURL(String)
     */
    private static final Pattern MULTIPLE_PROTOCOLS_PATTERN = Pattern.compile("^(https?://)(?=https?://)");

    /**
     * Check whether two {@link URL URLs} are the same.
     * <p>
     * This is different from checking their full string representations:
     * <ul>
     *     <li>Two URLs are considered equal if they have the same host, file, and query.
     *     <li>The protocol (http/https) and port are ignored.
     *     <li>If the query is <code>null</code> or empty, trailing slashes after the file are ignored.
     *     <li>The <code>www.</code> prefix in hostnames is ignored.
     *     <li>Hostnames are case-insensitive.
     * </ul>
     *
     * @param urlA The first Link to compare.
     * @param urlB The second Link to compare.
     * @return <code>True</code> if and only if the URLs are equal; <code>false</code> otherwise.
     */
    public static boolean equals(@Nullable URL urlA, @Nullable URL urlB) {
        // If both URLs are null, they're equal; if only one is null, they're not equal
        if (urlA == null && urlB == null) return true;
        if (urlA == null || urlB == null) return false;

        // Check hostname
        if (!urlA.getHost().equalsIgnoreCase(urlB.getHost())) {
            // If they're not equal, attempt to remove www. and try again
            String hostA = urlA.getHost().startsWith("www.") ? urlA.getHost().substring(4) : urlA.getHost();
            String hostB = urlB.getHost().startsWith("www.") ? urlB.getHost().substring(4) : urlB.getHost();
            if (!hostA.equalsIgnoreCase(hostB)) return false;
        }

        // Check query
        if (!Objects.equals(urlA.getQuery(), urlB.getQuery())) {
            return false;
        }

        // Check file name
        if (!urlA.getFile().equals(urlB.getFile())) {
            // If they're not equal, and the query is empty, attempt to remove trailing slashes from the file names and
            // try again
            if (urlA.getQuery() == null || urlA.getQuery().isEmpty()) {
                String fileA = urlA.getFile();
                String fileB = urlB.getFile();
                if (fileA.endsWith("/")) fileA = fileA.substring(0, fileA.length() - 1);
                if (fileB.endsWith("/")) fileB = fileB.substring(0, fileB.length() - 1);
                return fileA.equals(fileB);
            }
            return false;
        }

        return true;
    }

    /**
     * Determine whether two {@link URL URLs} have the same domain (host and subdomains). This is case-insensitive and
     * ignores the '<code>www.</code>' prefix.
     *
     * @param urlA The first URL.
     * @param urlB The second URL.
     * @return <code>True</code> if and only if the URLs have the same host; <code>false</code> otherwise.
     */
    public static boolean hostEquals(@Nullable URL urlA, @Nullable URL urlB) {
        // If both URLs are null, they're equal; if only one is null, they're not equal
        if (urlA == null && urlB == null) return true;
        if (urlA == null || urlB == null) return false;

        String hostA = urlA.getHost();
        String hostB = urlB.getHost();

        // Check for null hostnames
        if (hostA == null && hostB == null) return false;
        if (hostA == null || hostB == null) return false;

        hostA = hostA.toLowerCase(Locale.ROOT);
        hostB = hostB.toLowerCase(Locale.ROOT);
        while (hostA.startsWith("www.")) hostA = hostA.substring(4);
        while (hostB.startsWith("www.")) hostB = hostB.substring(4);

        return hostA.equals(hostB);
    }

    /**
     * Extract just the domain from a URL. This excludes subdomains.
     * <p>
     * The extraction is done by removing any match for the following Regex from the hostname:
     * <br><code>"^.*\.(?=.*\.)"</code>
     * <p>
     * Note that for URLs like <code>"alice.blogs.website.k12.us"</code> this will return <code>"k12.us"</code>, even
     * though <code>"website.k12.us"</code> might be desired.
     *
     * @param urlStr The URL from which to extract the domain.
     * @return The domain, or <code>null</code> if the input is <code>null</code>, {@link #createURL(String)
     * un-parseable}, or has no {@link URL#getHost() host}.
     */
    @Nullable
    public static String getDomain(@Nullable String urlStr) {
        URL url = createURL(urlStr);
        if (url == null) return null;

        String host = url.getHost();
        if (host == null) return null;

        return host.toLowerCase(Locale.ROOT).replaceAll("^.*\\.(?=.*\\.)", "");
    }

    /**
     * This method takes a Link as a {@link String} and attempts to coerce it into a proper {@link URL} object.
     * Sometimes, this may result in a {@link MalformedURLException}. In that case, various attempts are made to fix the
     * url. If this is successful, a Link object is returned; otherwise <code>null</code> is returned. This method will
     * not throw exceptions.
     * <p>
     * This method performs the following steps:
     * <ol>
     *     <li>If the Link is <code>null</code> or a {@link String#isBlank() blank} string, return <code>null</code>.
     *     <li>If the Link starts with multiple protocols (http/https), attempt to remove the outer ones.
     *     <li>Attempt to parse the Link. If this fails, check for a missing protocol. Add https:// if missing.
     *     <li>Attempt again to parse the Link. If this fails, return <code>null</code>.
     * </ol>
     *
     * @param url The url as a string. If this is <code>null</code>, <code>null</code> is immediately returned.
     * @return A {@link URL} object, or <code>null</code> if the url could not be processed.
     */
    @Nullable
    public static URL createURL(@Nullable String url) {
        if (url == null || url.isBlank()) return null;

        // A Link with multiple protocols will process correctly. So check for this first, and remove the outer
        // protocol(s) if more than one are present.
        while (MULTIPLE_PROTOCOLS_PATTERN.matcher(url).find()) {
            url = MULTIPLE_PROTOCOLS_PATTERN.matcher(url).replaceAll("");
        }

        try {
            return new URL(url);
        } catch (MalformedURLException ignore) {
        }

        // Check for missing protocol
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }

        try {
            return new URL(url);
        } catch (MalformedURLException ignore) {
        }

        return null;
    }

    /**
     * Attempt to strip the page and query data from a URL, returning just the protocol and hostname.
     *
     * @param urlStr The URL to strip.
     * @return The stripped URL, or <code>null</code> if the input is <code>null</code> or {@link #createURL(String)
     * un-parseable}.
     */
    @Nullable
    public static String stripPageData(@Nullable String urlStr) {
        URL url = createURL(urlStr);
        if (url == null) return null;

        // Recreate the URL without the file data
        try {
            url = new URL(url.getProtocol(), url.getHost(), url.getPort(), "");
        } catch (MalformedURLException e) {
            logger.warn("Unexpected error while stripping page data from '" + url + "'", e);
        }

        return url.toExternalForm();
    }

    /**
     * Attempt to normalize a Link to a relatively standard format. Though this method may semantically change the
     * Link, this is deemed necessary, as most URLs are malformed on the original organization pages anyway.
     * <p>
     * The following steps are performed to normalize the input Link:
     * <ol>
     *     <li>Make the hostname lowercase
     *     <li>Change <code>"http://"</code> to <code>"https://"</code>
     *     <li>Remove <code>"www."</code> on hostnames
     * </ol>
     * It's suggested to first convert the Link from a string with {@link #createURL(String)}.
     *
     * @param url The Link to normalize.
     * @return The normalized Link, or <code>null</code> if the input is <code>null</code>.
     */
    @Nullable
    public static String normalize(@Nullable URL url) {
        if (url == null) return null;

        try {
            // Make the host lowercase
            String host = url.getHost().toLowerCase(Locale.ROOT);

            // Remove www
            while (host.startsWith("www."))
                host = host.substring(4);

            // Make the protocol lowercase
            String protocol = url.getProtocol().toLowerCase(Locale.ROOT);

            // Replace http with https
            if ("http".equals(protocol))
                protocol = "https";

            url = new URL(protocol, host, url.getPort(), url.getFile());

        } catch (MalformedURLException e) {
            logger.warn("Unexpected error while recreating '" + url + "' during normalization", e);
        }

        return url.toExternalForm();
    }
}
