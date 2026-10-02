import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

// 홈 화면에 추가(PWA) 설정이 깨지지 않았는지 — 아이콘 파일 누락·manifest 오타는 빌드가 잡지 못한다
const root = resolve(__dirname, '..')
const publicFile = (path: string) => resolve(root, 'public', path.replace(/^\//, ''))
const indexHtml = readFileSync(resolve(root, 'index.html'), 'utf8')

describe('PWA', () => {
  it('manifest_always_isStandaloneWithExistingIcons', () => {
    const manifest = JSON.parse(readFileSync(publicFile('manifest.webmanifest'), 'utf8'))

    expect(manifest.display).toBe('standalone')
    expect(manifest.start_url).toBe('/')
    expect(manifest.icons.map((icon: { sizes: string }) => icon.sizes)).toEqual(
      expect.arrayContaining(['192x192', '512x512']),
    )
    for (const icon of manifest.icons) {
      expect(existsSync(publicFile(icon.src)), icon.src).toBe(true)
    }
  })

  it('indexHtml_always_linksManifestAndIosHomeScreenTags', () => {
    expect(indexHtml).toContain('<link rel="manifest" href="/manifest.webmanifest" />')
    expect(indexHtml).toContain('<link rel="apple-touch-icon" href="/apple-touch-icon.png" />')
    expect(indexHtml).toContain('name="apple-mobile-web-app-capable" content="yes"')
    expect(indexHtml).toContain('viewport-fit=cover')
    expect(existsSync(publicFile('/apple-touch-icon.png'))).toBe(true)
  })

  it('indexHtml_always_keepsStrictOriginReferrerForOAuthCallback', () => {
    expect(indexHtml).toContain('<meta name="referrer" content="strict-origin" />')
  })
})
