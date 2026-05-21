/**
 * medication.ts — 약·약장·식약처 약 마스터
 *
 * 백엔드 정합:
 *   - 백엔드 01 v2.1 (ref.drugs_master + ref.pill_visuals)
 *   - 백엔드 04 v2.1 (app.patient_medications CRUD)
 *
 * Slice: F2 (medication-safety), F4·F6에서 재사용
 */

import { z } from 'zod';
import { MedicationSourceEnum, SpecialtyTypeEnum } from './common';

// ============================================================================
// 약 (식약처 마스터 + 부모 약장)
// ============================================================================

export const MedicationSchema = z.object({
  // ref.drugs_master 필드
  item_seq: z.string(),
  item_name: z.string(),
  main_ingr_en: z.string().nullable(),
  atc_code: z.string().nullable(),
  specialty_type: SpecialtyTypeEnum.nullable(),

  // app.patient_medications 필드
  medication_id: z.string().optional(),         // 부모 약장에 추가된 경우
  source: MedicationSourceEnum.optional(),
  dosage_schedule: z.string().optional(),
  added_at: z.string(),
});
export type Medication = z.infer<typeof MedicationSchema>;

export const MedicationListSchema = z.object({
  medications: z.array(MedicationSchema),
});
export type MedicationList = z.infer<typeof MedicationListSchema>;

// ============================================================================
// 알약 외형 (식약처 낱알식별 15057639)
// ============================================================================

export const PillVisualSchema = z.object({
  item_image: z.string().url(),
  drug_shape: z.string(),                       // 예: '팔각형'
  color_class1: z.string(),                     // 예: '하양'
  color_class2: z.string().optional(),
  print_front: z.string().optional(),           // 예: 'VLE'
  print_back: z.string().optional(),
  line_front: z.string().optional(),
  line_back: z.string().optional(),
  leng_long: z.number(),                        // 길이 mm
  leng_short: z.number(),                       // 짧은 변 mm
  thick: z.number(),                            // 두께 mm
  form_code_name: z.string().optional(),        // 제형
  chart: z.string().optional(),                 // 성상 텍스트
});
export type PillVisual = z.infer<typeof PillVisualSchema>;

// ============================================================================
// 약 상세 (drugs_master + pill_visuals + 주의사항)
// ============================================================================

export const DrugCautionSchema = z.object({
  type: z.enum(['warning', 'info']),
  title: z.string(),
  body: z.string(),
  source: z.string(),                           // 예: '식약처 NB_DOC_DATA'
});
export type DrugCaution = z.infer<typeof DrugCautionSchema>;

export const DrugDetailSchema = z.object({
  item_seq: z.string(),
  item_name: z.string(),
  main_ingr_en: z.string().nullable(),
  atc_code: z.string().nullable(),
  specialty_type: SpecialtyTypeEnum.nullable(),
  manufacturer: z.string().optional(),
  pill_visual: PillVisualSchema.optional(),
  cautions: z.array(DrugCautionSchema),
});
export type DrugDetail = z.infer<typeof DrugDetailSchema>;

// ============================================================================
// 약 추가 / 삭제
// ============================================================================

export const AddMedicationReqSchema = z.object({
  item_seq: z.string(),
  source: MedicationSourceEnum,
  dosage: z.string().optional(),
});
export type AddMedicationReq = z.infer<typeof AddMedicationReqSchema>;

/**
 * 백엔드 04 v2.1 응답:
 *   HTTP 201 (ALLOW 또는 WARN — 약 추가됨)
 *   HTTP 409 (BLOCK — safety.ts BlockErrorBodySchema 참조)
 *
 * safety_check 필드는 safety.ts에서 정의 (SafetyCheckSchema)
 */
// AddMedicationResSchema는 safety.ts에서 정의 (SafetyCheckSchema 의존)
