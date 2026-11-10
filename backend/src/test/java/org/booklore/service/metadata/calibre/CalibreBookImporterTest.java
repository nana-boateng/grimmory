package org.booklore.service.metadata.calibre;

import org.booklore.model.MetadataUpdateContext;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.repository.BookRepository;
import org.booklore.service.metadata.BookMetadataUpdater;
import org.booklore.service.metadata.extractor.EpubMetadataExtractor;
import org.booklore.util.FileService;
import org.booklore.util.epub.CoverDetectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalibreBookImporterTest {

    private static final String VALID_OPF = """
            <?xml version='1.0' encoding='utf-8'?>
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="uuid_id" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                <dc:identifier opf:scheme="uuid" id="uuid_id">00000000-0000-4000-8000-000000000007</dc:identifier>
                <dc:title>The Imported Book</dc:title>
                <dc:creator opf:file-as="Author, Ann" opf:role="aut">Ann Author</dc:creator>
                <meta name="calibre:series" content="Imported Series"/>
              </metadata>
            </package>
            """;

    @Mock BookRepository bookRepository;
    @Mock BookMetadataUpdater bookMetadataUpdater;
    @Mock FileService fileService;

    @TempDir
    Path libraryRoot;

    private CalibreBookImporter importer;
    private Path bookFolder;
    private BookEntity book;

    @BeforeEach
    void setUp() throws IOException {
        var reader = new CalibreOpfReader(new EpubMetadataExtractor(JsonMapper.shared(), mock(CoverDetectorService.class)));
        importer = new CalibreBookImporter(bookRepository, reader, bookMetadataUpdater, fileService);

        bookFolder = Files.createDirectories(libraryRoot.resolve("Ann Author").resolve("The Imported Book (7)"));
        Files.createFile(bookFolder.resolve("The Imported Book - Ann Author.epub"));

        var libraryPath = new LibraryPathEntity();
        libraryPath.setPath(libraryRoot.toString());
        var file = BookFileEntity.builder()
                .id(70L)
                .fileName("The Imported Book - Ann Author.epub")
                .fileSubPath("Ann Author/The Imported Book (7)")
                .isBookFormat(true)
                .bookType(BookFileType.EPUB)
                .build();
        var metadata = BookMetadataEntity.builder().bookId(7L).title("The Imported Book").build();
        book = BookEntity.builder()
                .id(7L)
                .libraryPath(libraryPath)
                .bookFiles(new HashSet<>(Set.of(file)))
                .metadata(metadata)
                .build();
        file.setBook(book);
        metadata.setBook(book);
        when(bookRepository.findByIdWithBookFiles(7L)).thenReturn(Optional.of(book));
    }

    private void writeCover() throws IOException {
        ImageIO.write(new BufferedImage(8, 12, BufferedImage.TYPE_INT_RGB), "jpg", bookFolder.resolve("cover.jpg").toFile());
    }

    @Test
    void importBook_withoutAnOpf_changesNothing() {
        var result = importer.importBook(7L);

        assertThat(result.outcome()).isEqualTo(CalibreBookImporter.Outcome.NO_OPF);
        assertThat(result.coverUpdated()).isFalse();
        verifyNoInteractions(bookMetadataUpdater, fileService);
    }

    @Test
    void importBook_withAnUnreadableOpf_reportsFailureAndChangesNothing() throws IOException {
        Files.writeString(bookFolder.resolve("metadata.opf"), "not xml");

        var result = importer.importBook(7L);

        assertThat(result.outcome()).isEqualTo(CalibreBookImporter.Outcome.FAILED);
        verifyNoInteractions(bookMetadataUpdater);
    }

    @Test
    void importBook_appliesTheOpfWithoutEverTouchingFiles() throws IOException {
        Files.writeString(bookFolder.resolve("metadata.opf"), VALID_OPF);

        var result = importer.importBook(7L);

        assertThat(result.outcome()).isEqualTo(CalibreBookImporter.Outcome.IMPORTED);
        var context = ArgumentCaptor.forClass(MetadataUpdateContext.class);
        verify(bookMetadataUpdater).setBookMetadata(context.capture());
        assertThat(context.getValue().isSkipFileWrite()).as("never write into a Calibre library").isTrue();
        assertThat(context.getValue().getReplaceMode()).isEqualTo(MetadataReplaceMode.REPLACE_WHEN_PROVIDED);
        assertThat(context.getValue().getBookEntity()).isSameAs(book);
        var applied = context.getValue().getMetadataUpdateWrapper().getMetadata();
        assertThat(applied.getTitle()).isEqualTo("The Imported Book");
        assertThat(applied.getAuthors()).containsExactly("Ann Author");
        assertThat(applied.getSeriesName()).isEqualTo("Imported Series");
    }

    @Test
    void importBook_usesCalibresCoverAndRefreshesTheCoverHash() throws IOException {
        Files.writeString(bookFolder.resolve("metadata.opf"), VALID_OPF);
        writeCover();
        when(fileService.saveCoverImages(any(BufferedImage.class), eq(7L))).thenReturn(true);
        book.setBookCoverHash("old-hash");

        var result = importer.importBook(7L);

        assertThat(result.coverUpdated()).isTrue();
        assertThat(book.getBookCoverHash()).isNotEqualTo("old-hash").isNotBlank();
    }

    @Test
    void importBook_leavesALockedCoverAlone() throws IOException {
        Files.writeString(bookFolder.resolve("metadata.opf"), VALID_OPF);
        writeCover();
        book.getMetadata().setCoverLocked(true);

        var result = importer.importBook(7L);

        assertThat(result.outcome()).isEqualTo(CalibreBookImporter.Outcome.IMPORTED);
        assertThat(result.coverUpdated()).isFalse();
        verify(fileService, never()).saveCoverImages(any(), eq(7L));
    }

    @Test
    void importBook_stillImportsMetadataWhenTheCoverCannotBeSaved() throws IOException {
        Files.writeString(bookFolder.resolve("metadata.opf"), VALID_OPF);
        Files.writeString(bookFolder.resolve("cover.jpg"), "not an image");

        var result = importer.importBook(7L);

        assertThat(result.outcome()).isEqualTo(CalibreBookImporter.Outcome.IMPORTED);
        assertThat(result.coverUpdated()).isFalse();
        verify(bookMetadataUpdater).setBookMetadata(any());
    }
}
