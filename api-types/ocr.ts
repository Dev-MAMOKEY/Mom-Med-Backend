/**
 * ocr.ts — 처방전 OCR + 약 알람
 *
 * 백엔드: 별도 PRD (Naver Clova OCR · Google Document AI 등).
 * MVP: mock만으로 동작.
 *
 * Slice: F6 (prescription-ocr)
 */

import { z } from 'zod';
import { DecisionEnum } from './common';
import { MedicationSchema } from './medication';

// ============================================================================
// 처방전 OCR 결과
// ============================================================================

export const PrescriptionDocumentInfoSchema = z.object({
  issued_by: z.string(),                        // 병원명
  issued_at: z.string(),                        // ISO date
  doctor_name: z.string().nullable(),
  confidence: z.number().min(0).max(1),         // 처방전 전체 인식 신뢰도
});
export type PrescriptionDocumentInfo = z.infer<typeof PrescriptionDocumentInfoSchema>;

/**
 * OCR로 인식된 약 1개.
 * safety_decision은 백엔드 04와 동일 enum + safety_reason은 사용자 표시용.
 */
export const OcrMedicationSchema = z.object({
  temp_id: z.string(),                          // FE 임시 ID (checkbox state)
  item_seq: z.string(),
  item_name: z.string(),
  main_ingr_en: z.string().nullable(),
  atc_code: z.string().nullable(),
  dosage: z.string(),                           // '1일 1회'
  days: z.number().int().positive(),            // 처방 일수
  match_confidence: z.number().min(0).max(1),   // 약 매칭 신뢰도
  safety_decision: DecisionEnum,
  safety_reason: z.string().optional(),         // BLOCK/WARN 시 인라인 표시
});
export type OcrMedication = z.infer<typeof OcrMedicationSchema>;

export const OcrResultSchema = z.object({
  document_info: PrescriptionDocumentInfoSchema,
  medications: z.array(OcrMedicationSchema),
});
export type OcrResult = z.infer<typeof OcrResultSchema>;

export const OcrReqSchema = z.object({
  image: z.string(),                            // base64
});
export type OcrReq = z.infer<typeof OcrReqSchema>;

// ============================================================================
// 약 알람 (부모 시점)
// ============================================================================

export const ReminderScheduleSchema = z.object({
  reminder_id: z.string().uuid(),
  time: z.string(),                             // 'HH:mm' (예: '12:00')
  weekdays: z.array(z.number().int().min(0).max(6)),    // 0=일 ~ 6=토
  medications: z.array(MedicationSchema),
  enabled: z.boolean(),
});
export type ReminderSchedule = z.infer<typeof ReminderScheduleSchema>;

export const ReminderListSchema = z.object({
  reminders: z.array(ReminderScheduleSchema),
});
export type ReminderList = z.infer<typeof ReminderListSchema>;

// ============================================================================
// 약 복용 기록 (부모 "먹었어요" 버튼)
// ============================================================================

export const TakeMedicationReqSchema = z.object({
  reminder_id: z.string().uuid(),
  taken_at: z.string(),                         // ISO datetime
});
export type TakeMedicationReq = z.infer<typeof TakeMedicationReqSchema>;

// ============================================================================
// 오늘의 스케줄 (부모 홈)
// ============================================================================

export const TodayScheduleItemSchema = z.object({
  reminder_id: z.string().uuid(),
  time: z.string(),
  medications: z.array(MedicationSchema),
  state: z.enum(['past', 'current', 'next', 'future']),
  taken_at: z.string().nullable(),
});
export type TodayScheduleItem = z.infer<typeof TodayScheduleItemSchema>;

export const TodayScheduleSchema = z.object({
  date: z.string(),                             // 'YYYY-MM-DD'
  schedules: z.array(TodayScheduleItemSchema),
});
export type TodaySchedule = z.infer<typeof TodayScheduleSchema>;
