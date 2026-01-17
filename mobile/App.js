import React, { useState, useEffect, createContext, useContext } from 'react';
import { 
  StyleSheet, 
  Text, 
  View, 
  TouchableOpacity, 
  TextInput, 
  ActivityIndicator, 
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StatusBar,
  Dimensions,
  Switch,
  Modal
} from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import { LinearGradient } from 'expo-linear-gradient';
import { 
  Weight, 
  Activity, 
  Plus, 
  CheckCircle2, 
  ChevronLeft, 
  ChevronRight,
  Heart, 
  TrendingUp, 
  History as HistoryIcon,
  Bell,
  Pill,
  Target,
  FileDown,
  Mic,
  Settings as SettingsIcon,
  Trash2,
  Lock,
  LogOut,
  Smile,
  Frown,
  Meh,
  AlertCircle,
  RefreshCw,
  Palette,
  Info
} from 'lucide-react-native';
import { LineChart } from 'react-native-chart-kit';
import { useFocusEffect } from '@react-navigation/native';
import * as Notifications from 'expo-notifications';
import * as Print from 'expo-print';
import * as Sharing from 'expo-sharing';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Speech from 'expo-speech';
import { API_URL } from './Constants';

const Stack = createStackNavigator();

// --- Theme Context ---
const THEMES = {
  PREMIUM: { primary: '#8b5cf6', secondary: '#1e1b4b', bg: ['#0f172a', '#1e1b4b'], name: 'Deep Space' },
  SAKURA: { primary: '#ec4899', secondary: '#500724', bg: ['#2d0617', '#500724'], name: 'Sakura Pink' },
  EMERALD: { primary: '#10b981', secondary: '#064e3b', bg: ['#022c22', '#064e3b'], name: 'Nature Green' },
  OCEAN: { primary: '#0ea5e9', secondary: '#0c4a6e', bg: ['#082f49', '#0c4a6e'], name: 'Ocean Blue' },
};

const ThemeContext = createContext({
  theme: THEMES.PREMIUM,
  setTheme: () => {},
});

// --- Offline & Sync Manager ---
const SyncManager = {
  queueRequest: async (payload) => {
    try {
      const q = await AsyncStorage.getItem('offline_queue');
      const queue = q ? JSON.parse(q) : [];
      queue.push({ ...payload, id: Date.now() });
      await AsyncStorage.setItem('offline_queue', JSON.stringify(queue));
    } catch (e) {}
  },
  getQueueCount: async () => {
    const q = await AsyncStorage.getItem('offline_queue');
    return q ? JSON.parse(q).length : 0;
  },
  sync: async () => {
    try {
      const q = await AsyncStorage.getItem('offline_queue');
      if (!q) return { success: 0, failed: 0 };
      const queue = JSON.parse(q);
      let success = 0;
      let failedRows = [];

      for (const item of queue) {
        try {
          const resp = await fetch(API_URL, {
            method: 'POST',
            body: JSON.stringify(item)
          });
          const res = await resp.json();
          if (res.status === 'success') success++;
          else failedRows.push(item);
        } catch (e) {
          failedRows.push(item);
        }
      }
      await AsyncStorage.setItem('offline_queue', JSON.stringify(failedRows));
      return { success, failed: failedRows.length };
    } catch (e) { return { success: 0, failed: 0 }; }
  }
};

// --- Notification Setup ---
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

// --- Components ---

const GlassCard = ({ children, style }) => (
  <View style={[styles.glassCard, style]}>
    {children}
  </View>
);

const CustomButton = ({ title, onPress, icon: Icon, color, loading, style }) => {
  const { theme } = useContext(ThemeContext);
  return (
    <TouchableOpacity 
      style={[styles.button, { backgroundColor: color || theme.primary }, style]} 
      onPress={onPress}
      disabled={loading}
    >
      {loading ? (
        <ActivityIndicator color="#fff" />
      ) : (
        <>
          {Icon && <Icon size={22} color="#fff" style={{ marginRight: 10 }} />}
          <Text style={styles.buttonText}>{title}</Text>
        </>
      )}
    </TouchableOpacity>
  );
};

const BPZone = (sys, dia) => {
  if (!sys || !dia) return { label: 'Unknown', color: '#94a3b8' };
  if (sys < 120 && dia < 80) return { label: 'Normal', color: '#22c55e' };
  if (sys < 130 && dia < 80) return { label: 'Elevated', color: '#eab308' };
  if (sys < 140 || dia < 90) return { label: 'High (Stage 1)', color: '#f97316' };
  return { label: 'High (Stage 2)', color: '#ef4444' };
};

// --- Screens ---

