package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * Options for {@code kolektr().extract(url, options)} ({@code POST kolektr/extract}).
 *
 * @param schema the fields to extract, or null: the shorthand
 *               {@code {"title": "css:h1"}} (prefix optional) or a full
 *               {@code {"fields": [{"name", "selector", ...}], "pagination"?, "max_pages"?, "output_format"?}}
 * @param level {@code level1}, {@code level2}, or null for automatic
 * @param options extra extraction options passed through as {@code options}, or null
 * @param fetchArtifactId an Evadr fetch artifact to extract from (skips the fetch), or null
 * @param sessionArtifactId an authenticated session (Fetchyr), or null
 * @param apiMapArtifactId an API map artifact, or null
 * @param pipelineId a pipeline id to report progress under, or null
 */
public record ExtractOptions(
        Map<String, ?> schema,
        String level,
        Map<String, Object> options,
        String fetchArtifactId,
        String sessionArtifactId,
        String apiMapArtifactId,
        String pipelineId
) {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Map<String, ?> schema;
        private String level;
        private Map<String, Object> options;
        private String fetchArtifactId;
        private String sessionArtifactId;
        private String apiMapArtifactId;
        private String pipelineId;

        private Builder() {}

        public Builder schema(Map<String, ?> schema) { this.schema = schema; return this; }
        public Builder level(String level) { this.level = level; return this; }
        public Builder options(Map<String, Object> options) { this.options = options; return this; }
        public Builder fetchArtifactId(String id) { this.fetchArtifactId = id; return this; }
        public Builder sessionArtifactId(String id) { this.sessionArtifactId = id; return this; }
        public Builder apiMapArtifactId(String id) { this.apiMapArtifactId = id; return this; }
        public Builder pipelineId(String id) { this.pipelineId = id; return this; }

        public ExtractOptions build() {
            return new ExtractOptions(schema, level, options, fetchArtifactId, sessionArtifactId,
                    apiMapArtifactId, pipelineId);
        }
    }
}
