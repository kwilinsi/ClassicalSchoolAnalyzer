package utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * This class contains utility methods for handling {@link URL URLs}.
 */
public class URLUtils {
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
     * @param urlA The first URL to compare.
     * @param urlB The second URL to compare.
     *
     * @return <code>True</code> if and only if the URLs are equal; <code>false</code> otherwise.
     */
    public static boolean equals(@NotNull URL urlA, @NotNull URL urlB) {
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
     * @param urlA The first URL as a string.
     * @param urlB The second URL as a string.
     *
     * @return <code>True</code> if and only if the URLs have the same host; <code>false</code> otherwise.
     */
    public static boolean hostEquals(@Nullable String urlA, @Nullable String urlB) {
        // If both URLs are null, they're equal; if only one is null, they're not equal
        if (urlA == null && urlB == null) return true;
        if (urlA == null || urlB == null) return false;

        try {
            URL urlObjA = new URL(urlA);
            URL urlObjB = new URL(urlB);
            String hostA = urlObjA.getHost();
            String hostB = urlObjB.getHost();

            // Check for null hostnames using the same procedure as before
            if (hostA == null && hostB == null) return false;
            if (hostA == null || hostB == null) return false;

            hostA = hostA.toLowerCase(Locale.ROOT);
            hostB = hostB.toLowerCase(Locale.ROOT);

            // Check for equal hostnames
            if (hostA.equals(hostB)) return true;
            if (hostA.startsWith("www.")) hostA = hostA.substring(4);
            if (hostB.startsWith("www.")) hostB = hostB.substring(4);
            return hostA.equals(hostB);

        } catch (MalformedURLException e) {
            // If the URLs are malformed, they're only equal when the string representations are the same
            return urlA.equals(urlB);
        }
    }

    /**
     * This method takes a URL as a {@link String} and attempts to coerce it into a proper {@link URL} object.
     * Sometimes, this may result in a {@link MalformedURLException}. In that case, various attempts are made to fix the
     * url. If this is successful, a URL object is returned; otherwise <code>null</code> is returned. This method will
     * not throw exceptions.
     * <p>
     * This method performs the following steps:
     * <ol>
     *     <li>If the URL is <code>null</code> or a {@link String#isBlank() blank} string, return <code>null</code>.
     *     <li>If the URL starts with multiple protocols (http/https), attempt to remove the outer ones.
     *     <li>Attempt to parse the URL. If this fails, check for a missing protocol. Add http:// if missing.
     *     <li>Attempt again to parse the URL. If this fails, return <code>null</code>.
     * </ol>
     *
     * @param url The url as a string. If this is <code>null</code>, <code>null</code> is immediately returned.
     *
     * @return A {@link URL} object, or <code>null</code> if the url could not be processed.
     */
    @Nullable
    public static URL createURL(@Nullable String url) {
        if (url == null || url.isBlank()) return null;

        // A URL with multiple protocols will process correctly. So check for this first, and remove the outer
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
            url = "http://" + url;
        }

        try {
            return new URL(url);
        } catch (MalformedURLException ignore) {
        }

        return null;
    }
}
