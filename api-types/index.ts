/**
 * api-types/index.ts — 모든 Zod schema 통합 export
 *
 * 사용:
 *   import { Medication, AddMedicationReqSchema, SafetyBlockError } from '@/api/types';
 *
 * 백엔드 v2.1과 정합. F0 코딩 시작 후 mom-med/api/types/ 로 복사.
 */

// 공통
export * from './common';

// F1
export * from './parent';

// F2 (★ 핵심)
export * from './medication';
export * from './safety';

// F3
export * from './condition';

// F4
export * from './emergency';

// F5
export * from './weather';

// F6
export * from './ocr';
