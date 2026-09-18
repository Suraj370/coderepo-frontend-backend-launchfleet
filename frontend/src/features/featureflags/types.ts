export type FeatureFlagType = "BOOLEAN" | "MULTIVARIANT";

export type FeatureFlagStatus = "ACTIVE" | "RETIRED";

export type ConditionType =
  | "ATTRIBUTE"
  | "USER_KEY"
  | "SEGMENT_MATCH";

export type ConditionOperator = "EQUALS" | "IN";

export interface FeatureFlagVariant {
  id: string;
  key: string;
  name: string;
  value: unknown;
  order: number;
}

export interface Condition {
  type: ConditionType;
  attribute: string | null;
  operator: ConditionOperator;
  values: string[];
}

export interface TargetingRule {
  id: string;
  priority: number;
  conditions: Condition[];
  variantId: string;
}

export interface Allocation {
  variantId: string;
  percentage: number;
}

export interface Rollout {
  allocations: Allocation[];
}

export interface FeatureFlagConfig {
  id: string;
  featureFlagId: string;
  environmentId: string;
  environmentKey: string;
  enabled: boolean;
  defaultVariantId: string;
  targetingRules: TargetingRule[];
  rollout: Rollout | null;
  version: number;
  updatedBy: string | null;
  updatedAt: string;
  createdAt: string;
}

export interface FeatureFlag {
  id: string;
  projectId: string;
  key: string;
  name: string;
  description: string | null;
  type: FeatureFlagType;
  status: FeatureFlagStatus;
  variants: FeatureFlagVariant[];
  environments: FeatureFlagConfig[];
  createdBy: string | null;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string;
}

export interface CreateFeatureFlagVariant {
  key: string;
  name: string;
  value: unknown;
}

export interface CreateFeatureFlagRequest {
  key: string;
  name: string;
  description?: string;
  type: FeatureFlagType;
  variants: CreateFeatureFlagVariant[] | null;
}

export interface UpdateFeatureFlagRequest {
  name: string;
  description?: string;
}

export interface UpdateFlagEnvironmentConfigRequest {
  enabled?: boolean;
  defaultVariantId?: string;
}

export interface ConditionRequest {
  type: ConditionType;
  attribute?: string;
  operator: ConditionOperator;
  values: string[];
}

export interface TargetingRuleRequest {
  priority: number;
  conditions: ConditionRequest[];
  variantId: string;
}

export interface AllocationRequest {
  variantId: string;
  percentage: number;
}

export interface RolloutRequest {
  allocations: AllocationRequest[];
}