package dev.kloakd.sdk.models;

import java.time.Duration;
import java.util.List;

/**
 * Options for {@code evadr().fetch}, {@code fetchAsync}, {@code startFetchJob}
 * and {@code fetchStream}.
 *
 * @param forceBrowser skip plain HTTP and start at a browser tier
 * @param useProxy use a stored proxy
 * @param sessionArtifactId an authenticated session (Fetchyr) to fetch with, or null
 * @param webhookUrl HTTPS URL notified when an asynchronous job ends (async calls only), or null
 * @param pollInterval first wait before polling a handed-off job, or null for the client's
 * @param pollTimeout limit on following a handed-off job, or null for the client's
 * @param includeResources the page extras a browser tier loads ({@code include_resources};
 *                         {@link ResourceTypes}): page extras are off by default on every browser
 *                         route, and null (unset) means none. Sent by {@code fetch}
 *                         ({@code POST evadr/fetch}) and by {@code startFetchJob}, {@code fetchAsync}
 *                         and {@code fetchStream} ({@code POST evadr/fetch/async})
 */
public record FetchOptions(
        boolean forceBrowser,
        boolean useProxy,
        String sessionArtifactId,
        String webhookUrl,
        Duration pollInterval,
        Duration pollTimeout,
        List<String> includeResources
) {
    /** The 0.7.0 constructor (no page extras). */
    public FetchOptions(boolean forceBrowser, boolean useProxy, String sessionArtifactId, String webhookUrl,
            Duration pollInterval, Duration pollTimeout) {
        this(forceBrowser, useProxy, sessionArtifactId, webhookUrl, pollInterval, pollTimeout, null);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean forceBrowser;
        private boolean useProxy;
        private String sessionArtifactId;
        private String webhookUrl;
        private Duration pollInterval;
        private Duration pollTimeout;
        private List<String> includeResources;

        private Builder() {}

        public Builder forceBrowser(boolean forceBrowser) { this.forceBrowser = forceBrowser; return this; }
        public Builder useProxy(boolean useProxy) { this.useProxy = useProxy; return this; }
        public Builder sessionArtifactId(String sessionArtifactId) { this.sessionArtifactId = sessionArtifactId; return this; }
        public Builder webhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; return this; }
        public Builder pollInterval(Duration pollInterval) { this.pollInterval = pollInterval; return this; }
        public Builder pollTimeout(Duration pollTimeout) { this.pollTimeout = pollTimeout; return this; }

        /** The page extras to load ({@link ResourceTypes}); unset means none (the default). */
        public Builder includeResources(List<String> includeResources) { this.includeResources = includeResources == null ? null : List.copyOf(includeResources); return this; }
        /** The page extras to load ({@link ResourceTypes}). */
        public Builder includeResources(String... includeResources) { return includeResources(List.of(includeResources)); }

        public FetchOptions build() {
            return new FetchOptions(forceBrowser, useProxy, sessionArtifactId, webhookUrl, pollInterval, pollTimeout,
                    includeResources);
        }
    }
}
