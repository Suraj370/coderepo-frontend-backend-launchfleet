import { useQueries } from "@tanstack/react-query";

import { listEnvironments } from "@/features/environments/api";
import {
  listExperimentSummaries,
  listFlagSummaries,
  listSegmentSummaries,
} from "../api";

export function useDashboardSummary(projectKey: string | null) {
  const [flags, environments, segments, experiments] = useQueries({
    queries: [
      {
        queryKey: ["dashboard", "flags", projectKey],
        queryFn: () => listFlagSummaries(projectKey!),
        enabled: projectKey !== null,
      },
      // Shares the topbar's environment selector cache entry intentionally -
      // same key, same data, one fetch.
      {
        queryKey: ["environments", projectKey],
        queryFn: () => listEnvironments(projectKey!),
        enabled: projectKey !== null,
      },
      {
        queryKey: ["dashboard", "segments", projectKey],
        queryFn: () => listSegmentSummaries(projectKey!),
        enabled: projectKey !== null,
      },
      {
        queryKey: ["dashboard", "experiments", projectKey],
        queryFn: () => listExperimentSummaries(projectKey!),
        enabled: projectKey !== null,
      },
    ],
  });

  const isPending =
    flags.isPending || environments.isPending || segments.isPending || experiments.isPending;
  const isError = flags.isError || environments.isError || segments.isError || experiments.isError;

  const flagsActive = flags.data?.filter((flag) => flag.status === "ACTIVE").length ?? 0;
  const flagsRetired = flags.data?.filter((flag) => flag.status === "RETIRED").length ?? 0;
  const segmentsActive = segments.data?.filter((segment) => segment.status === "ACTIVE").length ?? 0;
  const segmentsRetired = segments.data?.filter((segment) => segment.status === "RETIRED").length ?? 0;
  const experimentsRunning =
    experiments.data?.filter((experiment) => experiment.status === "RUNNING").length ?? 0;
  const experimentsCompleted =
    experiments.data?.filter((experiment) => experiment.status === "COMPLETED").length ?? 0;

  return {
    isPending,
    isError,
    flagsTotal: flags.data?.length ?? 0,
    flagsActive,
    flagsRetired,
    environmentsTotal: environments.data?.length ?? 0,
    environmentNames: environments.data?.map((environment) => environment.name) ?? [],
    segmentsTotal: segments.data?.length ?? 0,
    segmentsActive,
    segmentsRetired,
    experimentsTotal: experiments.data?.length ?? 0,
    experimentsRunning,
    experimentsCompleted,
  };
}
