import dev.kloakd.sdk.Kloakd;

import java.util.Map;

/**
 * Quickstart: fetch, crawl and extract from a site.
 *
 * <p>Not part of the published jar. Run it with the SDK and Jackson on the
 * class path (Java 21 launches a single source file directly):
 *
 * <pre>
 * export KLOAKD_API_KEY=sk-live-...
 * export KLOAKD_ORG_ID=your-org-uuid
 * mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
 * java -cp target/classes:$(cat cp.txt) examples/Quickstart.java
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

        // Step 2: Site hierarchy (crawl() waits for the crawl and returns its pages)
        var crawl = client.webgrph().crawl(targetUrl, 2, 50, false, null);
        System.out.printf("Crawl %s: %d pages%n", crawl.status(), crawl.pages().size());

        // Step 3: Extract structured data, reusing the Evadr artifact.
        // The shorthand {"field": "css:selector"} is sent as {"fields": [{name, selector}]}.
        var data = client.kolektr().extract(targetUrl,
                Map.of("title", "css:h3 a", "price", "css:p.price_color"),
                fetch.artifactId(), null, null);
        System.out.printf("Extracted %d records%n", data.totalRecords());

        data.records().stream().limit(3).forEach(System.out::println);
    }
}
