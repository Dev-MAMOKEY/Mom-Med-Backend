/**
 * common.ts — 공통 에러·enum·기본 타입
 *
 * 백엔드 v2.1과 정합:
 *   - 모든 슬라이스에서 공유
 *   - ApiError·SafetyBlockError는 F0의 apiCall에서 throw
 *   - decision·severity는 백엔드 04 (medication safety) + 06 (weather rules) 정의 따름
 */

import { z } from 'zod';

// ============================================================================
// 에러 클래스
// ============================================================================

export class ApiError extends Error {
  constructor(public status: number, public body: unknown) {
    super(`ApiError ${status}`);
    this.name = 'ApiError';
  }
}

/**
 * 백엔드 04 v2.1: BLOCK 결정 시 HTTP 409 + body = { error: 'block', verdict: {...} }
 * WARN/ALLOW는 HTTP 201 (약은 추가됨) — SafetyBlockError 던지지 않음.
 */
export class SafetyBlockError extends ApiError {
  constructor(public payload: BlockErrorBody) {
    super(409, payload);
    this.name = 'SafetyBlockError';
  }
}

// ============================================================================
// 공통 enum
// ============================================================================

export const DecisionEnum = z.enum(['ALLOW', 'WARN', 'BLOCK']);
export type Decision = z.infer<typeof DecisionEnum>;

export const SeverityHmlEnum = z.enum(['high', 'medium', 'low']);
export type SeverityHml = z.infer<typeof SeverityHmlEnum>;

/** 백엔드 06 v2.1 weather_rules.severity CHECK 제약 */
export const SeverityKoEnum = z.enum(['관심', '주의', '경고', '위험']);
export type SeverityKo = z.infer<typeof SeverityKoEnum>;

/** 백엔드 04 v2.1 patient_allergies.severity CHECK */
export const AllergySeverityEnum = z.enum(['mild', 'moderate', 'severe']);
export type AllergySeverity = z.infer<typeof AllergySeverityEnum>;

/** 백엔드 04 v2.1 patient_medications.source CHECK */
export const MedicationSourceEnum = z.enum(['scan', 'manual', 'photo', 'ocr', 'prescription']);
export type MedicationSource = z.infer<typeof MedicationSourceEnum>;

/** 백엔드 04 v2.1 patient_allergies.allergen_type */
export const AllergenTypeEnum = z.enum(['drug', 'food', 'environment']);
export type AllergenType = z.infer<typeof AllergenTypeEnum>;

/** 식약처 의약품 분류 */
export const SpecialtyTypeEnum = z.enum(['ETC', 'OTC']);
export type SpecialtyType = z.infer<typeof SpecialtyTypeEnum>;

// 임시 forward declaration (medication.ts에서 정확히 정의)
const BlockErrorBodyPlaceholder = z.object({
  error: z.literal('block'),
  verdict: z.unknown(),
});
type BlockErrorBody = z.infer<typeof BlockErrorBodyPlaceholder>;
