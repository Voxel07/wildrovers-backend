package tools;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Sanitizes user-submitted HTML to prevent stored XSS attacks.
 * Allows a safe forum subset of HTML tags while stripping all scripts,
 * event handlers, javascript: hrefs, and data: image URIs.
 *
 * Uses OWASP Java HTML Sanitizer with allowStandardUrlProtocols() which
 * permits only http/https/ftp and blocks javascript: and data: URIs.
 */
@ApplicationScoped
public class HtmlSanitizer {

    /**
     * Forum-safe HTML policy built from OWASP pre-built policies plus
     * custom heading/code/blockquote elements.
     * allowStandardUrlProtocols() strips javascript: and data: URIs automatically.
     */
    private static final PolicyFactory FORUM_POLICY =
        Sanitizers.FORMATTING          // b, i, u, s, em, strong, strike, sub, sup, tt
        .and(Sanitizers.BLOCKS)        // p, div, h1-h6, blockquote, pre, br, hr, ul, ol, li
        .and(Sanitizers.LINKS)         // a[href] — http/https only
        .and(Sanitizers.IMAGES)        // img[src] — http/https only, no data: URIs
        .and(Sanitizers.TABLES)        // table, thead, tbody, tr, td, th
        .and(Sanitizers.STYLES)        // style attribute (safe subset)
        .and(new HtmlPolicyBuilder()   // extra tags Quill uses
            .allowElements("code", "pre", "span", "sub", "sup")
            .allowAttributes("class").globally()
            .allowStandardUrlProtocols()
            .toFactory());

    /**
     * Sanitize an HTML string. Returns the input unchanged if null or blank.
     */
    public String sanitize(String html) {
        if (html == null || html.isBlank()) return html;
        return FORUM_POLICY.sanitize(html);
    }

    /**
     * Strip ALL HTML from a plain-text field (titles, topic names).
     * No tags allowed — only the text content is preserved.
     */
    public String sanitizeTitle(String title) {
        if (title == null || title.isBlank()) return title;
        return new HtmlPolicyBuilder().toFactory().sanitize(title).trim();
    }
}
