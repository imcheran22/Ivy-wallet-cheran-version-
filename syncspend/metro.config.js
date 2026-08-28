// Expo's Metro defaults: the transformer, resolver conditions and asset plugins
// the SDK expects. Without this file Metro falls back to bare React Native
// defaults, which do not downlevel the private class fields in React Native's
// own `src/private/webapis` sources — Hermes then refuses the bundle.
const { getDefaultConfig } = require('expo/metro-config');

module.exports = getDefaultConfig(__dirname);
