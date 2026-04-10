package dev.kloakd.sdk.models;

import java.util.List;

/** A single page in the site hierarchy. */
public record PageNode(
        String url,
        int depth,
        String title,
        Integer statusCode,
        List<String> children
) {}
