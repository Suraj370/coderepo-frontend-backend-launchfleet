export type ExperimentStatus = "DRAFT" | "RUNNING" | "COMPLETED" | "CANCELLED";

export interface Allocation {
  variantId: string;
  percentage: number;
}

export interface Rollout {
  allocations: Allocation[];
}

export interface Experiment {
  id: string;
  projectId: string;
  environmentId: string;
  featureFlagId: string;
  key: string;
  name: string;
  description: string | null;
  allocation: Rollout | null;
  conversionEventName: string | null;
  status: ExperimentStatus;
  version: number;
  createdBy: string | null;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string;
}

export interface ExperimentVariantMetrics {
  variantId: string;
  assignedCount: number;
  conversionCount: number;
  conversionRate: number;
}

export interface CreateExperimentRequest {
  environmentKey: string;
  flagKey: string;
  key: string;
  name: string;
  description?: string;
}

export interface AllocationRequest {
  variantId: string;
  percentage: number;
}

export interface UpdateExperimentRequest {
  name: string;
  description?: string;
  allocation?: AllocationRequest[] | null;
  conversionEventName?: string | null;
}
