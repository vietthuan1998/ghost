import { NativeEventEmitter, NativeModules } from 'react-native';

const M = NativeModules.GhostAssist;
export const emitter = new NativeEventEmitter(M);

export const GhostAssist = {
  hasOverlayPermission: (): Promise<boolean> => M.hasOverlayPermission(),
  openOverlaySettings: (): void => M.openOverlaySettings(),
  startCapture: (): Promise<boolean> => M.startCapture(),
  stop: (): void => M.stop(),
  showSuggestion: (json: string): void => M.showSuggestion(json),
  learnTemplate: (kind: 'LZ' | 'QL', zone: 'field' | 'rest', a: number, b: number): Promise<boolean> =>
    M.learnTemplate(kind, zone, a, b),
};
