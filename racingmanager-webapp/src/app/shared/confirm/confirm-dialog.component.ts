import { Component, computed, effect, ElementRef, HostListener, inject, signal, viewChild } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { ConfirmService } from './confirm.service';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './confirm-dialog.component.html',
  styleUrl: './confirm-dialog.component.scss',
})
export class ConfirmDialogComponent {
  protected readonly confirm = inject(ConfirmService);
  protected readonly typed = signal('');
  private readonly requireTextInput = viewChild<ElementRef<HTMLInputElement>>('requireTextInput');

  constructor() {
    effect(() => this.requireTextInput()?.nativeElement.focus());
  }

  protected readonly canConfirm = computed(() => {
    const req = this.confirm.request();
    if (!req) return false;
    return !req.requireText || this.typed() === req.requireText;
  });

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.confirm.request()) this.cancel();
  }

  protected onConfirm(): void {
    if (!this.canConfirm()) return;
    this.typed.set('');
    this.confirm.resolve(true);
  }

  protected cancel(): void {
    this.typed.set('');
    this.confirm.resolve(false);
  }

  protected onBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.cancel();
  }
}
