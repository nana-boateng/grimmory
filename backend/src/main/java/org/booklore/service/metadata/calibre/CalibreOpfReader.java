package org.booklore.service.metadata.calibre;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.BookMetadata;
import org.booklore.service.metadata.extractor.EpubMetadataExtractor;
import org.booklore.util.SecureXmlUtils;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Reads the {@code metadata.opf} and {@code cover.jpg} that Calibre keeps in each book's folder
 * ({@code Author/Title (id)/}). Read-only: nothing here writes to the library.
 * <p>
 * The OPF is parsed by {@link EpubMetadataExtractor}, the same code that reads the OPF inside an EPUB, so a
 * book imported from Calibre gets the same fields, in the same shape, as one scanned from its file.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalibreOpfReader {

    public static final String OPF_FILE_NAME = "metadata.opf";
    public static final String COVER_FILE_NAME = "cover.jpg";

    /** What Calibre writes as the author of a book that has none. */
    private static final String CALIBRE_UNKNOWN_AUTHOR = "Unknown";

    /** Calibre stores 0101-01-01 as "no publication date"; no real book is that old. */
    private static final int EARLIEST_REAL_YEAR = 1000;

    private final EpubMetadataExtractor epubMetadataExtractor;

    /** The OPF Calibre keeps beside a book file, if there is one. */
    public Optional<Path> findOpf(Path bookFile) {
        return siblingFile(bookFile, OPF_FILE_NAME);
    }

    /** The cover Calibre keeps beside a book file, if there is one. */
    public Optional<Path> findCover(Path bookFile) {
        return siblingFile(bookFile, COVER_FILE_NAME);
    }

    public Optional<BookMetadata> read(Path opfFile) {
        try (InputStream in = Files.newInputStream(opfFile)) {
            Document doc = SecureXmlUtils.createSecureDocumentBuilder(true).parse(in);
            BookMetadata metadata = epubMetadataExtractor.extractMetadataFromOpf(doc, null);
            return Optional.ofNullable(metadata).map(this::dropCalibrePlaceholders);
        } catch (Exception e) {
            log.warn("Could not read Calibre OPF {}: {}", opfFile, e.getMessage());
            return Optional.empty();
        }
    }

    private BookMetadata dropCalibrePlaceholders(BookMetadata metadata) {
        BookMetadata.BookMetadataBuilder cleaned = metadata.toBuilder();

        List<String> authors = metadata.getAuthors();
        if (authors != null) {
            List<String> real = authors.stream()
                    .filter(a -> !CALIBRE_UNKNOWN_AUTHOR.equalsIgnoreCase(a.trim()))
                    .toList();
            cleaned.authors(real.isEmpty() ? null : real);
        }

        if (metadata.getPublishedDate() != null && metadata.getPublishedDate().getYear() < EARLIEST_REAL_YEAR) {
            cleaned.publishedDate(null);
        }

        return cleaned.build();
    }

    private Optional<Path> siblingFile(Path bookFile, String name) {
        if (bookFile == null || bookFile.getParent() == null) {
            return Optional.empty();
        }
        Path candidate = bookFile.getParent().resolve(name);
        return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
    }
}
