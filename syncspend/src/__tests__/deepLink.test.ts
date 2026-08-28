import assert from 'node:assert/strict';
import { test } from 'node:test';

import { isQuickLogUrl } from '../utils/deepLink';

test('matches the link the tile and the launcher shortcut fire', () => {
  assert.equal(isQuickLogUrl('syncspend://quick-log'), true);
  assert.equal(isQuickLogUrl('syncspend:///quick-log'), true);
  assert.equal(isQuickLogUrl('syncspend://quick-log?source=tile'), true);
  assert.equal(isQuickLogUrl('syncspend://quick-log/'), true);
});

test('matches the Expo Go shape used in development', () => {
  assert.equal(isQuickLogUrl('exp://192.168.1.5:8081/--/quick-log'), true);
});

test('ignores anything that is not the quick-log link', () => {
  assert.equal(isQuickLogUrl(null), false);
  assert.equal(isQuickLogUrl(undefined), false);
  assert.equal(isQuickLogUrl(''), false);
  assert.equal(isQuickLogUrl('syncspend://'), false);
  assert.equal(isQuickLogUrl('syncspend://settings'), false);
  assert.equal(isQuickLogUrl('https://example.com/quick-log-guide'), false);
});
