import React from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

type Props = { children: React.ReactNode };
type State = { error: Error | null };

/**
 * Without this, a render error in a release build closes the app with no
 * explanation — indistinguishable, from the outside, from a native crash.
 * Showing the message makes the difference reportable.
 */
export class ErrorBoundary extends React.Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    return (
      <View style={styles.screen}>
        <ScrollView contentContainerStyle={styles.content}>
          <Text style={styles.title}>SyncSpend hit an error</Text>
          <Text style={styles.message}>{error.message}</Text>
          {error.stack ? <Text style={styles.stack}>{error.stack}</Text> : null}
        </ScrollView>
      </View>
    );
  }
}

// Deliberately not themed: the theme layer is one of the things that could be
// broken by the time this renders.
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#FFFFFF' },
  content: { padding: 24, paddingTop: 72 },
  title: { fontSize: 19, fontWeight: '600', color: '#0A0A0A', marginBottom: 12 },
  message: { fontSize: 15, color: '#0A0A0A', marginBottom: 16 },
  stack: { fontSize: 11, color: '#8A8A8E', fontFamily: 'monospace' },
});
