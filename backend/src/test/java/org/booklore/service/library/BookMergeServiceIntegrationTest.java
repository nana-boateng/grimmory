package org.booklore.service.library;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.task.TaskCronService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Runs the merge's bulk queries against a real (H2) schema. Not @Transactional: the merge opens its own
 * transaction, so the fixtures have to be committed for it to see them. The database has its own name so
 * the committed rows never reach the other @SpringBootTest classes.
 */
@SpringBootTest(classes = {BookloreApplication.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:bookmergetestdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.path-config=build/tmp/merge-test-config",
        "app.bookdrop-folder=build/tmp/merge-test-bookdrop",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.task.scheduling.enabled=false",
        "app.task.scan-library-cron=*/1 * * * * *",
        "app.task.process-bookdrop-cron=*/1 * * * * *",
        "app.features.oidc-enabled=false",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false"
})
@Import(BookMergeServiceIntegrationTest.TestConfig.class)
class BookMergeServiceIntegrationTest {

    @TestConfiguration
    public static class TestConfig {
        @Bean("flyway")
        @Primary
        public Flyway flyway() {
            return mock(Flyway.class);
        }

        @Bean
        @Primary
        public TaskCronService taskCronService() {
            return mock(TaskCronService.class);
        }
    }

    @Autowired
    private BookMergeService bookMergeService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private record Fixture(long winnerId, long loserId, long userId, long shelfId) {}

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private Fixture folderWithEpubAndPdf(boolean winnerHasProgress, boolean loserHasProgress) {
        return inTransaction(() -> {
            String unique = UUID.randomUUID().toString();
            var library = LibraryEntity.builder().name("Lib " + unique).icon("book").watch(false)
                    .formatPriority(List.of(BookFileType.EPUB, BookFileType.PDF)).build();
            entityManager.persist(library);
            var path = LibraryPathEntity.builder().library(library).path("/books-" + unique).build();
            entityManager.persist(path);

            var user = BookLoreUserEntity.builder().username("u-" + unique).passwordHash("x").name("Reader")
                    .createdAt(LocalDateTime.now()).build();
            entityManager.persist(user);
            var shelf = ShelfEntity.builder().user(user).name("Favourites").build();
            entityManager.persist(shelf);

            var epub = book(library, path, "Clit-ology", "Clit-ology.epub", BookFileType.EPUB);
            var pdf = book(library, path, "Clit-ology", "Clit-ology.pdf", BookFileType.PDF);
            pdf.getShelves().add(shelf);
            entityManager.persist(pdf);
            if (winnerHasProgress) {
                entityManager.persist(UserBookProgressEntity.builder().user(user).book(epub).build());
            }
            if (loserHasProgress) {
                entityManager.persist(UserBookProgressEntity.builder().user(user).book(pdf).build());
            }
            entityManager.flush();
            return new Fixture(epub.getId(), pdf.getId(), user.getId(), shelf.getId());
        });
    }

    private BookEntity book(LibraryEntity library, LibraryPathEntity path, String folder, String fileName, BookFileType type) {
        var book = BookEntity.builder().library(library).libraryPath(path).addedOn(Instant.now()).deleted(false).build();
        entityManager.persist(book);
        var file = BookFileEntity.builder().book(book).fileName(fileName).fileSubPath(folder)
                .isBookFormat(true).bookType(type).addedOn(Instant.now()).build();
        entityManager.persist(file);
        book.getBookFiles().add(file);
        return book;
    }

    private long countFiles(long bookId) {
        return inTransaction(() -> entityManager
                .createQuery("SELECT COUNT(f) FROM BookFileEntity f WHERE f.book.id = :id", Long.class)
                .setParameter("id", bookId).getSingleResult());
    }

    private long countProgress(long bookId) {
        return inTransaction(() -> entityManager
                .createQuery("SELECT COUNT(p) FROM UserBookProgressEntity p WHERE p.book.id = :id", Long.class)
                .setParameter("id", bookId).getSingleResult());
    }

    @Test
    void mergeBooks_movesFilesShelvesAndProgressOntoTheWinnerAndHidesTheLoser() {
        var f = folderWithEpubAndPdf(false, true);

        bookMergeService.mergeBooks(f.winnerId(), List.of(f.loserId()));

        assertThat(countFiles(f.winnerId())).isEqualTo(2);
        assertThat(countFiles(f.loserId())).isZero();
        assertThat(countProgress(f.winnerId())).as("progress read on the PDF moves to the stacked book").isEqualTo(1);
        assertThat(countProgress(f.loserId())).isZero();
        inTransaction(() -> {
            var winner = entityManager.find(BookEntity.class, f.winnerId());
            var loser = entityManager.find(BookEntity.class, f.loserId());
            assertThat(winner.getShelves()).extracting(ShelfEntity::getId).containsExactly(f.shelfId());
            assertThat(winner.getPrimaryBookFile().getBookType()).isEqualTo(BookFileType.EPUB);
            assertThat(winner.getDeleted()).isFalse();
            assertThat(loser.getDeleted()).isTrue();
            assertThat(loser.getDeletedAt()).isNotNull();
            return null;
        });
    }

    @Test
    void mergeBooks_keepsTheWinnersProgressWhenBothBooksHaveSome() {
        var f = folderWithEpubAndPdf(true, true);

        bookMergeService.mergeBooks(f.winnerId(), List.of(f.loserId()));

        assertThat(countProgress(f.winnerId())).isEqualTo(1);
        assertThat(countFiles(f.winnerId())).isEqualTo(2);
        assertThat(countFiles(f.loserId())).isZero();
    }
}
