/**
 * safety.ts — 약 안전판정 (DUR + NB + 환자분류)
 *
 * 백엔드 정합 (★ 가장 중요):
 *   - 백엔드 02 v2.1 (DUR 병용금기)
 *   - 백엔드 03 v2.1 (NB AI 추출)
 *   - 백엔드 04 v2.1 (POST /v1/parents/{id}/medications 의 응답)
 *
 * v1.1 검수 반영: 백엔드 04의 verdict wrapper 구조 정확 반영
 *   - BLOCK: HTTP 409 + { error: 'block', verdict: { decision, evidences } }
 *   - ALLOW/WARN: HTTP 201 + { medication_id, item_seq, safety_check: { decision, evidences } }
 *
 * Slice: F2 (medication-safety)
 */

import { z } from 'zod';
import { DecisionEnum, SeverityHmlEnum } from './common';
import { MedicationSchema } from './medication';

// ============================================================================
// Evidence (DUR/NB/환자분류 사유 1건)
// ============================================================================

export const EvidenceSourceEnum = z.enum(['DUR', 'NB', 'patient_class']);
export type EvidenceSource = z.infer<typeof EvidenceSourceEnum>;

export const EvidenceSchema = z.object({
  source: EvidenceSourceEnum,
  severity: SeverityHmlEnum,
  message: z.string(),
  conflicting_drug: MedicationSchema.optional(),    // 충돌 약 (DUR의 경우)
  citation: z.string().optional(),                  // 식약처 원문 (NB의 경우)
  citation_source: z.string().optional(),           // 예: '와파린정 사용상의주의사항'
});
export type Evidence = z.infer<typeof EvidenceSchema>;

// ============================================================================
// Verdict wrapper (★ 백엔드 04 v2.1)
// ============================================================================

/** BLOCK·WARN·ALLOW 결과 wrapper */
export const VerdictSchema = z.object({
  decision: DecisionEnum,
  evidences: z.array(EvidenceSchema),
});
export type Verdict = z.infer<typeof VerdictSchema>;

/** 약 추가 성공 시 (HTTP 201) — ALLOW 또는 WARN만 */
export const SafetyCheckSchema = z.object({
  decision: z.enum(['ALLOW', 'WARN']),
  evidences: z.array(EvidenceSchema),
});
export type SafetyCheck = z.infer<typeof SafetyCheckSchema>;

// ============================================================================
// 약 추가 응답 (★ 핵심)
// ============================================================================

/** HTTP 201 응답 — 약은 이미 추가됨 (ALLOW + WARN 둘 다) */
export const AddMedicationResSchema = z.object({
  medication_id: z.string(),
  item_seq: z.string(),
  safety_check: SafetyCheckSchema,
});
export type AddMedicationRes = z.infer<typeof AddMedicationResSchema>;

/** HTTP 409 응답 본문 — BLOCK만, 약 추가 안 됨 */
export const BlockErrorBodySchema = z.object({
  error: z.literal('block'),
  verdict: VerdictSchema,
});
export type BlockErrorBody = z.infer<typeof BlockErrorBodySchema>;

// ============================================================================
// 사전 약장 안전 점검 (GET /v1/parents/{id}/safety-check)
// ============================================================================

export const OverallSafetyCheckSchema = z.object({
  overall_decision: DecisionEnum,
  evidences: z.array(EvidenceSchema),
});
export type OverallSafetyCheck = z.infer<typeof OverallSafetyCheckSchema>;

// ============================================================================
// 헬퍼: 항응고제 필터 (ATC B01A*)
// ============================================================================

/**
 * 항응고제 여부 (백엔드 08과 동일 로직).
 * F2 약장 표시, F4 응급카드 critical_drugs 분리에 사용.
 */
export const isAnticoagulant = (m: { atc_code: string | null }): boolean =>
  m.atc_code?.startsWith('B01A') ?? false;
