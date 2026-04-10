package dev.kloakd.sdk;

import dev.kloakd.sdk.http.HttpTransport;
import dev.kloakd.sdk.modules.*;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * KLOAKD Java SDK client.
 *
 * <pre>{@code
 * Kloakd client = Kloakd.builder()
 *     .apiKey("sk-live-...")
 *     .organizationId("your-org-uuid")
 *     .build();
 * }</pre>
 */
public final class Kloakd {

    static final String DEFAULT_BASE_URL = "https://api.kloakd.dev";
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    static final int DEFAULT_MAX_RETRIES = 3;

    private final EvadrNamespace evadr;
    private final WebgrphNamespace webgrph;
    private final SkanyrNamespace skanyr;
    private final NexusNamespace nexus;
    private final ParlyrNamespace parlyr;
    private final FetchyrNamespace fetchyr;
    private final KolektrNamespace kolektr;

    private Kloakd(Builder builder) {
        String baseUrl = builder.baseUrl != null ? builder.baseUrl : DEFAULT_BASE_URL;
        Duration timeout = builder.timeout != null ? builder.timeout : DEFAULT_TIMEOUT;
        int maxRetries = builder.maxRetries >= 0 ? builder.maxRetries : DEFAULT_MAX_RETRIES;

        var transport = new HttpTransport(
                builder.apiKey,
                builder.organizationId,
                baseUrl,
                timeout,
                maxRetries,
                builder.httpClient);

        this.evadr   = new EvadrNamespace(transport);
        this.webgrph = new WebgrphNamespace(transport);
        this.skanyr  = new SkanyrNamespace(transport);
        this.nexus   = new NexusNamespace(transport);
        this.parlyr  = new ParlyrNamespace(transport);
        this.fetchyr = new FetchyrNamespace(transport);
        this.kolektr = new KolektrNamespace(transport);
    }

    public EvadrNamespace evadr()   { return evadr; }
    public WebgrphNamespace webgrph() { return webgrph; }
    public SkanyrNamespace skanyr() { return skanyr; }
    public NexusNamespace nexus()   { return nexus; }
    public ParlyrNamespace parlyr() { return parlyr; }
    public FetchyrNamespace fetchyr() { return fetchyr; }
    public KolektrNamespace kolektr() { return kolektr; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String apiKey;
        private String organizationId;
        private String baseUrl;
        private Duration timeout;
        private int maxRetries = -1;
        private HttpClient httpClient;

        private Builder() {}

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** 0 = one attempt, no retries. Default: 3. */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /** Inject a custom HttpClient (useful for testing). */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Kloakd build() {
            if (apiKey == null || apiKey.isBlank())
                throw new IllegalArgumentException("apiKey is required");
            if (organizationId == null || organizationId.isBlank())
                throw new IllegalArgumentException("organizationId is required");
            return new Kloakd(this);
        }
    }
}
