package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * Options for {@code kolektr().extract(url, options)} ({@code POST kolektr/extract}).
 *
 * @param schema the fields to extract, or null: an {@link ExtractSchema#toMap()}, the shorthand
 *               {@code {"title": "css:h1"}} (prefix optional) or a full
 *               {@code {"fields": [{"name", "selector", ...}], "pagination"?, "max_pages"?, "output_format"?}}
 * @param level {@code level1}, {@code level2}, or null for automatic
 * @param options extra extraction options passed through as {@code options}, or null
 * @param fetchArtifactId an Evadr fetch artifact to extract from (skips the fetch), or null
 * @param sessionArtifactId an authenticated session (Fetchyr), or null
 * @param apiMapArtifactId an API map artifact, or null
 * @param pipelineId a pipeline id to report progress under, or null
 * @param itemSelector CSS selector of the repeated item on a list page, or null: sent as
 *                     {@code schema_spec.item_selector}, so each matching element is one
 *                     record and every field selector is read inside it (since 0.6.0;
 *                     overrides an {@code item_selector} in {@code schema})
 */
public record ExtractOptions(
        Map<String, ?> schema,
        String level,
        Map<String, Object> options,
        String fetchArtifactId,
        String sessionArtifactId,
        String apiMapArtifactId,
        String pipelineId,
        String itemSelector
) {
    /** The 0.5.0 constructor (no item selector). */
    public ExtractOptions(Map<String, ?> schema, String level, Map<String, Object> options,
            String fetchArtifactId, String sessionArtifactId, String apiMapArtifactId, String pipelineId) {
        this(schema, level, options, fetchArtifactId, sessionArtifactId, apiMapArtifactId, pipelineId, null);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Map<String, ?> schema;
        private String level;
        private Map<String, Object> options;
        private String fetchArtifactId;
        private String sessionArtifactId;
        private String apiMapArtifactId;
        private String pipelineId;
        private String itemSelector;

        private Builder() {}

        public Builder schema(Map<String, ?> schema) { this.schema = schema; return this; }
        /** CSS selector of the repeated item: one record per matching element (see {@link ExtractSchema}). */
        public Builder itemSelector(String itemSelector) { this.itemSelector = itemSelector; return this; }
        public Builder level(String level) { this.level = level; return this; }
        public Builder options(Map<String, Object> options) { this.options = options; return this; }
        public Builder fetchArtifactId(String id) { this.fetchArtifactId = id; return this; }
        public Builder sessionArtifactId(String id) { this.sessionArtifactId = id; return this; }
        public Builder apiMapArtifactId(String id) { this.apiMapArtifactId = id; return this; }
        public Builder pipelineId(String id) { this.pipelineId = id; return this; }

        public ExtractOptions build() {
            return new ExtractOptions(schema, level, options, fetchArtifactId, sessionArtifactId,
                    apiMapArtifactId, pipelineId, itemSelector);
        }
    }
}
