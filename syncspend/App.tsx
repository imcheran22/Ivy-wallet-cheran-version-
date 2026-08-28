import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { ErrorBoundary } from './src/components/common/ErrorBoundary';
import { DashboardScreen } from './src/screens/DashboardScreen';
import { ExpenseProvider } from './src/state/ExpenseStore';
import { ThemeProvider } from './src/theme/ThemeProvider';

export default function App() {
  return (
    <SafeAreaProvider>
      <ErrorBoundary>
        <ThemeProvider>
          <ExpenseProvider>
            <StatusBar style="auto" />
            <DashboardScreen />
          </ExpenseProvider>
        </ThemeProvider>
      </ErrorBoundary>
    </SafeAreaProvider>
  );
}
