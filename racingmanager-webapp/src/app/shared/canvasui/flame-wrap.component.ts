import {
  afterNextRender,
  Component,
  computed,
  effect,
  ElementRef,
  input,
  OnDestroy,
  viewChild,
} from '@angular/core';
import {
  createFlameWrap,
  type FlameWrapInstance,
  type FlameWrapOptions,
} from './flame-wrap-vanilla';

/**
 * Restrained defaults, tuned well below the upstream demo: low flames that lick
 * just past the top edge, a warm amber rim instead of a bright blaze, and only
 * an occasional spark. `radius` mirrors `--radius-panel` so the burning outline
 * follows the card corners.
 */
const DEFAULT_OPTIONS: FlameWrapOptions = {
  color: [1, 0.42, 0.12],
  intensity: 0.35,
  height: 64,
  spread: 12,
  radius: 8,
  speed: 0.2,
  sparks: 0.7,
  sparkDensity: 0.6,
  rim: 1.4,
  melt: 1.2,
  smoke: 0.4,
  turbulence: 0.4,
};

/**
 * Wraps projected content in an animated WebGL border of fire.
 *
 * The content is rendered as ordinary DOM and the effect is painted on a
 * `pointer-events: none` canvas around it, so anything wrapped stays visible,
 * focusable and interactive in every browser. Degrades to plain content when
 * WebGL2 is unavailable, and stops animating under `prefers-reduced-motion`.
 */
@Component({
  selector: 'app-flame-wrap',
  standalone: true,
  templateUrl: './flame-wrap.component.html',
  styleUrl: './flame-wrap.component.scss',
})
export class FlameWrapComponent implements OnDestroy {
  /** Overrides merged over the card defaults. */
  readonly options = input<FlameWrapOptions>({});

  private readonly sourceCanvas = viewChild.required<ElementRef<HTMLCanvasElement>>('source');
  private readonly contentBox = viewChild.required<ElementRef<HTMLElement>>('content');
  private readonly outputCanvas = viewChild.required<ElementRef<HTMLCanvasElement>>('output');

  private instance: FlameWrapInstance | null = null;

  protected readonly effectiveOptions = computed<FlameWrapOptions>(() => ({
    ...DEFAULT_OPTIONS,
    ...this.options(),
  }));

  /** How far the canvas has to reach past the content to fit the flames. */
  protected readonly reachTop = computed(() => `${this.effectiveOptions().height ?? 0}px`);
  protected readonly reachSide = computed(() => `${this.effectiveOptions().spread ?? 0}px`);

  constructor() {
    afterNextRender(() => {
      this.instance = createFlameWrap(
        {
          source: this.sourceCanvas().nativeElement,
          content: this.contentBox().nativeElement,
          output: this.outputCanvas().nativeElement,
        },
        this.effectiveOptions(),
      );
    });

    effect(() => {
      const options = this.effectiveOptions();
      this.instance?.setOptions(options);
    });
  }

  ngOnDestroy(): void {
    this.instance?.destroy();
    this.instance = null;
  }
}
