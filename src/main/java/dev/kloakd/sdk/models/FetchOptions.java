package dev.kloakd.sdk.models;

import java.time.Duration;

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
 */
public record FetchOptions(
        boolean forceBrowser,
        boolean useProxy,
        String sessionArtifactId,
        String webhookUrl,
        Duration pollInterval,
        Duration pollTimeout
) {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean forceBrowser;
        private boolean useProxy;
        private String sessionArtifactId;
        private String webhookUrl;
        private Duration pollInterval;
        private Duration pollTimeout;

        private Builder() {}

        public Builder forceBrowser(boolean forceBrowser) { this.forceBrowser = forceBrowser; return this; }
        public Builder useProxy(boolean useProxy) { this.useProxy = useProxy; return this; }
        public Builder sessionArtifactId(String sessionArtifactId) { this.sessionArtifactId = sessionArtifactId; return this; }
        public Builder webhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; return this; }
        public Builder pollInterval(Duration pollInterval) { this.pollInterval = pollInterval; return this; }
        public Builder pollTimeout(Duration pollTimeout) { this.pollTimeout = pollTimeout; return this; }

        public FetchOptions build() {
            return new FetchOptions(forceBrowser, useProxy, sessionArtifactId, webhookUrl, pollInterval, pollTimeout);
        }
    }
}
