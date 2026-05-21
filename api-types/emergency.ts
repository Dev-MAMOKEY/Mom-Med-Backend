/**
 * emergency.ts — 응급카드 + 단골 병원·약국
 *
 * 백엔드 정합:
 *   - 백엔드 08 v2.1 (emergency_cards, parent_hospitals, parent_pharmacies)
 *   - HIRA 11999 병원정보서비스
 *   - HIRA 12101 의료기관별 상세 (응급실)
 *   - HIRA 12100 약국정보서비스
 *
 * Slice: F4 (emergency-card)
 */

import { z } from 'zod';
import { AllergenTypeEnum, AllergySeverityEnum } from './common';
import { MedicationSchema } from './medication';

// ============================================================================
// 병원 / 약국
// ============================================================================

export const HospitalEmergencyInfoSchema = z.object({
  night_er_phone_1: z.string().nullable(),
  night_er_phone_2: z.string().nullable(),
  night_er_available: z.enum(['Y', 'N']).nullable(),
  day_er_phone_1: z.string().nullable(),
  day_er_available: z.enum(['Y', 'N']).nullable(),
  weekly_hours: z.record(z.string()).optional(),
});
export type HospitalEmergencyInfo = z.infer<typeof HospitalEmergencyInfoSchema>;

export const HospitalSchema = z.object({
  ykiho: z.string(),                            // HIRA 암호화 요양기호
  yadm_nm: z.string(),                          // 병원명
  cl_cd_nm: z.string().optional(),              // '상급종합', '종합' 등
  addr: z.string().optional(),
  telno: z.string().optional(),
  x_pos: z.number().optional(),
  y_pos: z.number().optional(),
  is_regular: z.boolean(),
  emergency: HospitalEmergencyInfoSchema.optional(),
});
export type Hospital = z.infer<typeof HospitalSchema>;

export const PharmacySchema = z.object({
  ykiho: z.string(),
  yadm_nm: z.string(),
  addr: z.string().optional(),
  telno: z.string().optional(),
  x_pos: z.number().optional(),
  y_pos: z.number().optional(),
  is_regular: z.boolean(),
  visit_count: z.number().int().nonnegative(),
});
export type Pharmacy = z.infer<typeof PharmacySchema>;

// ============================================================================
// 병원 검색 (HIRA 11999)
// ============================================================================

export const HospitalSearchResultSchema = z.object({
  ykiho: z.string(),
  yadm_nm: z.string(),
  cl_cd_nm: z.string().optional(),
  addr: z.string(),
  telno: z.string().optional(),
  x_pos: z.number().optional(),
  y_pos: z.number().optional(),
});
export type HospitalSearchResult = z.infer<typeof HospitalSearchResultSchema>;

// ============================================================================
// 응급카드 snapshot
// ============================================================================

export const PatientInfoSchema = z.object({
  name: z.string(),
  age: z.number().int(),
  sex: z.enum(['M', 'F']),
  address: z.string(),
});

export const AllergyInSnapshotSchema = z.object({
  type: AllergenTypeEnum,
  name: z.string(),
  severity: AllergySeverityEnum,
  notes: z.string().nullable(),
});

export const CriticalDrugSchema = z.object({
  item_seq: z.string(),
  item_name: z.string(),
  main_ingr_en: z.string(),
  atc_code: z.string(),
  warning: z.string(),                          // 예: 'ANTICOAGULANT - 수술·시술 시 출혈 위험'
});
export type CriticalDrug = z.infer<typeof CriticalDrugSchema>;

export const ConditionInSnapshotSchema = z.object({
  code: z.string(),
  name: z.string(),
});

export const EmergencyContactSchema = z.object({
  name: z.string(),
  phone: z.string(),
  relation: z.string(),
});
export type EmergencyContact = z.infer<typeof EmergencyContactSchema>;

export const EmergencySnapshotSchema = z.object({
  patient: PatientInfoSchema,
  allergies: z.array(AllergyInSnapshotSchema),           // ★ P1
  critical_drugs: z.array(CriticalDrugSchema),           // ★ P2 (항응고제 등 ATC B01A*)
  conditions: z.array(ConditionInSnapshotSchema),        // P3
  medications: z.array(MedicationSchema),                // 전체 (항응고제 최상단 정렬)
  hospitals: z.array(HospitalSchema),
  pharmacies: z.array(PharmacySchema),
  emergency_contacts: z.array(EmergencyContactSchema),
});
export type EmergencySnapshot = z.infer<typeof EmergencySnapshotSchema>;

// ============================================================================
// 응급카드 (자녀 시점)
// ============================================================================

export const EmergencyCardSchema = z.object({
  qr_token: z.string(),
  qr_url: z.string().url(),                     // ★ v1.1: /em/{token} 패턴
  valid_until: z.string(),                      // ISO datetime
  snapshot: EmergencySnapshotSchema,
  snapshot_at: z.string(),
  access_count: z.number().int().nonnegative(),
});
export type EmergencyCard = z.infer<typeof EmergencyCardSchema>;

// ============================================================================
// 외부 공개 페이지 응답 (GET /em/{token})
// ============================================================================

/**
 * 인증 없이 접근 가능. snapshot만 반환.
 * 만료/revoke 시 HTTP 410.
 * Rate limit 초과 시 HTTP 429.
 */
export const EmergencyPublicResSchema = EmergencySnapshotSchema;
export type EmergencyPublicRes = z.infer<typeof EmergencyPublicResSchema>;
