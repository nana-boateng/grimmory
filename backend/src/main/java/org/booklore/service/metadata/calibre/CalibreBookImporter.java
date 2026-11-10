package org.booklore.service.metadata.calibre;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.MetadataUpdateContext;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.repository.BookRepository;
import org.booklore.service.metadata.BookMetadataUpdater;
import org.booklore.util.BookCoverUtils;
import org.booklore.util.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Imports one book's Calibre metadata, in its own transaction so one bad book cannot undo the rest of a
 * library import. Database only: it never writes to the book's files or folder.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalibreBookImporter {

    public enum Outcome {
        /** Metadata applied from the book's metadata.opf. */
        IMPORTED,
        /** No metadata.opf next to the book (not a Calibre folder, or no file). */
        NO_OPF,
        /** There was an OPF but it could not be read or applied. */
        FAILED
    }

    public record Result(Outcome outcome, boolean coverUpdated) {
        static Result of(Outcome outcome) {
            return new Result(outcome, false);
        }
    }

    private final BookRepository bookRepository;
    private final CalibreOpfReader opfReader;
    private final BookMetadataUpdater bookMetadataUpdater;
    private final FileService fileService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result importBook(Long bookId) {
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        Path bookFile = book.getFullFilePath();
        Optional<Path> opf = opfReader.findOpf(bookFile);
        if (opf.isEmpty()) {
            return Result.of(Outcome.NO_OPF);
        }

        Optional<BookMetadata> metadata = opfReader.read(opf.get());
        if (metadata.isEmpty()) {
            return Result.of(Outcome.FAILED);
        }

        bookMetadataUpdater.setBookMetadata(MetadataUpdateContext.builder()
                .bookEntity(book)
                .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(metadata.get()).build())
                .updateThumbnail(false)
                .replaceMode(MetadataReplaceMode.REPLACE_WHEN_PROVIDED)
                .skipFileWrite(true)
                .build());

        return new Result(Outcome.IMPORTED, importCover(book, bookFile));
    }

    private boolean importCover(BookEntity book, Path bookFile) {
        if (book.getMetadata() == null || Boolean.TRUE.equals(book.getMetadata().getCoverLocked())) {
            return false;
        }
        Optional<Path> cover = opfReader.findCover(bookFile);
        if (cover.isEmpty()) {
            return false;
        }
        try {
            BufferedImage image = FileService.readImage(Files.readAllBytes(cover.get()));
            if (image == null) {
                return false;
            }
            try {
                if (!fileService.saveCoverImages(image, book.getId())) {
                    return false;
                }
            } finally {
                image.flush();
            }
            FileService.setBookCoverPath(book.getMetadata());
            book.setBookCoverHash(BookCoverUtils.generateCoverHash());
            return true;
        } catch (Exception e) {
            log.warn("Could not import Calibre cover for book ID {}: {}", book.getId(), e.getMessage());
            return false;
        }
    }
}
