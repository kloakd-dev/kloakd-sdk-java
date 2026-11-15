package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * A stored page from a fetch ({@code GET artifacts/{artifact_id}}; cost-control
 * contract of 15 Nov 2026, section 8). Only fetched-content artifacts of your
 * organisation are served; session and cookie artifacts answer 404. Its HTML
 * comes from {@code client.downloadArtifact(id)}.
 *
 * @param id          the artifact's id
 * @param type        the artifact's type (fetched content)
 * @param url         the page it was fetched from, or null
 * @param createdAt   when it was stored (ISO 8601), or null
 * @param sizeBytes   its size in bytes, or null when not sent
 * @param contentType its media type, or null
 */
public record Artifact(String id, String type, String url, String createdAt, Long sizeBytes, String contentType) {

    /** Read the metadata from the API's answer. */
    public static Artifact from(Map<String, Object> m) {
        return new Artifact(str(m, "id"), str(m, "type"), str(m, "url"), str(m, "created_at"),
                m.get("size_bytes") instanceof Number n ? n.longValue() : null, str(m, "content_type"));
    }

    private static String str(Map<String, Object> m, String k) {
        return m.get(k) instanceof String s ? s : null;
    }
}
