import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  addTargetingRule,
  removeRollout,
  removeTargetingRule,
  retireFeatureFlag,
  setRollout,
  updateFeatureFlag,
  updateFlagEnvironmentConfig,
  updateTargetingRule,
} from "../api";
import type {
  RolloutRequest,
  TargetingRuleRequest,
  UpdateFeatureFlagRequest,
  UpdateFlagEnvironmentConfigRequest,
} from "../types";

function useInvalidateFlag(projectKey: string, flagKey: string) {
  const queryClient = useQueryClient();

  return () => {
    queryClient.invalidateQueries({ queryKey: ["feature-flags", projectKey, flagKey] });
    queryClient.invalidateQueries({ queryKey: ["feature-flags", projectKey], exact: true });
  };
}

export function useUpdateFeatureFlag(projectKey: string, flagKey: string) {
  const invalidate = useInvalidateFlag(projectKey, flagKey);

  return useMutation({
    mutationFn: (request: UpdateFeatureFlagRequest) =>
      updateFeatureFlag(projectKey, flagKey, request),
    onSuccess: invalidate,
  });
}

export function useRetireFeatureFlag(projectKey: string, flagKey: string) {
  const invalidate = useInvalidateFlag(projectKey, flagKey);

  return useMutation({
    mutationFn: () => retireFeatureFlag(projectKey, flagKey),
    onSuccess: invalidate,
  });
}

export function useUpdateFlagEnvironmentConfig(
  projectKey: string,
  flagKey: string,
  environmentKey: string,
) {
  const invalidate = useInvalidateFlag(projectKey, flagKey);

  return useMutation({
    mutationFn: (request: UpdateFlagEnvironmentConfigRequest) =>
      updateFlagEnvironmentConfig(projectKey, flagKey, environmentKey, request),
    onSuccess: invalidate,
  });
}

export function useAddTargetingRule(projectKey: string, flagKey: string, environmentKey: string) {
  const invalidate = useInvalidateFlag(projectKey, flagKey);

  return useMutation({
    mutationFn: (request: TargetingRuleRequest) =>
      addTargetingRule(projectKey, flagKey, environmentKey, request),
    onSuccess: invalidate,
  });
}

export function useUpdateTargetingRule(
  projectKey: string,
  flagKey: string,
  environmentKey: string,
) {
  const invalidate = useInvalidateFlag(projectKey, flagKey);

  return useMutation({
    mutationFn: ({ ruleId, request }: { ruleId: string; request: TargetingRuleRequest }) =>
      updateTargetingRule(projectKey, flagKey, environmentKey, ruleId, request),
    onSuccess: invalidate,
  });
}

export function useRemoveTargetingRule(
  projectKey: string,
  flagKey: string,
  environmentKey: string,
) {
  const invalidate = useInvalidateFlag(projectKey, flagKey);

  return useMutation({
    mutationFn: (ruleId: string) =>
      removeTargetingRule(projectKey, flagKey, environmentKey, ruleId),
    onSuccess: invalidate,
  });
}

export function useSetRollout(projectKey: string, flagKey: string, environmentKey: string) {
  const invalidate = useInvalidateFlag(projectKey, flagKey);

  return useMutation({
    mutationFn: (request: RolloutRequest) => setRollout(projectKey, flagKey, environmentKey, request),
    onSuccess: invalidate,
  });
}

export function useRemoveRollout(projectKey: string, flagKey: string, environmentKey: string) {
  const invalidate = useInvalidateFlag(projectKey, flagKey);

  return useMutation({
    mutationFn: () => removeRollout(projectKey, flagKey, environmentKey),
    onSuccess: invalidate,
  });
}