const LoginScreen = ({ onLogin }) => {
  const { theme } = useContext(ThemeContext);
  const [mode, setMode] = useState('LOADING'); // LOADING, SETUP_PIN, SETUP_QUESTIONS, LOGIN, FORGOT
  const [pin, setPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [loading, setLoading] = useState(false);
  const [q1, setQ1] = useState("What was the name of your first pet?");
  const [a1, setA1] = useState('');
  const [q2, setQ2] = useState("What city were you born in?");
  const [a2, setA2] = useState('');
  const [resetData, setResetData] = useState({ q1: '', q2: '' });
  const [resetAns1, setResetAns1] = useState('');
  const [resetAns2, setResetAns2] = useState('');

  useEffect(() => { checkInitialization(); }, []);

  const checkInitialization = async () => {
    try {
      const resp = await fetch(API_URL, { method: 'POST', body: JSON.stringify({ action: 'CHECK_INIT' }) });
      const { initialized } = await resp.json();
      setMode(initialized ? 'LOGIN' : 'SETUP_PIN');
    } catch (e) { setMode('LOGIN'); }
  };

  const handleSetupPin = () => {
    if (pin.length === 4 && pin === confirmPin) { setMode('SETUP_QUESTIONS'); }
    else { Alert.alert("Error", "PINs must match and be 4 digits."); }
  };

  const handleCompleteSetup = async () => {
    if (!a1 || !a2) return Alert.alert("Error", "Please answer both questions.");
    setLoading(true);
    try {
      await fetch(API_URL, {
        method: 'POST',
        body: JSON.stringify({ action: 'INITIALIZE_USER', pin, q1, a1, q2, a2, timestamp: new Date().toISOString() })
      });
      await AsyncStorage.setItem('user_session', 'active');
      onLogin();
    } catch (e) { Alert.alert("Error", "Could not save setup."); }
    setLoading(false);
  };

  const handleLogin = async () => {
    if (pin.length < 4) return;
    setLoading(true);
    try {
      const resp = await fetch(API_URL, { method: 'POST', body: JSON.stringify({ action: 'VERIFY_PIN', pin }) });
      const result = await resp.json();
      if (result.status === 'success') {
        await AsyncStorage.setItem('user_session', 'active');
        onLogin();
      } else {
        Alert.alert("Access Denied", "Incorrect PIN.");
        setPin('');
      }
    } catch (e) { Alert.alert("Offline", "Offline login enabled for authorized sessions."); 
      const active = await AsyncStorage.getItem('user_session');
      if (active === 'active') onLogin();
    }
    setLoading(false);
  };

  if (mode === 'LOADING') return (
    <LinearGradient colors={theme.bg} style={styles.container}>
      <ActivityIndicator size="large" color={theme.primary} style={{ flex: 1 }} />
    </LinearGradient>
  );

  return (
    <LinearGradient colors={theme.bg} style={styles.container}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={{ flex: 1 }}>
      <ScrollView contentContainerStyle={styles.centerContent}>
        <View style={[styles.loginLogo, { borderColor: theme.primary + '4d', backgroundColor: theme.primary + '1a' }]}>
          <Lock size={40} color={theme.primary} />
        </View>

        {mode === 'SETUP_PIN' && (
          <>
            <Text style={styles.title}>Welcome!</Text>
            <Text style={styles.subtitle}>Create your 4-digit security PIN</Text>
            <TextInput style={styles.pinInput} value={pin} onChangeText={setPin} keyboardType="numeric" maxLength={4} secureTextEntry placeholder="New PIN" placeholderTextColor="rgba(255,255,255,0.2)" />
            <TextInput style={styles.pinInput} value={confirmPin} onChangeText={setConfirmPin} keyboardType="numeric" maxLength={4} secureTextEntry placeholder="Confirm PIN" placeholderTextColor="rgba(255,255,255,0.2)" />
            <CustomButton title="Next Step" icon={ChevronRight} onPress={handleSetupPin} />
          </>
        )}

        {mode === 'SETUP_QUESTIONS' && (
          <>
            <Text style={styles.title}>Security Questions</Text>
            <Text style={styles.subtitle}>These help you recover your PIN</Text>
            <View style={styles.formCard}>
              <Text style={styles.label}>{q1}</Text>
              <TextInput style={styles.inputSmall} value={a1} onChangeText={setA1} placeholder="Your answer" placeholderTextColor="#64748b" />
              <Text style={styles.label}>{q2}</Text>
              <TextInput style={styles.inputSmall} value={a2} onChangeText={setA2} placeholder="Your answer" placeholderTextColor="#64748b" />
              <CustomButton title="Complete Setup" icon={CheckCircle2} onPress={handleCompleteSetup} loading={loading} />
            </View>
          </>
        )}

        {mode === 'LOGIN' && (
          <>
            <Text style={styles.title}>Mom's Tracker</Text>
            <Text style={styles.subtitle}>Enter PIN to continue</Text>
            <TextInput style={styles.pinInput} value={pin} onChangeText={setPin} keyboardType="numeric" maxLength={4} secureTextEntry placeholder="••••" placeholderTextColor="rgba(255,255,255,0.2)" autoFocus />
            <TouchableOpacity style={[styles.loginBtn, pin.length === 4 && { backgroundColor: theme.primary }]} onPress={handleLogin} disabled={loading || pin.length < 4}>
              <Text style={styles.loginBtnText}>Unlock</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={async () => {
              setLoading(true);
              const resp = await fetch(API_URL, { method: 'POST', body: JSON.stringify({ action: 'GET_QUESTIONS' }) });
              const data = await resp.json();
              setResetData(data);
              setMode('FORGOT');
              setLoading(false);
            }} style={{ marginTop: 20 }}>
              <Text style={{ color: '#94a3b8' }}>Forgot PIN?</Text>
            </TouchableOpacity>
          </>
        )}

        {mode === 'FORGOT' && (
          <View style={styles.formCard}>
            <Text style={styles.titleSmall}>Reset PIN</Text>
            <Text style={styles.label}>{resetData.q1}</Text>
            <TextInput style={styles.inputSmall} value={resetAns1} onChangeText={setResetAns1} placeholder="Answer 1" />
            <Text style={styles.label}>{resetData.q2}</Text>
            <TextInput style={styles.inputSmall} value={resetAns2} onChangeText={setResetAns2} placeholder="Answer 2" />
            <Text style={styles.label}>New PIN</Text>
            <TextInput style={styles.inputSmall} value={pin} onChangeText={setPin} keyboardType="numeric" maxLength={4} secureTextEntry placeholder="••••" />
            <CustomButton title="Reset & Login" icon={CheckCircle2} onPress={async () => {
              setLoading(true);
              const resp = await fetch(API_URL, { method: 'POST', body: JSON.stringify({ action: 'RESET_PIN', a1: resetAns1, a2: resetAns2, newPin: pin }) });
              const res = await resp.json();
              setLoading(false);
              if (res.status === 'success') { setMode('LOGIN'); setPin(''); } 
              else { Alert.alert("Error", "Wrong answers."); }
            }} loading={loading} />
          </View>
        )}
      </ScrollView>
      </KeyboardAvoidingView>
    </LinearGradient>
  );
};

const HomeScreen = ({ navigation }) => {
  const { theme } = useContext(ThemeContext);
  const [goals, setGoals] = useState({ weight: '', bp_sys: '' });
  const [latest, setLatest] = useState(null);
  const [syncCount, setSyncCount] = useState(0);

  const loadData = async () => {
    try {
      const g = await AsyncStorage.getItem('health_goals');
      if (g) setGoals(JSON.parse(g));
      const count = await SyncManager.getQueueCount();
      setSyncCount(count);
      const response = await fetch(API_URL);
      const json = await response.json();
      if (json.length > 0) setLatest(json[0]);
    } catch (e) {}
  };

  useFocusEffect(React.useCallback(() => { loadData(); }, []));

  useEffect(() => {
    const setupNotifications = async () => {
      const { status } = await Notifications.requestPermissionsAsync();
      if (status !== 'granted') return;
      await Notifications.cancelAllScheduledNotificationsAsync();
      await Notifications.scheduleNotificationAsync({
        content: { title: "Mom, time for a check! ❤️", body: "Don't forget to log your BP and weight today." },
        trigger: { hour: 9, minute: 0, repeats: true },
      });
    };
    setupNotifications();
  }, []);

  const handleSync = async () => {
    Alert.alert("Syncing", "Sending offline logs to cloud...");
    const res = await SyncManager.sync();
    setSyncCount(await SyncManager.getQueueCount());
    Alert.alert("Sync Finished", `Successfully updated ${res.success} logs.`);
  };

  return (
    <LinearGradient colors={theme.bg} style={styles.container}>
      <StatusBar barStyle="light-content" />
      <View style={styles.header}>
        <View style={styles.navHeader}>
          <View>
            <Text style={styles.title}>Mom's Tracker</Text>
            <Text style={styles.subtitle}>Premium Health Suite</Text>
          </View>
          <View style={{ flexDirection: 'row', gap: 15 }}>
            {syncCount > 0 && (
              <TouchableOpacity onPress={handleSync} style={styles.syncIndicator}>
                <RefreshCw size={20} color={theme.primary} />
                <Text style={styles.syncText}>{syncCount}</Text>
              </TouchableOpacity>
            )}
            <TouchableOpacity onPress={() => navigation.navigate('Settings')}>
              <SettingsIcon size={24} color="#94a3b8" />
            </TouchableOpacity>
          </View>
        </View>

        {latest && goals.weight && (
          <GlassCard style={[styles.goalProgressCard, { borderLeftColor: theme.primary }]}>
            <View style={styles.goalRow}>
              <Text style={styles.goalLabel}>Weight Goal: {goals.weight}kg</Text>
              <Text style={styles.goalValue}>{latest.weight}kg</Text>
            </View>
            <View style={styles.progressBarBg}>
              <View style={[styles.progressBarFill, { backgroundColor: theme.primary, width: `${Math.min(100, (parseFloat(latest.weight) / parseFloat(goals.weight)) * 100)}%` }]} />
            </View>
          </GlassCard>
        )}
      </View>

      <ScrollView style={styles.menuScroll} contentContainerStyle={styles.menuContainer}>
        <View style={styles.grid}>
          <TouchableOpacity style={styles.gridItem} onPress={() => navigation.navigate('LogWeight')}>
            <GlassCard style={styles.gridCard}>
              <Weight size={32} color="#06b6d4" />
              <Text style={styles.gridText}>Log Weight</Text>
            </GlassCard>
          </TouchableOpacity>
          <TouchableOpacity style={styles.gridItem} onPress={() => navigation.navigate('LogBP')}>
            <GlassCard style={styles.gridCard}>
              <Heart size={32} color="#ec4899" />
              <Text style={styles.gridText}>Log BP</Text>
            </GlassCard>
          </TouchableOpacity>
        </View>

        <TouchableOpacity onPress={() => navigation.navigate('Mood')}>
          <GlassCard style={[styles.actionCard, { borderColor: '#eab308' }]}>
            <Smile size={28} color="#eab308" />
            <View style={{ flex: 1 }}>
              <Text style={styles.actionText}>Mood & Symptoms</Text>
              <Text style={styles.actionSubtext}>How are you feeling today?</Text>
            </View>
            <Plus size={20} color="#eab308" />
          </GlassCard>
        </TouchableOpacity>

        <TouchableOpacity onPress={() => navigation.navigate('Medication')}>
          <GlassCard style={[styles.actionCard, { borderColor: '#10b981' }]}>
            <Pill size={28} color="#10b981" />
            <Text style={styles.actionText}>Medication Tracker</Text>
            <Plus size={20} color="#10b981" />
          </GlassCard>
        </TouchableOpacity>

        <View style={styles.divider} />

        <View style={styles.grid}>
          <TouchableOpacity style={styles.gridItem} onPress={() => navigation.navigate('Trends')}>
            <GlassCard style={styles.gridCard}>
              <TrendingUp size={28} color="#22c55e" />
              <Text style={styles.gridTextSmall}>View Trends</Text>
            </GlassCard>
          </TouchableOpacity>
          <TouchableOpacity style={styles.gridItem} onPress={() => navigation.navigate('History')}>
            <GlassCard style={styles.gridCard}>
              <HistoryIcon size={28} color="#f59e0b" />
              <Text style={styles.gridTextSmall}>Log History</Text>
            </GlassCard>
          </TouchableOpacity>
        </View>

        <TouchableOpacity onPress={() => navigation.navigate('Goals')}>
          <GlassCard style={styles.actionCard}>
            <Target size={28} color={theme.primary} />
            <Text style={styles.actionText}>Set Health Goals</Text>
          </GlassCard>
        </TouchableOpacity>

        <TouchableOpacity onPress={() => navigation.navigate('Report')}>
          <GlassCard style={[styles.actionCard, { backgroundColor: 'rgba(255,255,255,0.08)' }]}>
            <FileDown size={28} color="#fff" />
            <Text style={styles.actionText}>Generate Doctor Report</Text>
          </GlassCard>
        </TouchableOpacity>
      </ScrollView>
    </LinearGradient>
  );
};

const MoodScreen = ({ navigation }) => {
  const { theme } = useContext(ThemeContext);
  const [mood, setMood] = useState(null);
  const [symptom, setSymptom] = useState([]);
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);

  const MOODS = [
    { icon: Smile, label: 'Happy', color: '#22c55e' },
    { icon: Meh, label: 'Okay', color: '#eab308' },
    { icon: Frown, label: 'Low', color: '#ef4444' },
    { icon: AlertCircle, label: 'Pain', color: '#f97316' },
  ];

  const SYMPTOMS = ['Headache', 'Dizziness', 'Tiredness', 'Joint Pain', 'Drowsy', 'Short Breath'];

  const toggleSymptom = (s) => {
    if (symptom.includes(s)) setSymptom(symptom.filter(x => x !== s));
    else setSymptom([...symptom, s]);
  };

  const handleSave = async () => {
    if (!mood) return Alert.alert("Error", "Please select a mood");
    setLoading(true);
    const payload = { 
      action: 'LOG_MOOD', 
      mood, 
      symptoms: symptom.join(', '), 
      notes, 
      timestamp: new Date().toISOString() 
    };
    try {
      await fetch(API_URL, { method: 'POST', body: JSON.stringify(payload) });
      navigation.navigate('Success');
    } catch (e) {
      await SyncManager.queueRequest(payload);
      Alert.alert("Offline", "Saved to phone. Will sync when online.");
      navigation.navigate('Success');
    }
    setLoading(false);
  };

  return (
    <LinearGradient colors={theme.bg} style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <ChevronLeft size={24} color="#fff" />
          <Text style={styles.backText}>Back</Text>
        </TouchableOpacity>
        <Text style={styles.screenTitle}>How are you?</Text>
      </View>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.label}>Select your mood</Text>
        <View style={styles.moodGrid}>
          {MOODS.map((m, i) => (
            <TouchableOpacity key={i} onPress={() => setMood(m.label)} style={[styles.moodItem, mood === m.label && { backgroundColor: m.color, borderColor: m.color }]}>
              <m.icon size={32} color={mood === m.label ? '#fff' : m.color} />
              <Text style={[styles.moodLabel, mood === m.label && { color: '#fff' }]}>{m.label}</Text>
            </TouchableOpacity>
          ))}
        </View>

        <Text style={[styles.label, { marginTop: 25 }]}>Any symptoms?</Text>
        <View style={styles.chipGrid}>
          {SYMPTOMS.map((s, i) => (
            <TouchableOpacity key={i} onPress={() => toggleSymptom(s)} style={[styles.chip, symptom.includes(s) && { backgroundColor: theme.primary, borderColor: theme.primary }]}>
              <Text style={[styles.chipText, symptom.includes(s) && { color: '#fff' }]}>{s}</Text>
            </TouchableOpacity>
          ))}
        </View>

        <Text style={[styles.label, { marginTop: 25 }]}>Notes (Optional)</Text>
        <TextInput style={styles.textArea} multiline numberOfLines={4} placeholder="Type how you feel..." placeholderTextColor="#64748b" value={notes} onChangeText={setNotes} />
        
        <CustomButton title="Save Log" icon={CheckCircle2} onPress={handleSave} loading={loading} style={{ marginTop: 20 }} />
      </ScrollView>
    </LinearGradient>
  );
};

