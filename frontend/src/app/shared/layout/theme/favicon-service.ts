import {Injectable} from '@angular/core';

@Injectable({providedIn: 'root'})
export class FaviconService {
  private currentUrl?: string;

  /** The brand mark in the theme's colours: `bookColor` for the cover, `spineColor` for the spine stripe. */
  private svgTemplate = (bookColor: string, spineColor: string) => `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="4 2 32 40" fill="none">
      <rect x="4" y="2" width="32" height="40" rx="5" fill="${bookColor}"/>
      <rect x="9" y="2" width="3.5" height="40" fill="${spineColor}"/>
      <path d="M23.5 2v19l3.5-3.5 3.5 3.5V2z" fill="white"/>
    </svg>
  `;

  updateFavicon(startColor: string, endColor: string) {
    const svg = this.svgTemplate(startColor, endColor);
    const blob = new Blob([svg], {type: 'image/svg+xml'});
    const url = URL.createObjectURL(blob);

    if (this.currentUrl) {
      URL.revokeObjectURL(this.currentUrl);
    }

    let favicon = document.querySelector("link[rel*='icon']") as HTMLLinkElement;
    if (!favicon) {
      favicon = document.createElement('link');
      favicon.rel = 'icon';
      document.head.appendChild(favicon);
    }

    favicon.type = 'image/svg+xml';
    favicon.href = url;
    this.currentUrl = url;
  }
}
