/**
 * condition.ts — 부모 기저질환 + 알레르기 + KCD 검색
 *
 * 백엔드 정합:
 *   - 백엔드 05 v2.1 (app.patient_conditions 정규화 테이블 — JSONB 아님)
 *   - 백엔드 04 v2.1 (app.patient_allergies 신규)
 *   - HIRA 12904 (질병정보서비스)
 *
 * Slice: F3 (conditions-allergies)
 */

import { z } from 'zod';
import { AllergenTypeEnum, AllergySeverityEnum } from './common';

// ============================================================================
// 기저질환 (KCD 코드 기반)
// ============================================================================

export const ConditionSchema = z.object({
  condition_id: z.string().uuid(),
  disease_code: z.string(),                    // 예: 'I10'
  disease_name: z.string(),                    // 예: '본태성 고혈압'
  diagnosed_at: z.string().nullable(),         // ISO date
  notes: z.string().nullable(),
});
export type Condition = z.infer<typeof ConditionSchema>;

export const ConditionListSchema = z.object({
  conditions: z.array(ConditionSchema),
});
export type ConditionList = z.infer<typeof ConditionListSchema>;

export const AddConditionReqSchema = z.object({
  disease_code: z.string().min(2),
  diagnosed_at: z.string().optional(),
  notes: z.string().optional(),
});
export type AddConditionReq = z.infer<typeof AddConditionReqSchema>;

// ============================================================================
// 알레르기
// ============================================================================

export const AllergySchema = z.object({
  allergy_id: z.string().uuid(),
  allergen_type: AllergenTypeEnum,
  allergen_name: z.string(),
  severity: AllergySeverityEnum,
  notes: z.string().nullable(),
});
export type Allergy = z.infer<typeof AllergySchema>;

export const AllergyListSchema = z.object({
  allergies: z.array(AllergySchema),
});
export type AllergyList = z.infer<typeof AllergyListSchema>;

export const AddAllergyReqSchema = z.object({
  allergen_type: AllergenTypeEnum,
  allergen_name: z.string().min(1),
  severity: AllergySeverityEnum,
  notes: z.string().optional(),
});
export type AddAllergyReq = z.infer<typeof AddAllergyReqSchema>;

// ============================================================================
// KCD 검색 (HIRA 12904)
// ============================================================================

export const DiseaseSearchResultSchema = z.object({
  sickCd: z.string(),                          // 'I10'
  sickNm: z.string(),                          // '본태성 (원발성) 고혈압'
  sickEngNm: z.string(),                       // 'Essential (primary) hypertension'
});
export type DiseaseSearchResult = z.infer<typeof DiseaseSearchResultSchema>;

export const DiseaseSearchResSchema = z.object({
  results: z.array(DiseaseSearchResultSchema),
});
export type DiseaseSearchRes = z.infer<typeof DiseaseSearchResSchema>;

// ============================================================================
// 질병 → 위험 약 매핑 (백엔드 03 NB 환자분류)
// ============================================================================

export const ConditionDrugWarningSchema = z.object({
  item_seq: z.string(),
  condition_code: z.string(),
  warning_message: z.string(),
  citation: z.string().optional(),
});
export type ConditionDrugWarning = z.infer<typeof ConditionDrugWarningSchema>;

export const ConditionDrugWarningListSchema = z.array(ConditionDrugWarningSchema);
export type ConditionDrugWarningList = z.infer<typeof ConditionDrugWarningListSchema>;
