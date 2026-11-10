package org.booklore.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.enums.MetadataReplaceMode;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataUpdateContext {
    private BookEntity bookEntity;
    private MetadataUpdateWrapper metadataUpdateWrapper;
    private boolean updateThumbnail;
    private boolean mergeCategories;
    private boolean mergeMoods;
    private boolean mergeTags;
    private MetadataReplaceMode replaceMode;
    /**
     * Update the database only. Skips every change to files on disk that a metadata update can make on
     * local storage: writing into the book, writing a sidecar file, and moving files to the library pattern.
     */
    private boolean skipFileWrite;
}
