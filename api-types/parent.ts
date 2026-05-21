/**
 * parent.ts — 부모 프로필 + 자녀-부모 관계 인증
 *
 * 백엔드 04 v2.1 (app.patient_profiles) 정합.
 * 자녀-부모 관계 인증 API는 MVP에서 mock — 출시 시 별도 인증 PRD.
 *
 * Slice: F1 (auth-role-parents)
 */

import { z } from 'zod';

// ============================================================================
// Parent (요약 + 상세)
// ============================================================================

export const ParentSchema = z.object({
  parent_id: z.string().uuid(),
  display_name: z.string(),
  birthdate: z.string(),        // ISO date: "1954-03-15"
  age: z.number().int().nonnegative(),
  sex: z.enum(['M', 'F']).optional(),
  address_sido: z.string(),
  address_sigungu: z.string().optional(),
  address_dong: z.string().optional(),
  medication_count: z.number().int().nonnegative(),
  alert_count: z.number().int().nonnegative(),
  conditions: z.array(z.string()),    // KCD 코드 배열 (예: ['I10', 'E11'])
});
export type Parent = z.infer<typeof ParentSchema>;

export const ParentListSchema = z.object({
  parents: z.array(ParentSchema),
});
export type ParentList = z.infer<typeof ParentListSchema>;

// ============================================================================
// 부모-자녀 관계 인증 (MVP: "0000" 통과)
// ============================================================================

/**
 * POST /v1/me/parents/request-verify
 * Request: { phone }  Response: { request_id, expires_in }
 */
export const RequestVerifyReqSchema = z.object({
  phone: z.string().regex(/^010-?\d{4}-?\d{4}$/, '올바른 휴대폰 번호 형식'),
});
export type RequestVerifyReq = z.infer<typeof RequestVerifyReqSchema>;

export const RequestVerifyResSchema = z.object({
  request_id: z.string(),
  expires_in: z.number().int().positive(),    // 초 단위 (3분 = 180)
});
export type RequestVerifyRes = z.infer<typeof RequestVerifyResSchema>;

/**
 * POST /v1/me/parents/verify
 * Request: { phone, code, request_id }
 * Response 200: { success: true, parent_id, display_name }
 * Response 400: { error: 'invalid_code' | 'expired' }
 *
 * MVP: code === '0000' 무조건 통과
 */
export const VerifyReqSchema = z.object({
  phone: z.string(),
  code: z.string().length(4),
  request_id: z.string(),
});
export type VerifyReq = z.infer<typeof VerifyReqSchema>;

export const VerifyResSchema = z.object({
  success: z.literal(true),
  parent_id: z.string().uuid(),
  display_name: z.string(),
});
export type VerifyRes = z.infer<typeof VerifyResSchema>;
