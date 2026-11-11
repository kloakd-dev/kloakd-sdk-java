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
 * @param jobStatus   {@code X-Job-Status} (the crawl-extract export only, else null)
 */
public record SavedExport(
        Path path,
        long bytes,
        String contentType,
        String filename,
        String jobStatus
) {}
