import { NativeEventEmitter, NativeModules } from 'react-native';

const M = NativeModules.GhostAssist;
export const emitter = new NativeEventEmitter(M);

export const GhostAssist = {
  hasOverlayPermission: (): Promise<boolean> => M.hasOverlayPermission(),
  openOverlaySettings: (): void => M.openOverlaySettings(),
  startCapture: (): Promise<boolean> => M.startCapture(),
  stop: (): void => M.stop(),
  showSuggestion: (json: string): void => M.showSuggestion(json),
  setAiConfig: (key: string, model: string): void => M.setAiConfig(key, model),
  clearAiKey: (): void => M.clearAiKey(),
  getAiConfig: (): Promise<{ hasKey: boolean; keyTail: string; model: string }> => M.getAiConfig(),
  learnTemplate: (kind: 'LZ' | 'QL', zone: 'field' | 'rest', a: number, b: number): Promise<boolean> =>
    M.learnTemplate(kind, zone, a, b),
};
