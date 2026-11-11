package dev.kloakd.sdk.models;

import java.nio.file.Path;

/**
 * An export written to a file ({@code webgrph().downloadCrawlExtract},
 * {@code client.downloadBatchJob}).
 *
 * @param path        the file written
 * @param bytes       its size
 * @param contentType the {@code Content-Type} header
 * @param filename    the file name the API suggested ({@code Content-Disposition}), or null
 * @param jobStatus   {@code X-Job-Status}: set for a crawl-extract export; null for a
 *                    batch export, whose route sends no such header
 * @param crawl       the {@code X-Crawl-*} headers of a crawl-extract export; null when
 *                    none was sent (always for a batch export)
 */
public record SavedExport(
        Path path,
        long bytes,
        String contentType,
        String filename,
        String jobStatus,
        ExportCrawl crawl
) {
    /** The components before {@code crawl} was added. */
    public SavedExport(Path path, long bytes, String contentType, String filename, String jobStatus) {
        this(path, bytes, contentType, filename, jobStatus, null);
    }
}
