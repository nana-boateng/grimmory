package org.booklore.service.metadata.calibre;

import org.booklore.exception.APIException;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.metadata.calibre.CalibreBookImporter.Outcome;
import org.booklore.service.metadata.calibre.CalibreBookImporter.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalibreImportServiceTest {

    @Mock LibraryRepository libraryRepository;
    @Mock BookRepository bookRepository;
    @Mock CalibreBookImporter bookImporter;

    @InjectMocks
    CalibreImportService service;

    @Test
    void importLibrary_countsEachOutcomeAndCovers() {
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(LibraryEntity.builder().name("Calibre").build()));
        when(bookRepository.findBookIdsByLibraryId(1L)).thenReturn(Set.of(10L, 20L, 30L, 40L));
        when(bookImporter.importBook(10L)).thenReturn(new Result(Outcome.IMPORTED, true));
        when(bookImporter.importBook(20L)).thenReturn(new Result(Outcome.IMPORTED, false));
        when(bookImporter.importBook(30L)).thenReturn(new Result(Outcome.NO_OPF, false));
        when(bookImporter.importBook(40L)).thenReturn(new Result(Outcome.FAILED, false));

        var summary = service.importLibrary(1L);

        assertThat(summary).isEqualTo(new CalibreImportService.Summary(2, 1, 1, 1));
    }

    @Test
    void importLibrary_aBookThatThrowsCountsAsFailedAndTheRestStillRun() {
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(LibraryEntity.builder().name("Calibre").build()));
        when(bookRepository.findBookIdsByLibraryId(1L)).thenReturn(Set.of(10L, 20L, 30L));
        when(bookImporter.importBook(10L)).thenReturn(new Result(Outcome.IMPORTED, false));
        when(bookImporter.importBook(20L)).thenThrow(new IllegalStateException("boom"));
        when(bookImporter.importBook(30L)).thenReturn(new Result(Outcome.IMPORTED, false));

        var summary = service.importLibrary(1L);

        assertThat(summary).isEqualTo(new CalibreImportService.Summary(2, 0, 1, 0));
        InOrder order = inOrder(bookImporter);
        order.verify(bookImporter).importBook(10L);
        order.verify(bookImporter).importBook(20L);
        order.verify(bookImporter).importBook(30L);
    }

    @Test
    void importLibrary_forAnUnknownLibraryThrowsAndImportsNothing() {
        when(libraryRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importLibrary(9L)).isInstanceOf(APIException.class);
        verifyNoInteractions(bookImporter);
    }
}