const HistoryScreen = ({ navigation }) => {
  const { theme } = useContext(ThemeContext);
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchData = async () => {
    setLoading(true);
    try {
      const resp = await fetch(API_URL);
      const json = await resp.json();
      setData(json);
    } catch (e) { Alert.alert("Error", "Could not fetch history"); }
    setLoading(false);
  };

  useFocusEffect(React.useCallback(() => { fetchData(); }, []));

  const handleDelete = (id, type) => {
    Alert.alert("Delete Entry?", "This will permanently remove this record.", [
      { text: "Cancel" },
      { text: "Delete", style: "destructive", onPress: async () => {
        try {
          await fetch(API_URL, { method: 'POST', body: JSON.stringify({ action: 'DELETE_LOG', id, type: 'LOGS' }) });
          fetchData();
        } catch (e) { Alert.alert("Error", "Cannot delete while offline"); }
      }}
    ]);
  };

  return (
    <LinearGradient colors={theme.bg} style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <ChevronLeft size={24} color="#fff" />
          <Text style={styles.backText}>Back</Text>
        </TouchableOpacity>
        <Text style={styles.screenTitle}>History</Text>
      </View>

      {loading ? (
        <ActivityIndicator size="large" color={theme.primary} style={{ marginTop: 50 }} />
      ) : (
        <ScrollView contentContainerStyle={styles.scrollContent}>
          {data.map((item, index) => {
            const zone = BPZone(item.bp_systolic, item.bp_diastolic);
            return (
              <GlassCard key={index} style={styles.historyCard}>
                <View style={styles.historyInfo}>
                  <Text style={styles.historyDate}>{item.date} • {item.time}</Text>
                  <Text style={styles.historyType}>{item.type}</Text>
                  {item.bp_systolic && <Text style={[styles.zoneText, { color: zone.color }]}>● {zone.label}</Text>}
                </View>
                <View style={styles.historyValues}>
                  {item.weight && (
                    <View style={styles.vBadge}>
                      <Text style={styles.vLabel}>{item.weight}kg</Text>
                    </View>
                  )}
                  {item.bp_systolic && (
                    <View style={[styles.vBadge, { backgroundColor: zone.color + '33' }]}>
                      <Text style={[styles.vLabel, { color: '#fff' }]}>{item.bp_systolic}/{item.bp_diastolic}</Text>
                    </View>
                  )}
                  <TouchableOpacity onPress={() => handleDelete(item.timestamp, 'LOGS')} style={styles.deleteBtn}>
                    <Trash2 size={16} color="#ef4444" />
                  </TouchableOpacity>
                </View>
              </GlassCard>
            );
          })}
        </ScrollView>
      )}
    </LinearGradient>
  );
};

