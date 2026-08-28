export const DAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'] as const;

const MS_PER_DAY = 86_400_000;

/** Local midnight on the Sunday of the week containing `ref`. */
export function startOfWeek(ref: Date = new Date()): Date {
  const start = new Date(ref.getFullYear(), ref.getMonth(), ref.getDate());
  start.setDate(start.getDate() - start.getDay());
  return start;
}

export function endOfWeek(ref: Date = new Date()): Date {
  const end = startOfWeek(ref);
  end.setDate(end.getDate() + 7);
  return end;
}

export function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

/**
 * Rows are read in the order they happened, so the date column answers
 * "how long ago" rather than making the reader subtract dates in their head.
 */
export function relativeDayLabel(iso: string, now: Date = new Date()): string {
  const date = new Date(iso);
  if (isSameDay(date, now)) return 'Today';

  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  if (isSameDay(date, yesterday)) return 'Yesterday';

  const daysAgo = Math.floor((startOfDay(now).getTime() - startOfDay(date).getTime()) / MS_PER_DAY);
  if (daysAgo > 0 && daysAgo < 7) return DAY_LABELS[date.getDay()] as string;

  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

/** The long form used on the confirmation card, where there is room to be explicit. */
export function fullDateLabel(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}
