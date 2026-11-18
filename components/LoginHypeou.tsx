import React, { useState } from 'react';
import { ActivityIndicator, Image, KeyboardAvoidingView, Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { entrarHypeou } from '../services/authHypeou';

export default function LoginHypeou({ aoEntrar }: { aoEntrar: () => void }) {
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState('');
  const [carregando, setCarregando] = useState(false);
  async function entrar() {
    if (!email.trim() || !senha) { setErro('Preencha e-mail e senha.'); return; }
    setCarregando(true); setErro('');
    try { await entrarHypeou(email, senha); aoEntrar(); }
    catch (e) { setErro(e instanceof Error ? e.message : 'Falha ao entrar.'); }
    finally { setCarregando(false); }
  }
  return <View style={styles.overlay}><KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.centro}>
    <Image source={require('../assets/images/hypeou_logo.png')} style={styles.logo} resizeMode="contain" />
    <Text style={styles.titulo}>Hypeou TV</Text>
    <Text style={styles.subtitulo}>Entre com a mesma conta do Hypeou</Text>
    <TextInput style={styles.campo} placeholder="E-mail" placeholderTextColor="#9299ad" autoCapitalize="none" keyboardType="email-address" value={email} onChangeText={setEmail} />
    <TextInput style={styles.campo} placeholder="Senha" placeholderTextColor="#9299ad" secureTextEntry value={senha} onChangeText={setSenha} onSubmitEditing={entrar} />
    {!!erro && <Text style={styles.erro}>{erro}</Text>}
    <Pressable style={styles.botao} onPress={entrar} disabled={carregando}>{carregando ? <ActivityIndicator color="#081323" /> : <Text style={styles.botaoTexto}>Entrar</Text>}</Pressable>
  </KeyboardAvoidingView></View>;
}
const styles = StyleSheet.create({ overlay: { ...StyleSheet.absoluteFillObject, zIndex: 99999, elevation: 99999, backgroundColor: '#080d1b' }, centro: { flex: 1, justifyContent: 'center', paddingHorizontal: 32, alignItems: 'center' }, logo: { width: 96, height: 96 }, titulo: { color: 'white', fontSize: 30, fontWeight: '800', marginTop: 16 }, subtitulo: { color: '#b4bdd1', marginTop: 8, marginBottom: 32 }, campo: { width: '100%', color: 'white', backgroundColor: '#18223a', borderRadius: 12, padding: 16, marginBottom: 12 }, erro: { color: '#ff8e8e', marginBottom: 12 }, botao: { width: '100%', backgroundColor: '#4bd4e8', padding: 16, borderRadius: 12, alignItems: 'center', marginTop: 8 }, botaoTexto: { color: '#081323', fontWeight: '800', fontSize: 16 } });
