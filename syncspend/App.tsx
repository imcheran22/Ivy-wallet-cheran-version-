import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { DashboardScreen } from './src/screens/DashboardScreen';
import { ExpenseProvider } from './src/state/ExpenseStore';

export default function App() {
  return (
    <SafeAreaProvider>
      <ExpenseProvider>
        <StatusBar style="dark" />
        <DashboardScreen />
      </ExpenseProvider>
    </SafeAreaProvider>
  );
}
