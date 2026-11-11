package dev.kloakd.sdk.models;

import dev.kloakd.sdk.errors.KloakdException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An export's body as a stream ({@code webgrph().exportCrawlExtract},
 * {@code client.exportBatchJob}), with what its response headers say. The
 * body is read as it arrives, not held in memory. Close it when done:
 *
 * <pre>{@code
 * try (ExportDownload export = client.webgrph().exportCrawlExtract(jobId, "csv")) {
 *     export.body().transferTo(out);
 * }
 * }</pre>
 */
public final class ExportDownload implements AutoCloseable {

    /** The formats the export routes accept. */
    public static final List<String> FORMATS = List.of("json", "ndjson", "csv");

    private static final Pattern FILENAME = Pattern.compile("filename=\"?([^\";]+)\"?");

    private final InputStream body;
    private final String contentType;
    private final String filename;
    private final String jobStatus;
    private final ExportCrawl crawl;

    public ExportDownload(InputStream body, String contentType, String filename, String jobStatus) {
        this(body, contentType, filename, jobStatus, null);
    }

    public ExportDownload(InputStream body, String contentType, String filename, String jobStatus,
            ExportCrawl crawl) {
        this.body = body;
        this.contentType = contentType;
        this.filename = filename;
        this.jobStatus = jobStatus;
        this.crawl = crawl;
    }

    /** The export a streamed response holds. */
    public static ExportDownload of(HttpResponse<InputStream> response) {
        var headers = response.headers();
        Matcher m = FILENAME.matcher(headers.firstValue("Content-Disposition").orElse(""));
        return new ExportDownload(response.body(), headers.firstValue("Content-Type").orElse(null),
                m.find() ? m.group(1) : null, headers.firstValue("X-Job-Status").orElse(null),
                crawlOf(headers));
    }

    /** The {@code X-Crawl-*} headers, or null when none was sent. */
    static ExportCrawl crawlOf(HttpHeaders headers) {
        boolean any = headers.map().keySet().stream()
                .anyMatch(name -> name.toLowerCase(Locale.ROOT).startsWith("x-crawl-"));
        if (!any) return null;
        return new ExportCrawl(
                headers.firstValue("X-Crawl-Status").orElse(null),
                headerBool(headers, "X-Crawl-Partial"),
                headers.firstValue("X-Crawl-Partial-Reason").orElse(null),
                headerInt(headers, "X-Crawl-Pages-Discovered"),
                headerInt(headers, "X-Crawl-Pages-Fetched"),
                headerInt(headers, "X-Crawl-Pages-Unfetched"),
                headerBool(headers, "X-Crawl-Cancelled"),
                headerBool(headers, "X-Crawl-Root-Unfetched"));
    }

    private static Boolean headerBool(HttpHeaders headers, String name) {
        String v = headers.firstValue(name).orElse(null);
        if (v == null) return null;
        v = v.trim();
        return "true".equalsIgnoreCase(v) ? Boolean.TRUE : "false".equalsIgnoreCase(v) ? Boolean.FALSE : null;
    }

    private static Integer headerInt(HttpHeaders headers, String name) {
        String v = headers.firstValue(name).orElse(null);
        if (v == null) return null;
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * {@code format} if the API accepts it.
     *
     * @throws IllegalArgumentException it is not one of {@link #FORMATS} (the API would answer 400)
     */
    public static String checkFormat(String format) {
        if (format == null || !FORMATS.contains(format)) {
            throw new IllegalArgumentException("format must be one of " + String.join(", ", FORMATS)
                    + ", was " + format);
        }
        return format;
    }

    /** The body, unread. */
    public InputStream body() { return body; }

    /** The {@code Content-Type} header ({@code application/json}, {@code application/x-ndjson} or {@code text/csv}). */
    public String contentType() { return contentType; }

    /** The file name the API suggests ({@code Content-Disposition}), or null. */
    public String filename() { return filename; }

    /**
     * The job's status when the export began ({@code X-Job-Status}). Set for
     * a crawl-extract export, where anything but {@code completed} means the
     * file holds the rows stored so far. Null for a batch export, whose
     * route sends no such header.
     */
    public String jobStatus() { return jobStatus; }

    /**
     * What the {@code X-Crawl-*} headers say about the job's crawl (a
     * crawl-extract export): its status, whether it is partial and why, and
     * its page counts, each null when its header was not sent. Null when no
     * {@code X-Crawl-*} header was sent: the job has no crawl yet, or this
     * is a batch export.
     */
    public ExportCrawl crawl() { return crawl; }

    /** The whole body in memory (for a large job prefer {@link #body()} or {@link #saveTo}); closes the export. */
    public byte[] readAllBytes() {
        try (this) {
            return body.readAllBytes();
        } catch (IOException e) {
            throw new KloakdException(0, "IO error: " + e.getMessage());
        }
    }

    /**
     * Write the body to {@code path} as it arrives (replacing a file already
     * there) and close the export. A download that breaks part way throws
     * and leaves the part written.
     */
    public SavedExport saveTo(Path path) {
        try (this; OutputStream out = Files.newOutputStream(path)) {
            long size = body.transferTo(out);
            return new SavedExport(path, size, contentType, filename, jobStatus, crawl);
        } catch (IOException e) {
            throw new KloakdException(0, "IO error: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            body.close();
        } catch (IOException ignored) {
            // the connection is gone either way
        }
    }
}
