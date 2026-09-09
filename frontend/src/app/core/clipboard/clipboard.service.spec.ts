import { ClipboardService } from './clipboard.service';

describe('ClipboardService', () => {
  const service = new ClipboardService();
  const original = navigator.clipboard;

  afterEach(() => {
    Object.defineProperty(navigator, 'clipboard', { value: original, configurable: true });
  });

  function stubClipboard(writeText: (text: string) => Promise<void>): void {
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    });
  }

  it('writes the exact text and reports success', async () => {
    const seen: string[] = [];
    stubClipboard(async (t) => {
      seen.push(t);
    });
    await expect(service.copy('https://esic.example/attendance?ref=abc')).resolves.toBe(true);
    expect(seen).toEqual(['https://esic.example/attendance?ref=abc']);
  });

  it('reports failure when the API rejects (permission denied, insecure context)', async () => {
    stubClipboard(() => Promise.reject(new Error('denied')));
    await expect(service.copy('x')).resolves.toBe(false);
  });

  it('reports failure when the Clipboard API is absent', async () => {
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
    await expect(service.copy('x')).resolves.toBe(false);
  });

  it('never reads the clipboard', () => {
    const readText = vi.fn();
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: async () => undefined, readText },
      configurable: true,
    });
    void service.copy('x');
    expect(readText).not.toHaveBeenCalled();
  });
});
