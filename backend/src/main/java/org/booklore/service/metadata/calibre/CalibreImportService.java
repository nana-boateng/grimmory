package org.booklore.service.metadata.calibre;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Applies the metadata Calibre keeps in each book folder to every book in a library. Meant for a library
 * whose root is a Calibre library, organized one book per folder, so that each book's files and its
 * {@code metadata.opf} share a folder. Books without an OPF are left as they are.
 * <p>
 * Not transactional: each book is imported in its own transaction by {@link CalibreBookImporter}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalibreImportService {

    public record Summary(int imported, int withoutOpf, int failed, int coversUpdated) {}

    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final CalibreBookImporter bookImporter;

    public Summary importLibrary(Long libraryId) {
        var library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        List<Long> bookIds = bookRepository.findBookIdsByLibraryId(libraryId).stream().sorted().toList();

        int imported = 0;
        int withoutOpf = 0;
        int failed = 0;
        int covers = 0;
        for (Long bookId : bookIds) {
            try {
                CalibreBookImporter.Result result = bookImporter.importBook(bookId);
                switch (result.outcome()) {
                    case IMPORTED -> imported++;
                    case NO_OPF -> withoutOpf++;
                    case FAILED -> failed++;
                }
                if (result.coverUpdated()) {
                    covers++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("Calibre import failed for book ID {}: {}", bookId, e.getMessage());
            }
        }

        log.info("Calibre import for library '{}': {} imported ({} covers), {} without metadata.opf, {} failed",
                library.getName(), imported, covers, withoutOpf, failed);
        return new Summary(imported, withoutOpf, failed, covers);
    }
}