const SettingsScreen = ({ navigation, onLogout }) => {
  const { theme, setTheme } = useContext(ThemeContext);
  
  const logout = async () => {
    await AsyncStorage.removeItem('user_session');
    onLogout();
  };

  return (
    <LinearGradient colors={theme.bg} style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <ChevronLeft size={24} color="#fff" />
          <Text style={styles.backText}>Back</Text>
        </TouchableOpacity>
        <Text style={styles.screenTitle}>Settings</Text>
      </View>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.label}>Choose Theme</Text>
        <View style={styles.themeGrid}>
          {Object.entries(THEMES).map(([key, t]) => (
            <TouchableOpacity key={key} onPress={() => setTheme(t)} style={[styles.themeItem, theme.name === t.name && { borderColor: theme.primary, borderWidth: 2 }]}>
              <View style={[styles.themeCircle, { backgroundColor: t.primary }]} />
              <Text style={styles.themeName}>{t.name}</Text>
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.divider} />

        <TouchableOpacity onPress={logout}>
          <GlassCard style={[styles.actionCard, { borderColor: '#ef4444' }]}>
            <LogOut size={28} color="#ef4444" />
            <Text style={[styles.actionText, { color: '#ef4444' }]}>Logout</Text>
          </GlassCard>
        </TouchableOpacity>
        <Text style={{ color: '#94a3b8', marginTop: 20, textAlign: 'center' }}>Version 3.2.0 • ReachShwet Edition</Text>
      </ScrollView>
    </LinearGradient>
  );
};

// ... Rest of screens (LogScreen, MedicationScreen, etc. updated with SyncManager)
const LogScreen = ({ navigation, type }) => {
  const { theme } = useContext(ThemeContext);
  const [weight, setWeight] = useState('');
  const [sys, setSys] = useState('');
  const [dia, setDia] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSave = async () => {
    const payload = { 
      timestamp: new Date().toISOString(), 
      type, 
      weight: parseFloat(weight) || null, 
      bp_sys: parseInt(sys) || null, 
      bp_dia: parseInt(dia) || null 
    };
    setLoading(true);
    try {
      await fetch(API_URL, { method: 'POST', body: JSON.stringify(payload) });
      navigation.navigate('Success');
    } catch (e) {
      await SyncManager.queueRequest(payload);
      Alert.alert("Offline", "Saved to phone.");
      navigation.navigate('Success');
    }
    setLoading(false);
  };

  return (
    <LinearGradient colors={theme.bg} style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}><ChevronLeft size={24} color="#fff"/><Text style={styles.backText}>Back</Text></TouchableOpacity>
        <Text style={styles.screenTitle}>{type}</Text>
        <GlassCard style={styles.formCard}>
          {(type === 'WEIGHT' || type === 'BOTH') && (
            <View style={styles.inputGroup}><Text style={styles.label}>Weight (kg)</Text><TextInput style={styles.input} keyboardType="numeric" value={weight} onChangeText={setWeight}/></View>
          )}
          {(type === 'BP' || type === 'BOTH') && (
            <View style={{ flexDirection: 'row', gap: 10 }}>
              <View style={{ flex: 1 }}><Text style={styles.label}>Systolic</Text><TextInput style={styles.input} keyboardType="numeric" value={sys} onChangeText={setSys}/></View>
              <View style={{ flex: 1 }}><Text style={styles.label}>Diastolic</Text><TextInput style={styles.input} keyboardType="numeric" value={dia} onChangeText={setDia}/></View>
            </View>
          )}
          <CustomButton title="Save Entry" icon={Plus} onPress={handleSave} loading={loading} />
        </GlassCard>
      </ScrollView>
    </LinearGradient>
  );
};

