package dev.kloakd.sdk.models;

import java.util.List;

/**
 * A single page in the site hierarchy.
 *
 * @param url fully qualified URL of this page
 * @param depth depth level of this page in the crawl tree
 * @param title page title extracted from the HTML head
 * @param statusCode HTTP status code returned when this page was fetched
 * @param children list of child page URLs linked from this page
 */
public record PageNode(
        String url,
        int depth,
        String title,
        Integer statusCode,
        List<String> children
) {}
