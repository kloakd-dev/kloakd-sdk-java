package dev.kloakd.sdk.models;

import java.util.List;

/**
 * The page extras a crawl or crawl-extract can load
 * ({@code include_resources}; cost-control contract of 15 Nov 2026). They are
 * off by default: a page loads only its content (the document, scripts and
 * data), never these, unless the job includes them. The estimate's
 * {@code data().savingByCategoryMb()} shows what each would add.
 */
public final class ResourceTypes {
    /** Images (img, srcset, picture). */
    public static final String IMAGES = "images";
    /** Video and audio. */
    public static final String MEDIA = "media";
    /** Web fonts. */
    public static final String FONTS = "fonts";
    /** Stylesheets. */
    public static final String STYLESHEETS = "stylesheets";
    /** Requests to known tracker hosts. */
    public static final String TRACKING = "tracking";

    /** All five extras: a page then loads as it would in a browser. */
    public static final List<String> ALL = List.of(IMAGES, MEDIA, FONTS, STYLESHEETS, TRACKING);

    private ResourceTypes() {}
}
