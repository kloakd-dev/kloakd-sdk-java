package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * A stored page from a fetch ({@code GET artifacts/{artifact_id}}; cost-control
 * contract of 15 Nov 2026, section 8). Only fetched-content artifacts of your
 * organisation are served ({@link #FETCHED_CONTENT} and
 * {@link #DEGRADED_FETCHED_CONTENT}); session and cookie artifacts, another
 * organisation's, unknown ids and ids with no HTML answer 404
 * {@code {"detail": "Artifact not found"}}. Its HTML comes from
 * {@code client.downloadArtifact(id)}.
 *
 * @param id          the artifact's id (a UUID)
 * @param type        {@link #FETCHED_CONTENT} or {@link #DEGRADED_FETCHED_CONTENT}
 * @param url         the page it was fetched from, or null
 * @param createdAt   when it was stored (ISO 8601), or null
 * @param sizeBytes   its size in bytes, or null when not sent
 * @param contentType its media type, or null
 * @param finalUrl    the URL after redirects ({@code final_url}), or null
 * @param statusCode  the HTTP status the page answered with ({@code status_code}), or null
 * @param verified    true when the fetch was verified as the real page ({@code verified})
 * @param contentUrl  the API path of its HTML ({@code content_url}), or null
 */
public record Artifact(String id, String type, String url, String createdAt, Long sizeBytes, String contentType,
        String finalUrl, Integer statusCode, boolean verified, String contentUrl) {

    /** A page fetched in full. */
    public static final String FETCHED_CONTENT = "FETCHED_CONTENT";
    /** A page fetched in a degraded form (for example a fallback fetch). */
    public static final String DEGRADED_FETCHED_CONTENT = "DEGRADED_FETCHED_CONTENT";

    /**
     * Read the metadata from the API's answer.
     *
     * @param m the answer's body
     * @return the artifact
     */
    public static Artifact from(Map<String, Object> m) {
        return new Artifact(str(m, "id"), str(m, "type"), str(m, "url"), str(m, "created_at"),
                m.get("size_bytes") instanceof Number n ? n.longValue() : null, str(m, "content_type"),
                str(m, "final_url"), m.get("status_code") instanceof Number n ? n.intValue() : null,
                Boolean.TRUE.equals(m.get("verified")), str(m, "content_url"));
    }

    private static String str(Map<String, Object> m, String k) {
        return m.get(k) instanceof String s ? s : null;
    }
}
