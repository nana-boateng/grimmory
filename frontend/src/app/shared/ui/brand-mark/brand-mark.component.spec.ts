import {TestBed} from '@angular/core/testing';
import {describe, expect, it} from 'vitest';

import {BrandMarkComponent} from './brand-mark.component';

describe('BrandMarkComponent', () => {
  it('draws the book, its spine and the ribbon, coloured from theme variables', () => {
    const fixture = TestBed.createComponent(BrandMarkComponent);
    fixture.detectChanges();

    const svg = fixture.nativeElement.querySelector('svg') as SVGElement;
    expect(svg.getAttribute('aria-hidden')).toBe('true');
    expect(svg.querySelectorAll('rect')).toHaveLength(2);
    expect(svg.querySelectorAll('path')).toHaveLength(1);
    expect(svg.querySelector('rect')?.getAttribute('fill')).toBe('var(--color-primary)');
    expect(svg.querySelector('path')?.getAttribute('fill')).toBe('var(--color-logo-bookmark)');
  });
});
