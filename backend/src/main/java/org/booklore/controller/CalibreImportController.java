package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.booklore.service.metadata.calibre.CalibreImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Calibre Import", description = "Import metadata from a Calibre library")
@RequestMapping("/api/v1")
@RestController
@AllArgsConstructor
public class CalibreImportController {

    private final CalibreImportService calibreImportService;

    @Operation(summary = "Import Calibre metadata for a library",
            description = "Reads the metadata.opf and cover.jpg Calibre keeps in each book folder and applies them to the library's books. Database only; no file is written.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Import completed"),
            @ApiResponse(responseCode = "404", description = "Library not found")
    })
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @PostMapping("/libraries/{libraryId}/calibre/import-all")
    public ResponseEntity<CalibreImportService.Summary> importAll(@Parameter(description = "Library ID") @PathVariable Long libraryId) {
        return ResponseEntity.ok(calibreImportService.importLibrary(libraryId));
    }
}
