import React, { useEffect, useRef, useState } from 'react';
import { Button, SafeAreaView, ScrollView, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import { GhostAssist, emitter } from './src/native';
import { boardFromNative, overlayFromSuggestion } from './src/bridge';
import { suggest } from './src/engine/solver';

export default function App() {
  const [perm, setPerm] = useState(false);
  const [running, setRunning] = useState(false);
  const [rest3, setRest3] = useState(false); // ô Khu Nghỉ Ngơi thứ 3 đã mở khoá chưa
  const [debug, setDebug] = useState(true); // hiện nhãn "app đọc được gì" trên màn hình
  const [log, setLog] = useState('');
  const [aiKey, setAiKey] = useState('');
  const [aiModel, setAiModel] = useState('');
  const [aiInfo, setAiInfo] = useState('');
  const rest3Ref = useRef(rest3);
  rest3Ref.current = rest3;
  const debugRef = useRef(debug);
  debugRef.current = debug;

  const refresh = () => GhostAssist.hasOverlayPermission().then(setPerm);
  const refreshAi = () => GhostAssist.getAiConfig().then((c) => {
    setAiInfo(c.hasKey ? `Đã lưu khoá …${c.keyTail} · model ${c.model}` : `Chưa có khoá · model mặc định ${c.model}`);
  });
  useEffect(() => { refresh(); refreshAi(); }, []);
  const saveAi = () => { GhostAssist.setAiConfig(aiKey, aiModel); setAiKey(''); refreshAi(); };

  useEffect(() => {
    const sub = emitter.addListener('GhostBoard', (json: string) => {
      try {
        const board = boardFromNative(json, rest3Ref.current ? 3 : 2);
        const s = suggest(board, { depth: 4, beam: 80 });
        GhostAssist.showSuggestion(overlayFromSuggestion(s, board, debugRef.current));
        setLog(`Nguồn: ${JSON.parse(json).source === 'ai' ? 'AI' : 'OCR'} · ${board.ghosts.length} Oán Linh\n` + JSON.stringify(board, null, 1));
      } catch (e: any) {
        GhostAssist.showSuggestion(JSON.stringify({ text: 'Lỗi giải: ' + e.message }));
      }
    });
    return () => sub.remove();
  }, []);

  const start = async () => setRunning(await GhostAssist.startCapture());
  const stop = () => { GhostAssist.stop(); setRunning(false); };
  const learn = async (kind: 'LZ' | 'QL') => {
    // đặt đúng loại đồng hành vào ô (hàng 1, cột 1) Khu Chiến Đấu rồi bấm
    const ok = await GhostAssist.learnTemplate(kind, 'field', 0, 0);
    setLog(ok ? `Đã học mẫu ${kind}` : 'Học mẫu thất bại');
  };

  return (
    <SafeAreaView style={s.root}>
      <ScrollView contentContainerStyle={s.body}>
        <Text style={s.h1}>Ghost Assist</Text>
        <Text>1) Cấp quyền hiển thị trên ứng dụng khác: {perm ? '✅' : '❌'}</Text>
        {!perm && <Button title="Mở cài đặt quyền" onPress={() => { GhostAssist.openOverlaySettings(); }} />}
        <Button title="Kiểm tra lại quyền" onPress={refresh} />
        <View style={s.row}><Text>Ô Khu Nghỉ Ngơi thứ 3 đã mở</Text><Switch value={rest3} onValueChange={setRest3} /></View>
        <View style={s.row}><Text>Hiện nhãn kiểm tra (HP, loại, cấp)</Text><Switch value={debug} onValueChange={setDebug} /></View>
        <Text style={s.h2}>2) Bật trợ lý</Text>
        <Button title={running ? 'Đang chạy — bấm để dừng' : 'Bật (cho phép chụp màn hình)'} onPress={running ? stop : start} disabled={!perm} />
        <Text style={s.hint}>Mở game, bấm nút 👻 nổi mỗi lượt để nhận gợi ý. Có thể chuyển sang game, app vẫn chạy nền.</Text>
        <Text style={s.h2}>3) Học mẫu nhận diện (tuỳ chọn)</Text>
        <Text style={s.hint}>Đặt đồng hành vào ô hàng 1 – cột 1 của Khu Chiến Đấu trong game, quay lại app rồi bấm.</Text>
        <Button title="Học: đây là Li Zhuiyuan" onPress={() => learn('LZ')} disabled={!running} />
        <Button title="Học: đây là Qin Li" onPress={() => learn('QL')} disabled={!running} />
        <Text style={s.h2}>4) Đọc bằng AI (Claude)</Text>
        <Text style={s.hint}>Bấm nút 🤖 nổi (thay vì 👻) khi OCR đọc sai. Ảnh chụp màn hình (đã che thanh trạng thái) được gửi tới Anthropic API; mỗi lần đọc tốn một ít token trong tài khoản của bạn. Dùng khoá riêng có đặt hạn mức chi tiêu.</Text>
        <TextInput style={s.input} value={aiKey} onChangeText={setAiKey} placeholder="Khoá API (sk-ant-…)" secureTextEntry autoCapitalize="none" autoCorrect={false} />
        <TextInput style={s.input} value={aiModel} onChangeText={setAiModel} placeholder="Model (để trống = mặc định)" autoCapitalize="none" autoCorrect={false} />
        <Button title="Lưu cấu hình AI" onPress={saveAi} />
        <Text style={s.hint}>{aiInfo}</Text>
        <Button title="Xoá khoá API" onPress={() => { GhostAssist.clearAiKey(); refreshAi(); }} />
        <Text style={s.h2}>Lần đọc gần nhất</Text>
        <Text style={s.mono}>{log}</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#fff' },
  body: { padding: 16, gap: 10 },
  h1: { fontSize: 24, fontWeight: '700' },
  h2: { fontSize: 16, fontWeight: '600', marginTop: 12 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  hint: { color: '#666' },
  input: { borderWidth: 1, borderColor: '#ccc', borderRadius: 6, padding: 8 },
  mono: { fontFamily: 'monospace', fontSize: 11 },
});