// Standard Screen Templates for simple screens
const MedicationScreen = ({ navigation }) => {
  const { theme } = useContext(ThemeContext);
  const [meds, setMeds] = useState([]);
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);

  const f = async () => { const r = await fetch(API_URL + "?type=MEDS"); setMeds(await r.json()); };
  useEffect(() => { f(); }, []);

  const h = async () => {
    const p = { action: 'LOG_MED', name, timestamp: new Date().toISOString() };
    setLoading(true);
    try { await fetch(API_URL, { method: 'POST', body: JSON.stringify(p)}); f(); setName(''); } 
    catch(e) { await SyncManager.queueRequest(p); Alert.alert("Offline", "Will sync later"); }
    setLoading(false);
  };

  return (
    <LinearGradient colors={theme.bg} style={styles.container}>
      <View style={styles.header}><Text style={styles.screenTitle}>Medications</Text></View>
      <View style={{ paddingHorizontal: 24 }}><TextInput style={styles.inputSmall} placeholder="Medicine Name" value={name} onChangeText={setName}/><CustomButton title="Log Taken" color="#10b981" onPress={h} loading={loading}/></View>
      <ScrollView style={{ padding: 24 }}>{meds.map((m, i) => (
        <GlassCard key={i} style={styles.medCard}><View><Text style={styles.medName}>{m.med_name}</Text><Text style={styles.historyDate}>{m.date} • {m.time}</Text></View><CheckCircle2 size={24} color="#10b981"/></GlassCard>
      ))}</ScrollView>
    </LinearGradient>
  );
};

