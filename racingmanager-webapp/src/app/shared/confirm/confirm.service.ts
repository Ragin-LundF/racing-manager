import { Injectable, signal } from '@angular/core';

export type ConfirmVariant = 'default' | 'danger' | 'warning' | 'success';

export interface ConfirmOptions {
  /** Optional heading; pre-translated. */
  title?: string;
  /** Body text; pre-translated. */
  message: string;
  /** Confirm button label; pre-translated. Falls back to `common.confirm`. */
  confirmLabel?: string;
  /** Cancel button label; pre-translated. Falls back to `common.cancel`. */
  cancelLabel?: string;
  /** Colours the confirm button and panel border. */
  variant?: ConfirmVariant;
  /** Type-to-confirm: confirm stays disabled until the user types this exact value. */
  requireText?: string;
  /** Placeholder for the type-to-confirm input. */
  requireTextLabel?: string;
}

/**
 * Promise-based confirmation dialog. A single `<app-confirm-dialog />` mounted at
 * app root renders the current `request()`; callers `await confirm({...})` and get
 * back the user's choice. Replaces native confirm()/prompt() and the old inline panels.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly request = signal<ConfirmOptions | null>(null);
  private resolver: ((result: boolean) => void) | null = null;

  confirm(options: ConfirmOptions): Promise<boolean> {
    // A second confirm() while one is open cancels the first so its promise never hangs.
    this.resolver?.(false);
    this.request.set(options);
    return new Promise<boolean>((resolve) => {
      this.resolver = resolve;
    });
  }

  resolve(result: boolean): void {
    const done = this.resolver;
    if (!done) return;
    this.resolver = null;
    this.request.set(null);
    done(result);
  }
}
