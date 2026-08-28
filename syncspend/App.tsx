import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { DashboardScreen } from './src/screens/DashboardScreen';
import { ExpenseProvider } from './src/state/ExpenseStore';
import { ThemeProvider } from './src/theme/ThemeProvider';

export default function App() {
  return (
    <SafeAreaProvider>
      <ThemeProvider>
        <ExpenseProvider>
          <StatusBar style="auto" />
          <DashboardScreen />
        </ExpenseProvider>
      </ThemeProvider>
    </SafeAreaProvider>
  );
}