// Simplified Success, Goals, and Report screens
const SuccessScreen = ({ navigation }) => {
  const { theme } = useContext(ThemeContext);
  return (
    <LinearGradient colors={theme.bg} style={styles.container}>
      <View style={styles.centerContent}><CheckCircle2 size={100} color="#22c55e" /><Text style={styles.successTitle}>Logged Successfully!</Text><TouchableOpacity style={styles.homeButton} onPress={() => navigation.popToTop()}><Text style={styles.homeButtonText}>Return Home</Text></TouchableOpacity></View>
    </LinearGradient>
  );
};

const GoalsScreen = ({ navigation }) => {
  const { theme } = useContext(ThemeContext);
  const [w, setW] = useState('');
  const save = async () => { await AsyncStorage.setItem('health_goals', JSON.stringify({ weight: w })); navigation.goBack(); };
  return (
    <LinearGradient colors={theme.bg} style={styles.container}>
      <View style={styles.header}><Text style={styles.screenTitle}>Goals</Text></View>
      <View style={styles.scrollContent}><GlassCard style={styles.formCard}><Text style={styles.label}>Weight Goal (kg)</Text><TextInput style={styles.input} keyboardType="numeric" value={w} onChangeText={setW}/><CustomButton title="Save" onPress={save}/></GlassCard></View>
    </LinearGradient>
  );
};

