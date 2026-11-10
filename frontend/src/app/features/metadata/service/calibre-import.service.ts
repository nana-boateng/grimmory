import {inject, Service} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, tap} from 'rxjs';
import {QueryClient} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../../../core/config/api-config';
import {invalidateAllBookCaches} from '../../book/service/legacy-book-cache';
import {invalidateAuthorsQuery} from '../../author-browser/service/author-query-cache';

export interface CalibreImportSummary {
  imported: number;
  withoutOpf: number;
  failed: number;
  coversUpdated: number;
}

@Service()
export class CalibreImportService {
  private readonly http = inject(HttpClient);
  private readonly queryClient = inject(QueryClient);
  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1`;

  importAll(libraryId: number): Observable<CalibreImportSummary> {
    return this.http.post<CalibreImportSummary>(
      `${this.apiUrl}/libraries/${libraryId}/calibre/import-all`, {}
    ).pipe(
      tap(() => {
        invalidateAllBookCaches(this.queryClient);
        invalidateAuthorsQuery(this.queryClient);
      })
    );
  }
}
