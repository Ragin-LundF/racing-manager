/** Average speed in km/h for one traversal of a `meters`-long track in `nanos`
    nanoseconds. There is no lap concept in the domain — one measurement is one
    run of the track — so the distance is the track length itself.
    Null whenever speed is undefined: no track length, or no usable time. */
export function speedKmh(nanos: number | null | undefined, meters: number | null | undefined): number | null {
  if (!nanos || nanos <= 0 || !meters || meters <= 0) {
    return null;
  }
  return (meters / (nanos / 1_000_000_000)) * 3.6;
}

/** `speedKmh` rendered for display, or an empty string when it is undefined. */
export function formatSpeedKmh(nanos: number | null | undefined, meters: number | null | undefined): string {
  const kmh = speedKmh(nanos, meters);
  return kmh === null ? '' : `${kmh.toFixed(1)} km/h`;
}