const ReportScreen = ({ navigation }) => {
  const { theme } = useContext(ThemeContext);
  const [loading, setLoading] = useState(false);
  const gen = async () => {
    setLoading(true); try {
      const resp = await fetch(API_URL); const data = await resp.json();
      let rows = data.map(r => `<tr><td>${r.date}</td><td>${r.weight || '—'}</td><td>${r.bp_systolic ? `${r.bp_systolic}/${r.bp_diastolic}` : '—'}</td></tr>`).join('');
      const { uri } = await Print.printToFileAsync({ html: `<h1>Health Report</h1><table>${rows}</table>` });
      await Sharing.shareAsync(uri);
    } catch(e) { Alert.alert("Error", "Failed"); } setLoading(false);
  };
  return (
    <LinearGradient colors={theme.bg} style={styles.container}>
      <View style={styles.centerContent}><FileDown size={80} color="#fff"/><Text style={styles.successTitle}>Doctor Report</Text><CustomButton title="Share PDF" onPress={gen} loading={loading}/></View>
    </LinearGradient>
  );
};

const TrendsScreen = ({ navigation }) => {
  const { theme } = useContext(ThemeContext);
  const [data, setData] = useState([]);
  useEffect(() => { fetch(API_URL).then(r => r.json()).then(j => setData(j.reverse())); }, []);
  const wd = data.filter(d => d.weight).slice(-7);
  return (
    <LinearGradient colors={theme.bg} style={styles.container}>
      <View style={styles.header}><Text style={styles.screenTitle}>Trends</Text></View>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        {wd.length > 1 && <LineChart data={{ labels: wd.map(d => d.date.split('/')[0]), datasets: [{ data: wd.map(d => parseFloat(d.weight)) }] }} width={Dimensions.get('window').width - 48} height={200} chartConfig={{ backgroundColor: theme.secondary, backgroundGradientFrom: theme.secondary, backgroundGradientTo: theme.secondary, color: (o) => `rgba(255,255,255,${o})` }} bezier style={{ borderRadius: 16 }} />}
      </ScrollView>
    </LinearGradient>
  );
};

