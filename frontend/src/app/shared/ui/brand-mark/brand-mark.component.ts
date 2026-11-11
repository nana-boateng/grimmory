import {ChangeDetectionStrategy, Component} from '@angular/core';

/**
 * The Grimmory mark: a flat book with a ribbon. One colour, taken from the theme's primary, so it follows
 * the chosen theme and the light/dark switch. Size it from outside with `height` (the width follows).
 */
@Component({
  selector: 'app-brand-mark',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 40 48" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <rect x="4" y="2" width="32" height="40" rx="5" fill="var(--color-primary)"/>
      <rect x="9" y="2" width="3.5" height="40" fill="color-mix(in srgb, var(--color-primary), var(--color-surface-950) 28%)"/>
      <path d="M23.5 2v19l3.5-3.5 3.5 3.5V2z" fill="var(--color-logo-bookmark)"/>
    </svg>
  `,
  styles: [`
    :host {
      display: inline-block;
      aspect-ratio: 5 / 6;
      height: 1.5rem;
      line-height: 0;
    }

    svg {
      display: block;
      width: 100%;
      height: 100%;
    }
  `],
})
export class BrandMarkComponent {}
