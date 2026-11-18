import AsyncStorage from '@react-native-async-storage/async-storage';

const URL = (process.env.EXPO_PUBLIC_SUPABASE_URL || '').replace(/\/$/, '');
const KEY = process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY || '';
const STORAGE = 'hypeou.auth.session';
type Sessao = { access_token: string; refresh_token: string; expires_at: number };
async function token(grant: string, body: object): Promise<Sessao> {
  if (!URL || !KEY) throw new Error('Configure EXPO_PUBLIC_SUPABASE_URL e EXPO_PUBLIC_SUPABASE_ANON_KEY.');
  const response = await fetch(`${URL}/auth/v1/token?grant_type=${grant}`, {
    method: 'POST', headers: { apikey: KEY, 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  });
  const data = await response.json();
  if (!response.ok || !data.access_token) throw new Error(response.status === 400 ? 'E-mail ou senha inválidos.' : (data.msg || data.error_description || 'Não foi possível entrar.'));
  const sessao = { access_token: data.access_token, refresh_token: data.refresh_token, expires_at: Date.now() + (data.expires_in || 3600) * 1000 };
  await AsyncStorage.setItem(STORAGE, JSON.stringify(sessao));
  return sessao;
}
export async function entrarHypeou(email: string, password: string) { await token('password', { email: email.trim(), password }); }
export async function sessaoHypeouValida(): Promise<boolean> {
  try {
    const raw = await AsyncStorage.getItem(STORAGE);
    if (!raw) return false;
    const s: Sessao = JSON.parse(raw);
    if (s.expires_at > Date.now() + 60000) return true;
    if (!s.refresh_token) return false;
    await token('refresh_token', { refresh_token: s.refresh_token });
    return true;
  } catch { return false; }
}
