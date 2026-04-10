package dev.kloakd.examples;

import dev.kloakd.sdk.Kloakd;

import java.util.Map;

/**
 * Quickstart: Discover and extract from a site in ~10 lines.
 * This scenario is identical across all 4 KLOAKD SDKs per the design document §13.
 *
 * <pre>
 * export KLOAKD_API_KEY=sk-live-...
 * export KLOAKD_ORG_ID=your-org-uuid
 * mvn exec:java -Dexec.mainClass=dev.kloakd.examples.Quickstart
 * </pre>
 */
public class Quickstart {

    public static void main(String[] args) {
        var client = Kloakd.builder()
                .apiKey(System.getenv("KLOAKD_API_KEY"))
                .organizationId(System.getenv("KLOAKD_ORG_ID"))
                .build();

        String targetUrl = "https://books.toscrape.com";

        // Step 1: Anti-bot fetch
        var fetch = client.evadr().fetch(targetUrl);
        System.out.printf("Fetched via tier %d, anti-bot bypassed=%b%n",
                fetch.tierUsed(), fetch.antiBotBypassed());

        // Step 2: Site hierarchy (reuse Evadr artifact)
        var crawl = client.webgrph().crawl(targetUrl, 2, 50, false, fetch.artifactId(), 0, 0);
        System.out.printf("Found %d pages%n", crawl.totalPages());

        // Step 3: Extract structured data (reuse Evadr artifact)
        var data = client.kolektr().page(targetUrl,
                Map.of("title", "css:h3 a", "price", "css:p.price_color"),
                fetch.artifactId(), null, null, 0, 0);
        System.out.printf("Extracted %d records%n", data.totalRecords());

        data.records().stream().limit(3).forEach(System.out::println);
    }
}
