import { describe, expect, it } from 'vitest';
import { formatSpeedKmh, speedKmh } from './speed';

describe('speedKmh', () => {
  it('converts a track length and a time to km/h', () => {
    // 100 m in 10 s = 10 m/s = 36 km/h
    expect(speedKmh(10_000_000_000, 100)).toBeCloseTo(36);
  });

  it('is undefined without a track length', () => {
    expect(speedKmh(10_000_000_000, null)).toBeNull();
  });

  it('is undefined for a missing or non-positive time', () => {
    expect(speedKmh(null, 100)).toBeNull();
    expect(speedKmh(0, 100)).toBeNull();
    expect(speedKmh(-1, 100)).toBeNull();
  });

  it('is undefined for a non-positive track length', () => {
    expect(speedKmh(10_000_000_000, 0)).toBeNull();
    expect(speedKmh(10_000_000_000, -5)).toBeNull();
  });
});

describe('formatSpeedKmh', () => {
  it('renders one decimal with the unit', () => {
    expect(formatSpeedKmh(10_000_000_000, 100)).toBe('36.0 km/h');
  });

  it('renders an empty string when the speed is undefined', () => {
    expect(formatSpeedKmh(10_000_000_000, null)).toBe('');
  });
});
