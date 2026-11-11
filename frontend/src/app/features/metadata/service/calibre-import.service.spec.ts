import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {BOOKS_QUERY_KEY} from '../../book/service/book-query-keys';
import {AUTHORS_QUERY_KEY} from '../../author-browser/service/author-query-keys';
import {CalibreImportService} from './calibre-import.service';

describe('CalibreImportService', () => {
  let service: CalibreImportService;
  let httpTestingController: HttpTestingController;
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        CalibreImportService,
        {provide: QueryClient, useValue: queryClient},
      ]
    });

    service = TestBed.inject(CalibreImportService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    queryClient.clear();
    TestBed.resetTestingModule();
  });

  it('imports Calibre metadata for a library and returns the summary', () => {
    vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);
    const summary = {imported: 300, withoutOpf: 2, failed: 1, coversUpdated: 299};

    service.importAll(7).subscribe(response => {
      expect(response).toEqual(summary);
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/libraries/7/calibre/import-all`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(summary);
  });

  it('refreshes the book and author caches once the import finishes', () => {
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);

    service.importAll(7).subscribe();
    expect(invalidateQueriesSpy).not.toHaveBeenCalled();

    httpTestingController
      .expectOne(`${API_CONFIG.BASE_URL}/api/v1/libraries/7/calibre/import-all`)
      .flush({imported: 1, withoutOpf: 0, failed: 0, coversUpdated: 1});

    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: BOOKS_QUERY_KEY});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['app-books']});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: AUTHORS_QUERY_KEY, exact: true});
  });

  it('does not refresh caches when the import fails', () => {
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);
    let failed = false;

    service.importAll(7).subscribe({error: () => (failed = true)});

    httpTestingController
      .expectOne(`${API_CONFIG.BASE_URL}/api/v1/libraries/7/calibre/import-all`)
      .flush('nope', {status: 500, statusText: 'Server Error'});

    expect(failed).toBe(true);
    expect(invalidateQueriesSpy).not.toHaveBeenCalled();
  });
});
