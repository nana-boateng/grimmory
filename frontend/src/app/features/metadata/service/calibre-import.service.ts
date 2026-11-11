import {inject, Service} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, tap} from 'rxjs';
import {QueryClient} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../../../core/config/api-config';
import {invalidateAppBooksQueries} from '../../book/service/book-query-cache';
import {BOOKS_QUERY_KEY} from '../../book/service/book-query-keys';
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
        // Not `exact`: the per-book detail and recommendation queries live under the same prefix.
        void this.queryClient.invalidateQueries({queryKey: BOOKS_QUERY_KEY});
        invalidateAppBooksQueries(this.queryClient);
        invalidateAuthorsQuery(this.queryClient);
      })
    );
  }
}
