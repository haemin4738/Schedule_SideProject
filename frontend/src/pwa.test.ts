import { describe, expect, it } from 'vitest'
import indexHtml from '../index.html?raw'
import manifestRaw from '../public/manifest.webmanifest?raw'

// 홈 화면에 추가(PWA) 설정이 깨지지 않았는지 — 아이콘 파일 누락·manifest 오타는 빌드가 잡지 못한다
const publicFiles = new Set(
  Object.keys(import.meta.glob('../public/*', { query: '?url' })).map((path) => path.replace('../public', '')),
)

describe('PWA', () => {
  it('manifest_always_isStandaloneWithExistingIcons', () => {
    const manifest = JSON.parse(manifestRaw)

    expect(manifest.display).toBe('standalone')
    expect(manifest.start_url).toBe('/')
    expect(manifest.icons.map((icon: { sizes: string }) => icon.sizes)).toEqual(
      expect.arrayContaining(['192x192', '512x512']),
    )
    for (const icon of manifest.icons) {
      expect(publicFiles.has(icon.src), icon.src).toBe(true)
    }
  })

  it('indexHtml_always_linksManifestAndIosHomeScreenTags', () => {
    expect(indexHtml).toContain('<link rel="manifest" href="/manifest.webmanifest" />')
    expect(indexHtml).toContain('<link rel="apple-touch-icon" href="/apple-touch-icon.png" />')
    expect(indexHtml).toContain('name="apple-mobile-web-app-capable" content="yes"')
    // viewport-fit=cover 를 쓰면 h-screen 페이지가 홈 인디케이터만큼 넘친다 — safe-area 처리를 함께 넣을 때만 바꿀 것
    expect(indexHtml).toContain('<meta name="viewport" content="width=device-width, initial-scale=1.0" />')
    expect(publicFiles.has('/apple-touch-icon.png')).toBe(true)
  })

  it('indexHtml_always_keepsStrictOriginReferrerForOAuthCallback', () => {
    expect(indexHtml).toContain('<meta name="referrer" content="strict-origin" />')
  })
})
