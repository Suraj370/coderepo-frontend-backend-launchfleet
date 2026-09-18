import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createSegment, retireSegment, updateSegment } from "../api";
import type { CreateSegmentRequest, UpdateSegmentRequest } from "../types";

function useInvalidateSegments(projectKey: string) {
  const queryClient = useQueryClient();

  return () => queryClient.invalidateQueries({ queryKey: ["segments", projectKey] });
}

export function useCreateSegment(projectKey: string) {
  const invalidate = useInvalidateSegments(projectKey);

  return useMutation({
    mutationFn: (request: CreateSegmentRequest) => createSegment(projectKey, request),
    onSuccess: invalidate,
  });
}

export function useUpdateSegment(projectKey: string) {
  const invalidate = useInvalidateSegments(projectKey);

  return useMutation({
    mutationFn: ({ segmentKey, request }: { segmentKey: string; request: UpdateSegmentRequest }) =>
      updateSegment(projectKey, segmentKey, request),
    onSuccess: invalidate,
  });
}

export function useRetireSegment(projectKey: string) {
  const invalidate = useInvalidateSegments(projectKey);

  return useMutation({
    mutationFn: (segmentKey: string) => retireSegment(projectKey, segmentKey),
    onSuccess: invalidate,
  });
}