export default function App() {
  const [theme, setTheme] = useState(THEMES.PREMIUM);
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    AsyncStorage.getItem('app_theme').then(val => { if (val) setTheme(JSON.parse(val)); });
    AsyncStorage.getItem('user_session').then(val => { if (val === 'active') setIsLoggedIn(true); setChecking(false); });
  }, []);

  const updateTheme = (t) => { setTheme(t); AsyncStorage.setItem('app_theme', JSON.stringify(t)); };

  if (checking) return null;

  return (
    <ThemeContext.Provider value={{ theme, setTheme: updateTheme }}>
      <NavigationContainer>
        <Stack.Navigator screenOptions={{ headerShown: false }}>
          {!isLoggedIn ? (
            <Stack.Screen name="Login">{(p) => <LoginScreen {...p} onLogin={() => setIsLoggedIn(true)} />}</Stack.Screen>
          ) : (
            <>
              <Stack.Screen name="Home">{(p) => <HomeScreen {...p} onLogout={() => setIsLoggedIn(false)} />}</Stack.Screen>
              <Stack.Screen name="LogWeight">{(p) => <LogScreen {...p} type="WEIGHT" />}</Stack.Screen>
              <Stack.Screen name="LogBP">{(p) => <LogScreen {...p} type="BP" />}</Stack.Screen>
              <Stack.Screen name="LogBoth">{(p) => <LogScreen {...p} type="BOTH" />}</Stack.Screen>
              <Stack.Screen name="Mood" component={MoodScreen} />
              <Stack.Screen name="Medication" component={MedicationScreen} />
              <Stack.Screen name="Goals" component={GoalsScreen} />
              <Stack.Screen name="Report" component={ReportScreen} />
              <Stack.Screen name="Success" component={SuccessScreen} />
              <Stack.Screen name="History" component={HistoryScreen} />
              <Stack.Screen name="Trends" component={TrendsScreen} />
              <Stack.Screen name="Settings">{(p) => <SettingsScreen {...p} onLogout={() => setIsLoggedIn(false)} />}</Stack.Screen>
            </>
          )}
        </Stack.Navigator>
      </NavigationContainer>
    </ThemeContext.Provider>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, paddingTop: 50 },
  header: { paddingHorizontal: 24, marginBottom: 20 },
  navHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  title: { fontSize: 32, fontWeight: '800', color: '#fff' },
  titleSmall: { fontSize: 24, fontWeight: '700', color: '#fff', marginBottom: 15 },
  subtitle: { fontSize: 16, color: '#94a3b8' },
  scrollContent: { padding: 24 },
  menuScroll: { flex: 1 },
  menuContainer: { paddingHorizontal: 24, gap: 16, paddingBottom: 40 },
  grid: { flexDirection: 'row', gap: 16 },
  gridItem: { flex: 1 },
  gridCard: { padding: 20, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)' },
  gridText: { color: '#fff', fontSize: 16, fontWeight: '600', marginTop: 10 },
  gridTextSmall: { color: '#fff', fontSize: 14, fontWeight: '600', marginTop: 8 },
  actionSubtext: { color: '#94a3b8', fontSize: 12, marginTop: 2 },
  actionCard: { flexDirection: 'row', alignItems: 'center', padding: 20, gap: 16, borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)' },
  actionText: { color: '#fff', fontSize: 18, fontWeight: '600' },
  glassCard: { backgroundColor: 'rgba(255, 255, 255, 0.05)', borderRadius: 24, overflow: 'hidden' },
  divider: { height: 1, backgroundColor: 'rgba(255, 255, 255, 0.1)', marginVertical: 10 },
  screenTitle: { fontSize: 28, fontWeight: '700', color: '#fff' },
  formCard: { padding: 24, borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)' },
  inputGroup: { marginBottom: 20 },
  label: { fontSize: 14, color: '#94a3b8', marginBottom: 8 },
  input: { backgroundColor: 'rgba(0,0,0,0.2)', borderRadius: 16, padding: 18, fontSize: 20, color: '#fff', borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)' },
  inputSmall: { backgroundColor: 'rgba(0,0,0,0.2)', borderRadius: 12, padding: 12, fontSize: 16, color: '#fff', marginBottom: 15, borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)' },
  button: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', padding: 16, borderRadius: 16, marginTop: 10 },
  buttonText: { fontSize: 16, fontWeight: '700', color: '#fff' },
  backButton: { flexDirection: 'row', alignItems: 'center', marginBottom: 10 },
  backText: { color: '#fff', marginLeft: 4 },
  historyCard: { padding: 16, marginBottom: 12, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  historyDate: { color: '#94a3b8', fontSize: 12 },
  historyType: { color: '#fff', fontSize: 16, fontWeight: '600' },
  historyValues: { flexDirection: 'row', gap: 6, alignItems: 'center' },
  vBadge: { backgroundColor: 'rgba(6, 182, 212, 0.1),', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 10 },
  vLabel: { color: '#fff', fontSize: 12, fontWeight: '700' },
  zoneText: { fontSize: 12, fontWeight: '700', marginTop: 4 },
  deleteBtn: { marginLeft: 8, padding: 6 },
  goalProgressCard: { padding: 16, marginTop: 15, borderLeftWidth: 4 },
  goalRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 },
  goalLabel: { color: '#94a3b8', fontSize: 12 },
  goalValue: { color: '#fff', fontSize: 12, fontWeight: '700' },
  progressBarBg: { height: 6, backgroundColor: 'rgba(255,255,255,0.1)', borderRadius: 3, marginTop: 4 },
  progressBarFill: { height: '100%', borderRadius: 3 },
  syncIndicator: { backgroundColor: 'rgba(255,255,255,0.1)', flexDirection: 'row', alignItems: 'center', paddingHorizontal: 10, paddingVertical: 6, borderRadius: 15, gap: 6 },
  syncText: { color: '#fff', fontSize: 12, fontWeight: '700' },
  moodGrid: { flexDirection: 'row', gap: 10, justifyContent: 'space-between', marginVertical: 10 },
  moodItem: { flex: 1, alignItems: 'center', padding: 15, borderRadius: 20, borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)', backgroundColor: 'rgba(255,255,255,0.03)' },
  moodLabel: { color: '#94a3b8', fontSize: 12, marginTop: 8, fontWeight: '600' },
  chipGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { paddingHorizontal: 16, paddingVertical: 10, borderRadius: 20, borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)', backgroundColor: 'rgba(255,255,255,0.05)' },
  chipText: { color: '#94a3b8', fontSize: 13 },
  textArea: { backgroundColor: 'rgba(0,0,0,0.2)', borderRadius: 20, padding: 15, fontSize: 16, color: '#fff', minHeight: 120, textAlignVertical: 'top', borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)' },
  themeGrid: { flexDirection: 'row', gap: 12, flexWrap: 'wrap' },
  themeItem: { width: '47%', padding: 15, borderRadius: 20, backgroundColor: 'rgba(255,255,255,0.05)', alignItems: 'center' },
  themeCircle: { width: 30, height: 30, borderRadius: 15, marginBottom: 10 },
  themeName: { color: '#fff', fontSize: 14, fontWeight: '600' },
  loginLogo: { width: 80, height: 80, borderRadius: 40, alignItems: 'center', justifyContent: 'center', marginBottom: 24, borderWidth: 1 },
  pinInput: { fontSize: 32, color: '#fff', letterSpacing: 10, textAlign: 'center', padding: 20, backgroundColor: 'rgba(0,0,0,0.1)', borderRadius: 20, marginVertical: 10 },
  loginBtn: { width: '100%', padding: 18, borderRadius: 20, alignItems: 'center', backgroundColor: 'rgba(255,255,255,0.08)' },
  loginBtnText: { color: '#fff', fontSize: 18, fontWeight: '700' },
  medCard: { padding: 16, marginBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  medName: { color: '#fff', fontSize: 16, fontWeight: '600' },
  centerContent: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 40 },
  successTitle: { fontSize: 24, fontWeight: '700', color: '#fff', marginTop: 24 },
  homeButton: { marginTop: 40, paddingPaddingVertical: 16, paddingHorizontal: 32, borderRadius: 30, backgroundColor: 'rgba(255,255,255,0.1)' },
  homeButtonText: { color: '#fff', fontWeight: '600' }
});
