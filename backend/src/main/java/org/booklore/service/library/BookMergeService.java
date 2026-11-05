package org.booklore.service.library;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.entity.AnnotationEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMarkEntity;
import org.booklore.model.entity.BookNoteEntity;
import org.booklore.model.entity.BookNoteV2Entity;
import org.booklore.model.entity.PdfAnnotationEntity;
import org.booklore.model.entity.ReadingSessionEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.library.BookGroupingService.MergePlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Folds books into another book without touching any file on disk: only database rows move. The folded
 * books are soft-deleted in the same transaction, so a crash never leaves two visible copies; the caller
 * purges them afterwards with {@link BookDeletionService#deleteRemovedBooks(List)}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookMergeService {

    private static final List<Class<?>> BOOK_OWNED_ROWS = List.of(
            ReadingSessionEntity.class,
            BookMarkEntity.class,
            AnnotationEntity.class,
            PdfAnnotationEntity.class,
            BookNoteEntity.class,
            BookNoteV2Entity.class
    );

    private final BookRepository bookRepository;
    private final LibraryRepository libraryRepository;
    private final BookGroupingService bookGroupingService;

    @PersistenceContext
    private final EntityManager entityManager;

    /**
     * Read-only: which existing books in this library belong together under its organization mode. Runs in
     * its own transaction because deciding the winner reads lazy relations (library, files).
     */
    @Transactional(readOnly = true)
    public List<MergePlan> planSameFolderMerges(long libraryId) {
        LibraryEntity library = libraryRepository.findByIdWithPaths(libraryId).orElse(null);
        if (library == null) {
            return List.of();
        }
        return bookGroupingService.planSameFolderMerges(bookRepository.findAllByLibraryIdForRescan(libraryId), library);
    }

    /**
     * Moves the files, shelf membership, reading progress and per-user rows of each loser onto the winner.
     * Where the winner already has reading progress for a user, the winner's is kept and the loser's is
     * dropped when the loser is purged. Any failure rolls the whole merge back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mergeBooks(Long winnerId, List<Long> loserIds) {
        BookEntity winner = bookRepository.findById(winnerId)
                .orElseThrow(() -> new IllegalStateException("Merge target book " + winnerId + " no longer exists"));

        for (Long loserId : loserIds) {
            BookEntity loser = bookRepository.findById(loserId)
                    .orElseThrow(() -> new IllegalStateException("Book " + loserId + " to merge no longer exists"));

            winner.getShelves().addAll(loser.getShelves());
            entityManager.flush();

            entityManager.createQuery("UPDATE " + BookFileEntity.class.getSimpleName()
                            + " f SET f.book = :winner WHERE f.book.id = :loserId")
                    .setParameter("winner", winner)
                    .setParameter("loserId", loserId)
                    .executeUpdate();

            entityManager.createQuery("UPDATE UserBookProgressEntity p SET p.book = :winner"
                            + " WHERE p.book.id = :loserId AND NOT EXISTS ("
                            + "SELECT 1 FROM UserBookProgressEntity q WHERE q.book.id = :winnerId AND q.user = p.user)")
                    .setParameter("winner", winner)
                    .setParameter("loserId", loserId)
                    .setParameter("winnerId", winnerId)
                    .executeUpdate();

            for (Class<?> owned : BOOK_OWNED_ROWS) {
                entityManager.createQuery("UPDATE " + owned.getSimpleName()
                                + " r SET r.book = :winner WHERE r.book.id = :loserId")
                        .setParameter("winner", winner)
                        .setParameter("loserId", loserId)
                        .executeUpdate();
            }

            entityManager.createQuery("UPDATE BookEntity b SET b.deleted = true, b.deletedAt = :now WHERE b.id = :loserId")
                    .setParameter("now", Instant.now())
                    .setParameter("loserId", loserId)
                    .executeUpdate();
            log.info("Merged book {} into book {}", loserId, winnerId);
        }

        entityManager.flush();
        entityManager.clear();
    }
}
