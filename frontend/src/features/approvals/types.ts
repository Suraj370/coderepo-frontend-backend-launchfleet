import type { Condition, ConditionRequest } from "@/features/featureflags/types";

export type ApprovalStatus =
  | "PENDING"
  | "APPROVED"
  | "REJECTED"
  | "SCHEDULED"
  | "APPLIED"
  | "CANCELLED"
  | "FAILED";

export type CancellationReason = "USER_REQUESTED" | "STALE_CONFIGURATION" | "FLAG_OR_ENVIRONMENT_RETIRED";

export interface ApprovalAllocation {
  variantId: string;
  percentage: number;
}

export interface ApprovalTargetingRule {
  id: string;
  priority: number;
  conditions: Condition[];
  variantId: string;
}

export interface ApprovalRollout {
  allocations: ApprovalAllocation[];
}

export interface ProposedConfig {
  enabled: boolean;
  defaultVariantId: string;
  targetingRules: ApprovalTargetingRule[];
  rollout: ApprovalRollout | null;
}

export interface ApprovalRequest {
  id: string;
  projectId: string;
  featureFlagId: string;
  environmentId: string;
  status: ApprovalStatus;
  baseConfigVersion: number;
  proposedConfig: ProposedConfig;
  submittedBy: string;
  submittedAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  approvalComment: string | null;
  rejectionComment: string | null;
  scheduledAt: string | null;
  appliedVersion: number | null;
  cancellationReason: CancellationReason | null;
  cancelledBy: string | null;
  cancelledAt: string | null;
}

export interface SubmitApprovalRequestRequest {
  enabled: boolean;
  defaultVariantId: string;
  targetingRules: { priority: number; conditions: ConditionRequest[]; variantId: string }[];
  rollout: { allocations: { variantId: string; percentage: number }[] } | null;
}

export interface ApproveApprovalRequestRequest {
  approvalComment?: string;
}

export interface RejectApprovalRequestRequest {
  rejectionComment: string;
}

export interface ScheduleApprovalRequestRequest {
  approvalComment?: string;
  scheduledAt: string;
}

export const approvalStatusVariant: Record<
  ApprovalStatus,
  "default" | "secondary" | "destructive" | "outline"
> = {
  PENDING: "secondary",
  APPROVED: "outline",
  REJECTED: "destructive",
  SCHEDULED: "outline",
  APPLIED: "default",
  CANCELLED: "destructive",
  FAILED: "destructive",
};
