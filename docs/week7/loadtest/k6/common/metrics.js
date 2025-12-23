import { Counter, Rate, Trend } from 'k6/metrics';

// STEP13 - Ranking metrics
export const rankingQueryDuration = new Trend('ranking_query_duration');
export const rankingQuerySuccessRate = new Rate('ranking_query_success_rate');
export const rankingUpdateDuration = new Trend('ranking_update_duration');
export const rankingAccuracyRate = new Rate('ranking_accuracy_rate');
export const zincrbyOperationCount = new Counter('zincrby_operation_count');

// STEP14 - Reservation metrics
export const reservationSuccessCount = new Counter('reservation_success_count');
export const reservationSoldOutCount = new Counter('reservation_sold_out_count');
export const reservationDuplicateCount = new Counter('reservation_duplicate_count');
export const reservationErrorCount = new Counter('reservation_error_count');
export const sequenceAccuracyRate = new Rate('sequence_accuracy_rate');
export const duplicatePreventionRate = new Rate('duplicate_prevention_rate');
export const reservationDuration = new Trend('reservation_duration');
export const issuanceDuration = new Trend('issuance_duration');
