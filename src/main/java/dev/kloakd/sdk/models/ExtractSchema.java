package dev.kloakd.sdk.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A typed Kolektr extraction schema, sent as the API's {@code schema_spec}
 * ({@code modules/kolektr/router.py} {@code SchemaSpec}).
 *
 * <p>With an {@link #itemSelector() item selector} (CSS) the page is a list
 * page: every element the item selector matches is one record, and each
 * field's selector is read inside that element. The first match gives the
 * value (every match for a {@link Field#LIST list} field), its text or the
 * named attribute; a field with no match in an item is null; a field with
 * no selector is read from the item element itself. Without an item
 * selector the field selectors run against the whole page and give one
 * record (the 0.5.0 behaviour).
 *
 * <pre>{@code
 * var schema = ExtractSchema.builder()
 *         .itemSelector("article.product_pod")
 *         .field("title", "h3 a", "title")      // the title attribute
 *         .field("price", "p.price_color")      // the text
 *         .listField("tags", "ul.tags li")      // every match
 *         .build();
 * client.kolektr().extract(url, ExtractOptions.builder().schema(schema.toMap()).build());
 * }</pre>
 *
 * @param fields the fields of each record
 * @param itemSelector CSS selector of the repeated item, or null for one record per page
 * @param pagination whether to follow pagination, or null for the API default (false)
 * @param maxPages pages to follow (1–100), or null for the API default (1)
 * @param outputFormat the output format, or null for the API default ({@code json})
 */
public record ExtractSchema(
        List<Field> fields,
        String itemSelector,
        Boolean pagination,
        Integer maxPages,
        String outputFormat
) {
    public ExtractSchema {
        fields = fields == null ? List.of() : List.copyOf(fields);
        if (itemSelector != null && itemSelector.isBlank()) {
            throw new IllegalArgumentException("itemSelector must not be blank");
        }
        if (itemSelector != null && fields.isEmpty()) {
            throw new IllegalArgumentException("itemSelector needs at least one field");
        }
        if (maxPages != null && (maxPages < 1 || maxPages > 100)) {
            throw new IllegalArgumentException("maxPages must be 1-100, was " + maxPages);
        }
    }

    /**
     * One field of a record.
     *
     * @param name the field's name in the record
     * @param selector CSS selector (a {@code css:} prefix is removed), or null to read the item element itself
     * @param attribute an attribute to read instead of the text, or null
     * @param type {@code text} (the API default; the first match) or {@code list} (every match), or null
     */
    public record Field(String name, String selector, String attribute, String type) {
        /** The field type that returns every match as a list. */
        public static final String LIST = "list";

        public Field {
            if (name == null || name.isEmpty()) throw new IllegalArgumentException("field name is required");
            if (selector != null && selector.startsWith("css:")) selector = selector.substring(4);
        }

        /** A text field read from the first match of {@code selector}. */
        public Field(String name, String selector) { this(name, selector, null, null); }

        /** This field as the API's {@code FieldSpec} map. */
        public Map<String, Object> toMap() {
            var m = new LinkedHashMap<String, Object>();
            m.put("name", name);
            if (selector != null) m.put("selector", selector);
            if (attribute != null) m.put("attribute", attribute);
            if (type != null) m.put("type", type);
            return m;
        }
    }

    public static Builder builder() { return new Builder(); }

    /** This schema as the API's {@code schema_spec} map; unset options are left out. */
    public Map<String, Object> toMap() {
        var m = new LinkedHashMap<String, Object>();
        var fs = new ArrayList<Map<String, Object>>();
        for (var f : fields) fs.add(f.toMap());
        m.put("fields", fs);
        if (itemSelector != null) m.put("item_selector", itemSelector.strip());
        if (pagination != null) m.put("pagination", pagination);
        if (maxPages != null) m.put("max_pages", maxPages);
        if (outputFormat != null) m.put("output_format", outputFormat);
        return Collections.unmodifiableMap(m);
    }

    public static final class Builder {
        private final List<Field> fields = new ArrayList<>();
        private String itemSelector;
        private Boolean pagination;
        private Integer maxPages;
        private String outputFormat;

        private Builder() {}

        /** CSS selector of the repeated item: one record per matching element. */
        public Builder itemSelector(String itemSelector) { this.itemSelector = itemSelector; return this; }
        /** A text field: the first match of {@code selector} (inside the item when there is an item selector). */
        public Builder field(String name, String selector) { fields.add(new Field(name, selector)); return this; }
        /** A field reading {@code attribute} of the first match ({@code selector} null: of the item element). */
        public Builder field(String name, String selector, String attribute) {
            fields.add(new Field(name, selector, attribute, null)); return this;
        }
        /** A list field: every match of {@code selector}, as text. */
        public Builder listField(String name, String selector) {
            fields.add(new Field(name, selector, null, Field.LIST)); return this;
        }
        /** A list field: {@code attribute} of every match of {@code selector}. */
        public Builder listField(String name, String selector, String attribute) {
            fields.add(new Field(name, selector, attribute, Field.LIST)); return this;
        }
        public Builder field(Field field) { fields.add(field); return this; }
        public Builder pagination(boolean pagination) { this.pagination = pagination; return this; }
        public Builder maxPages(int maxPages) { this.maxPages = maxPages; return this; }
        public Builder outputFormat(String outputFormat) { this.outputFormat = outputFormat; return this; }

        /**
         * @throws IllegalArgumentException a blank item selector, an item
         *         selector with no fields, or {@code maxPages} outside 1–100
         *         (the API answers these with 422)
         */
        public ExtractSchema build() {
            return new ExtractSchema(fields, itemSelector, pagination, maxPages, outputFormat);
        }
    }
}
