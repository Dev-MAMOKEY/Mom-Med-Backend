/**
 * weather.ts — 날씨 × 질병 알림
 *
 * 백엔드 정합:
 *   - 백엔드 06 v2.1 (ref.weather_rules + WeatherAdvisory + rule_id 필드 추가)
 *   - 백엔드 07 v2.1 (매일 ETL + 푸시)
 *   - 기상청 단기예보 (TMX/TMN 자체 임계값 판정)
 *
 * v1.1 검수 반영:
 *   - rule_id 필드 (백엔드 06 v2.1 블로커 8번 해결)
 *   - requires_review optional (백엔드 06이 포함할 수 있음)
 *
 * Slice: F5 (weather-advisory)
 */

import { z } from 'zod';
import { SeverityKoEnum } from './common';

// ============================================================================
// 시드 인용
// ============================================================================

export const SourceCitationSchema = z.object({
  file: z.string(),                             // 예: 'heat_wave/02_kdca_노인고혈압.md'
  quote: z.string().optional(),                 // 시드 원문 (환각 검증 통과)
});
export type SourceCitation = z.infer<typeof SourceCitationSchema>;

// ============================================================================
// Advisory (룰 1건 매칭 결과)
// ============================================================================

export const AdvisorySchema = z.object({
  rule_id: z.number().int(),                    // ★ v2.1: dedup용
  disease_code: z.string(),                     // KCD: 'I10'
  disease_name: z.string(),                     // '본태성 고혈압'
  weather_alert: z.string(),                    // '폭염경보' | '한파주의보' 등
  severity: SeverityKoEnum,
  title: z.string(),
  message: z.string(),                          // 호칭 치환 완료 ('어머님')
  patient_actions: z.array(z.string()),
  specific_drugs_to_note: z.array(z.string()),
  source_citations: z.array(SourceCitationSchema),
  already_pushed: z.boolean(),
  pushed_at: z.string().nullable(),
  requires_review: z.boolean().optional(),     // ★ v1.1 추가
});
export type Advisory = z.infer<typeof AdvisorySchema>;

// ============================================================================
// 조회 응답
// ============================================================================

export const WeatherObservedSchema = z.object({
  tmx: z.number().nullable(),                   // 오늘 최고 (폭염 판정)
  tmn: z.number().nullable(),                   // 내일 최저 (한파 판정)
});

export const WeatherGridSchema = z.object({
  nx: z.number().int(),
  ny: z.number().int(),
});

export const WeatherAdvisoryResSchema = z.object({
  parent_id: z.string().uuid(),
  date: z.string(),                             // 'YYYY-MM-DD'
  grid: WeatherGridSchema,
  observed: WeatherObservedSchema,
  active_alerts: z.array(z.string()),           // 자체 판정된 알림 (['폭염경보'])
  advisories: z.array(AdvisorySchema),
});
export type WeatherAdvisoryRes = z.infer<typeof WeatherAdvisoryResSchema>;

// ============================================================================
// 데모 시뮬 (F5 (debug)/trigger-weather)
// ============================================================================

export const SimulateAdvisoryReqSchema = z.object({
  parent_id: z.string().uuid(),
  alert_type: z.enum(['폭염주의보', '폭염경보', '한파주의보', '한파경보']),
});
export type SimulateAdvisoryReq = z.infer<typeof SimulateAdvisoryReqSchema>;
